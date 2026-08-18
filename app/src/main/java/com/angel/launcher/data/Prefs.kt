package com.angel.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "angel")

/** The only persistence the launcher has. No Room until something needs one. */
object Prefs {
    private val PINNED = stringPreferencesKey("pinned")
    private val SKY = stringPreferencesKey("sky")
    private val TEMP = stringPreferencesKey("temp")
    private val LAT = stringPreferencesKey("lat")
    private val LON = stringPreferencesKey("lon")
    private val MESSAGES = stringPreferencesKey("messages")
    private val HOLDINGS = stringPreferencesKey("holdings")
    private val HOME_LAYOUT = stringPreferencesKey("home_layout")

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

    fun messages(c: Context): Flow<List<String>> =
        c.store.data.map { p -> decode(p[MESSAGES]) }

    suspend fun addMessages(c: Context, incoming: List<String>) {
        c.store.edit { p ->
            val existing = decode(p[MESSAGES])
            val merged = (existing + incoming.filter { it.isNotBlank() })
                .distinct()
                .takeLast(400)
            p[MESSAGES] = encode(merged)
        }
    }

    suspend fun clearMessages(c: Context) {
        c.store.edit { it.remove(MESSAGES) }
    }

    /**
     * The Home pane's arrangement, one encoded widget per line.
     *
     * `null` and empty mean different things: null is "never arranged", which
     * earns the derived default layout, while an empty list is a pane the owner
     * deliberately emptied and we must leave empty.
     */
    fun homeLayout(c: Context): Flow<List<String>?> =
        c.store.data.map { p ->
            p[HOME_LAYOUT]?.split('\n')?.filter { it.isNotBlank() }
        }

    suspend fun setHomeLayout(c: Context, lines: List<String>) {
        c.store.edit { it[HOME_LAYOUT] = lines.joinToString("\n") }
    }

    suspend fun clearHomeLayout(c: Context) {
        c.store.edit { it.remove(HOME_LAYOUT) }
    }

    fun holdings(c: Context): Flow<String?> = c.store.data.map { it[HOLDINGS] }

    suspend fun setHoldings(c: Context, json: String) {
        c.store.edit { it[HOLDINGS] = json }
    }

    /** Messages contain newlines, so records are separated by NUL, not a line break. */
    private const val SEP = "\u0000"

    private fun encode(list: List<String>) = list.joinToString(SEP)

    private fun decode(raw: String?): List<String> =
        raw?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
}
