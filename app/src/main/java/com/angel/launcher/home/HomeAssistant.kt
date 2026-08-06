package com.angel.launcher.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Long-lived access token, never in source and never in plain preferences. */
class HaStore(context: Context) {
    private val prefs = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "home-assistant",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    var baseUrl: String
        get() = prefs?.getString(BASE, "").orEmpty()
        set(value) { prefs?.edit()?.putString(BASE, value.trimEnd('/'))?.apply() }

    var token: String
        get() = prefs?.getString(TOKEN, "").orEmpty()
        set(value) { prefs?.edit()?.putString(TOKEN, value.trim())?.apply() }

    val configured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()

    private companion object {
        const val BASE = "base_url"
        const val TOKEN = "token"
    }
}

data class HaEntity(
    val id: String,
    val name: String,
    val state: String,
) {
    val domain: String get() = id.substringBefore('.')

    val on: Boolean
        get() = when (domain) {
            "lock" -> state == "locked"
            "media_player" -> state == "playing"
            "cover" -> state == "open"
            else -> state == "on"
        }

    val label: String
        get() = when (domain) {
            "lock" -> if (on) "Locked" else "Unlocked"
            "media_player" -> if (on) "Playing" else "Idle"
            "cover" -> if (on) "Open" else "Closed"
            "fan" -> if (on) "Running" else "Off"
            else -> if (on) "On" else "Off"
        }
}

data class House(
    val indoor: Double? = null,
    val humidity: Int? = null,
    val kilowatts: Double? = null,
    val target: Double? = null,
    val climateId: String? = null,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HaStore(app)
    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _configured = MutableStateFlow(store.configured)
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    private val _entities = MutableStateFlow<List<HaEntity>>(emptyList())
    val entities: StateFlow<List<HaEntity>> = _entities.asStateFlow()

    private val _house = MutableStateFlow(House())
    val house: StateFlow<House> = _house.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var socket: WebSocket? = null

    val baseUrl: String get() = store.baseUrl
    val token: String get() = store.token

    fun configure(url: String, token: String) {
        store.baseUrl = url
        store.token = token
        _configured.value = store.configured
        if (store.configured) refresh()
    }

    /** Pane opened: pull state, then keep it live over the websocket. */
    fun connect() {
        if (!store.configured) return
        refresh()
        openSocket()
    }

    fun disconnect() {
        socket?.close(1000, null)
        socket = null
    }

    override fun onCleared() {
        disconnect()
    }

    fun refresh() {
        viewModelScope.launch {
            val states = withContext(Dispatchers.IO) { get("/api/states") } ?: return@launch
            val all = runCatching { JSONArray(states) }.getOrNull() ?: return@launch
            val tiles = mutableListOf<HaEntity>()
            var house = House()

            for (i in 0 until all.length()) {
                val item = all.optJSONObject(i) ?: continue
                val id = item.optString("entity_id")
                val state = item.optString("state")
                val attributes = item.optJSONObject("attributes") ?: JSONObject()
                val name = attributes.optString("friendly_name").ifBlank { id.substringAfter('.') }
                val deviceClass = attributes.optString("device_class")

                when (id.substringBefore('.')) {
                    "climate" -> if (house.climateId == null) {
                        house = house.copy(
                            climateId = id,
                            target = attributes.optDouble("temperature").takeIf { !it.isNaN() },
                            indoor = attributes.optDouble("current_temperature").takeIf { !it.isNaN() },
                        )
                    }
                    "sensor" -> when (deviceClass) {
                        "humidity" -> if (house.humidity == null) {
                            house = house.copy(humidity = state.toDoubleOrNull()?.toInt())
                        }
                        "power" -> if (house.kilowatts == null) {
                            house = house.copy(kilowatts = state.toDoubleOrNull()?.let { it / 1000.0 })
                        }
                        "temperature" -> if (house.indoor == null) {
                            house = house.copy(indoor = state.toDoubleOrNull())
                        }
                    }
                    "light", "switch", "lock", "fan", "media_player", "humidifier", "cover" ->
                        tiles += HaEntity(id, name, state)
                }
            }

            _entities.value = tiles.take(8)
            _house.value = house
            _error.value = null
        }
    }

    fun toggle(entity: HaEntity) {
        viewModelScope.launch {
            val service = when {
                entity.domain == "lock" -> if (entity.on) "unlock" else "lock"
                entity.domain == "cover" -> if (entity.on) "close_cover" else "open_cover"
                entity.domain == "media_player" -> "media_play_pause"
                else -> "toggle"
            }
            // Optimistic: the websocket corrects us within a beat.
            _entities.value = _entities.value.map {
                if (it.id == entity.id) it.copy(state = flip(it)) else it
            }
            withContext(Dispatchers.IO) {
                post(
                    "/api/services/${entity.domain}/$service",
                    JSONObject().put("entity_id", entity.id).toString(),
                )
            }
        }
    }

    fun nudgeTarget(delta: Double) {
        val house = _house.value
        val id = house.climateId ?: return
        val next = ((house.target ?: 21.0) + delta).coerceIn(5.0, 30.0)
        _house.value = house.copy(target = next)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                post(
                    "/api/services/climate/set_temperature",
                    JSONObject().put("entity_id", id).put("temperature", next).toString(),
                )
            }
        }
    }

    private fun flip(entity: HaEntity): String = when (entity.domain) {
        "lock" -> if (entity.on) "unlocked" else "locked"
        "media_player" -> if (entity.on) "paused" else "playing"
        "cover" -> if (entity.on) "closed" else "open"
        else -> if (entity.on) "off" else "on"
    }

    private fun get(path: String): String? = runCatching {
        http.newCall(
            Request.Builder()
                .url(store.baseUrl + path)
                .header("Authorization", "Bearer " + store.token)
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) {
                _error.value = "HTTP " + response.code
                null
            } else {
                response.body?.string()
            }
        }
    }.onFailure { _error.value = it.message }.getOrNull()

    private fun post(path: String, body: String): String? = runCatching {
        http.newCall(
            Request.Builder()
                .url(store.baseUrl + path)
                .header("Authorization", "Bearer " + store.token)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build(),
        ).execute().use { it.body?.string() }
    }.onFailure { _error.value = it.message }.getOrNull()

    /** state_changed events keep the tiles honest without polling. */
    private fun openSocket() {
        if (socket != null) return
        val ws = store.baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/api/websocket"
        val request = Request.Builder().url(ws).build()
        socket = runCatching {
            http.newWebSocket(request, object : WebSocketListener() {
                private var id = 1

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                    when (message.optString("type")) {
                        "auth_required" -> webSocket.send(
                            JSONObject()
                                .put("type", "auth")
                                .put("access_token", store.token)
                                .toString(),
                        )
                        "auth_ok" -> webSocket.send(
                            JSONObject()
                                .put("id", id++)
                                .put("type", "subscribe_events")
                                .put("event_type", "state_changed")
                                .toString(),
                        )
                        "event" -> {
                            val data = message.optJSONObject("event")?.optJSONObject("data") ?: return
                            val newState = data.optJSONObject("new_state") ?: return
                            val entityId = newState.optString("entity_id")
                            val state = newState.optString("state")
                            _entities.value = _entities.value.map {
                                if (it.id == entityId) it.copy(state = state) else it
                            }
                            val house = _house.value
                            if (entityId == house.climateId) {
                                val attributes = newState.optJSONObject("attributes") ?: JSONObject()
                                _house.value = house.copy(
                                    target = attributes.optDouble("temperature").takeIf { !it.isNaN() }
                                        ?: house.target,
                                    indoor = attributes.optDouble("current_temperature").takeIf { !it.isNaN() }
                                        ?: house.indoor,
                                )
                            }
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socket = null
                }
            })
        }.getOrNull()
    }
}
