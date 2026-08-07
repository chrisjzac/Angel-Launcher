package com.angel.launcher.weather

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angel.launcher.data.Prefs
import com.angel.launcher.ui.Sky
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** LIVE is a fresh fix; CACHED is a real forecast for where we last were. */
enum class SkyMode { LOCATING, LIVE, CACHED, MANUAL, OFF }

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val http = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private val _sky = MutableStateFlow(Sky.Rain)
    val sky: StateFlow<Sky> = _sky.asStateFlow()

    private val _temp = MutableStateFlow<Int?>(null)
    val temp: StateFlow<Int?> = _temp.asStateFlow()

    private val _mode = MutableStateFlow(SkyMode.LOCATING)
    val mode: StateFlow<SkyMode> = _mode.asStateFlow()

    private var lastFetch = 0L

    /** Location toggled in quick settings: take the fix we could not get before. */
    private val providerWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (locationEnabled()) refresh(force = true)
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            providerWatcher,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Last good reading first — first paint never waits on the network.
        viewModelScope.launch {
            val (key, cached) = Prefs.sky(getApplication()).first()
            Sky.byKey(key)?.let { _sky.value = it }
            if (cached != null) _temp.value = cached
        }
        refresh(force = true)
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(providerWatcher) }
    }

    /**
     * A fresh fix when location allows it, otherwise the current forecast for
     * the last place we knew. Only a hand-picked sky is left alone.
     */
    fun refresh(force: Boolean = false) {
        if (_mode.value == SkyMode.MANUAL && !force) return
        val age = System.currentTimeMillis() - lastFetch
        if (!force && age < MIN_INTERVAL_MS) return

        viewModelScope.launch {
            val context = getApplication<Application>()
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

            val fix = if (granted && locationEnabled()) {
                _mode.value = SkyMode.LOCATING
                runCatching { currentLocation() }.getOrNull()
            } else {
                null
            }

            if (fix != null) {
                Prefs.setLastFix(context, fix.latitude, fix.longitude)
                apply(fix.latitude, fix.longitude, SkyMode.LIVE)
                return@launch
            }

            // No fix: the last place still has weather worth showing.
            val remembered = Prefs.lastFix(context).first()
            if (remembered == null) {
                _mode.value = SkyMode.OFF
                return@launch
            }
            apply(remembered.first, remembered.second, SkyMode.CACHED)
        }
    }

    private suspend fun apply(lat: Double, lon: Double, success: SkyMode) {
        val reading = withContext(Dispatchers.IO) { fetch(lat, lon) }
        if (reading == null) {
            _mode.value = SkyMode.OFF
            return
        }
        lastFetch = System.currentTimeMillis()
        _sky.value = Sky.fromWmoCode(reading.first)
        _temp.value = reading.second
        _mode.value = success
        Prefs.setSky(getApplication(), _sky.value.key, reading.second)
    }

    /** Tap the pill to walk the six palettes by hand. */
    fun cycle() {
        val next = Sky.all[(Sky.all.indexOf(_sky.value) + 1) % Sky.all.size]
        _sky.value = next
        _mode.value = SkyMode.MANUAL
    }

    private fun locationEnabled(): Boolean {
        val manager = getApplication<Application>()
            .getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(manager) }.getOrDefault(false)
    }

    private suspend fun currentLocation(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(10 * 60 * 1000)
            .setDurationMillis(9000)
            .build()
        return suspendCancellableCoroutine { cont ->
            @Suppress("MissingPermission")
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        if (cont.isActive) cont.resume(location)
                    } else {
                        @Suppress("MissingPermission")
                        client.lastLocation
                            .addOnSuccessListener { last -> if (cont.isActive) cont.resume(last) }
                            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    }
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }

    /** weather_code to temperature, straight from Open-Meteo. */
    private fun fetch(lat: Double, lon: Double): Pair<Int, Int>? = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=" + String.format("%.3f", lat) +
            "&longitude=" + String.format("%.3f", lon) +
            "&current=weather_code,temperature_2m"
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val current = JSONObject(response.body?.string().orEmpty()).getJSONObject("current")
            current.getInt("weather_code") to Math.round(current.getDouble("temperature_2m")).toInt()
        }
    }.getOrNull()

    private companion object {
        const val MIN_INTERVAL_MS = 10 * 60 * 1000L
    }
}
