package com.angel.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "angel")

/**
 * Launcher-wide preferences. The Wealth pane's ledger lives in Room instead
 * (see money/db/WealthDatabase.kt) — WEALTH_SMS_BACKFILLED is the one flag it
 * still keeps here, since it is a launcher-level "have we ever done this"
 * bit rather than ledger data.
 */
object Prefs {
    private val PINNED = stringPreferencesKey("pinned")
    private val SKY = stringPreferencesKey("sky")
    private val TEMP = stringPreferencesKey("temp")
    private val LAT = stringPreferencesKey("lat")
    private val LON = stringPreferencesKey("lon")
    private val HOLDINGS = stringPreferencesKey("holdings")
    private val WEALTH_SMS_BACKFILLED = booleanPreferencesKey("wealth_sms_backfilled")

    fun pinned(c: Context): Flow<List<String>> =
        c.store.data.map { p -> p[PINNED]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList() }

    suspend fun setPinned(c: Context, keys: List<String>) {
        c.store.edit { it[PINNED] = keys.joinToString("\n") }
    }

    /** Last good reading, so first paint never waits on the network. */
    fun sky(c: Context): Flow<Pair<String?, Int?>> =
        c.store.data.map { p -> p[SKY] to p[TEMP]?.toIntOrNull() }

    suspend fun setSky(c: Context, key: String, temp: Int?) {
        c.store.edit {
            it[SKY] = key
            if (temp != null) it[TEMP] = temp.toString() else it.remove(TEMP)
        }
    }

    /**
     * Last place we got a fix. With location off there is still a real forecast
     * to show for where the phone last was, which beats a stale sky.
     */
    fun lastFix(c: Context): Flow<Pair<Double, Double>?> =
        c.store.data.map { p ->
            val lat = p[LAT]?.toDoubleOrNull()
            val lon = p[LON]?.toDoubleOrNull()
            if (lat != null && lon != null) lat to lon else null
        }

    suspend fun setLastFix(c: Context, lat: Double, lon: Double) {
        c.store.edit {
            it[LAT] = lat.toString()
            it[LON] = lon.toString()
        }
    }

    fun holdings(c: Context): Flow<String?> = c.store.data.map { it[HOLDINGS] }

    suspend fun setHoldings(c: Context, json: String) {
        c.store.edit { it[HOLDINGS] = json }
    }

    /** Whether the one-time READ_SMS inbox scan (SmsIngestor.backfill) has run. */
    fun wealthSmsBackfilled(c: Context): Flow<Boolean> =
        c.store.data.map { it[WEALTH_SMS_BACKFILLED] ?: false }

    suspend fun setWealthSmsBackfilled(c: Context, done: Boolean) {
        c.store.edit { it[WEALTH_SMS_BACKFILLED] = done }
    }
}
