package com.angel.launcher.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.angel.launcher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor

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

/**
 * How an entity draws. Derived from its domain rather than stored, so a saved
 * arrangement can never name a shape the entity cannot take.
 */
enum class WidgetKind { THERMOSTAT, SENSOR, TILE }

data class HaEntity(
    val id: String,
    val name: String,
    val state: String,
    val unit: String = "",
    val deviceClass: String = "",
    val target: Double? = null,
    val current: Double? = null,
) {
    val domain: String get() = id.substringBefore('.')

    val kind: WidgetKind
        get() = when (domain) {
            "climate" -> WidgetKind.THERMOSTAT
            "sensor", "binary_sensor" -> WidgetKind.SENSOR
            else -> WidgetKind.TILE
        }

    val on: Boolean
        get() = when (domain) {
            "lock" -> state == "locked"
            "media_player" -> state == "playing"
            "cover" -> state == "open"
            "scene" -> false
            else -> state == "on"
        }

    val label: String
        get() = when (domain) {
            "lock" -> if (on) "Locked" else "Unlocked"
            "media_player" -> if (on) "Playing" else "Idle"
            "cover" -> if (on) "Open" else "Closed"
            "fan" -> if (on) "Running" else "Off"
            "scene" -> "Scene"
            "script" -> if (on) "Running" else "Script"
            else -> if (on) "On" else "Off"
        }

    /** What a sensor widget prints: the state, tidied, with its unit. */
    val reading: String
        get() {
            val number = state.toDoubleOrNull()
            val value = when {
                number == null -> state.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                number == floor(number) && abs(number) < 1e9 -> number.toLong().toString()
                else -> String.format(Locale.US, "%.1f", number)
            }
            if (unit.isBlank()) return value
            val gap = if (unit.startsWith("°") || unit == "%") "" else " "
            return value + gap + unit
        }

    /** The service that flips this entity, given where it currently sits. */
    val service: String
        get() = when (domain) {
            "lock" -> if (on) "unlock" else "lock"
            "cover" -> if (on) "close_cover" else "open_cover"
            "media_player" -> "media_play_pause"
            "scene", "script" -> "turn_on"
            else -> "toggle"
        }

    /** Optimistic state, applied while the websocket catches up. */
    fun flipped(): HaEntity = copy(
        state = when (domain) {
            "lock" -> if (on) "unlocked" else "locked"
            "media_player" -> if (on) "paused" else "playing"
            "cover" -> if (on) "closed" else "open"
            "scene" -> state
            else -> if (on) "off" else "on"
        },
    )
}

/**
 * One placed widget. The arrangement is a flat ordered list of these; `wide`
 * is the only geometry there is, because the pane packs two narrow widgets to
 * a row and gives a wide one the row to itself.
 */
data class HomeWidget(val entityId: String, val wide: Boolean = false) {

    fun encode(): String = entityId + SEP + if (wide) WIDE else NARROW

    companion object {
        private const val SEP = "|"
        private const val WIDE = "w"
        private const val NARROW = "n"

        fun decode(line: String): HomeWidget? {
            val parts = line.split(SEP)
            val id = parts.firstOrNull()?.trim().orEmpty()
            if (!id.contains('.')) return null
            return HomeWidget(id, parts.getOrNull(1) == WIDE)
        }
    }
}

/** A widget that resolved against live state and so has something to draw. */
data class HomeCard(val widget: HomeWidget, val entity: HaEntity) {
    val id: String get() = widget.entityId
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HaStore(app)
    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _configured = MutableStateFlow(store.configured)
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    /** Everything the pane could show, whether or not it is placed. */
    private val _all = MutableStateFlow<List<HaEntity>>(emptyList())
    val all: StateFlow<List<HaEntity>> = _all.asStateFlow()

    /** Saved arrangement, or null while it is still loading / never set. */
    private val _saved = MutableStateFlow<List<String>?>(null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * The arrangement, resolved against live state. Widgets naming an entity
     * that is gone stay in the saved layout — the entity may just be briefly
     * unavailable — but drop out here rather than drawing an empty box.
     */
    val cards: StateFlow<List<HomeCard>> =
        combine(_saved, _all) { saved, all ->
            val byId = all.associateBy { it.id }
            layoutOf(saved, all).mapNotNull { widget ->
                byId[widget.entityId]?.let { HomeCard(widget, it) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var socket: WebSocket? = null

    val baseUrl: String get() = store.baseUrl
    val token: String get() = store.token

    init {
        viewModelScope.launch {
            Prefs.homeLayout(getApplication()).collect { _saved.value = it }
        }
    }

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
            val parsed = mutableListOf<HaEntity>()
            for (i in 0 until all.length()) {
                val item = all.optJSONObject(i) ?: continue
                parse(item)?.let { parsed += it }
            }
            _all.value = parsed.sortedBy { it.name.lowercase(Locale.getDefault()) }
            _error.value = null
        }
    }

    /* ---------------- arrangement ---------------- */

    /** Appends a widget. Placing something already placed is a no-op. */
    fun addWidget(entityId: String) {
        val current = effective()
        if (current.any { it.entityId == entityId }) return
        val entity = _all.value.firstOrNull { it.id == entityId } ?: return
        // A thermostat needs the room; everything else starts narrow.
        save(current + HomeWidget(entityId, wide = entity.kind == WidgetKind.THERMOSTAT))
    }

    fun removeWidget(entityId: String) {
        save(effective().filterNot { it.entityId == entityId })
    }

    fun setWide(entityId: String, wide: Boolean) {
        save(effective().map { if (it.entityId == entityId) it.copy(wide = wide) else it })
    }

    /**
     * Moves the widget at [from] to [to], both indices into the *visible*
     * cards. Unresolved widgets sit between them in the saved layout, so the
     * move is replayed by entity id rather than by position.
     */
    fun moveWidget(from: Int, to: Int) {
        val visible = cards.value.map { it.id }
        if (from !in visible.indices || to !in visible.indices || from == to) return
        val layout = effective().toMutableList()
        val movedAt = layout.indexOfFirst { it.entityId == visible[from] }
        if (movedAt < 0) return
        val moved = layout.removeAt(movedAt)
        val anchorAt = layout.indexOfFirst { it.entityId == visible[to] }
        val insertAt = when {
            anchorAt < 0 -> layout.size
            from < to -> anchorAt + 1
            else -> anchorAt
        }
        layout.add(insertAt.coerceIn(0, layout.size), moved)
        save(layout)
    }

    /** Back to the derived arrangement, as if the pane had never been touched. */
    fun resetLayout() {
        viewModelScope.launch { Prefs.clearHomeLayout(getApplication()) }
    }

    private fun effective(): List<HomeWidget> = layoutOf(_saved.value, _all.value)

    private fun save(layout: List<HomeWidget>) {
        val lines = layout.distinctBy { it.entityId }.map { it.encode() }
        _saved.value = lines
        viewModelScope.launch { Prefs.setHomeLayout(getApplication(), lines) }
    }

    private fun layoutOf(saved: List<String>?, all: List<HaEntity>): List<HomeWidget> =
        saved?.mapNotNull { HomeWidget.decode(it) }?.distinctBy { it.entityId }
            ?: derive(all)

    /**
     * What the pane showed before it was arrangeable: a thermostat hero, the
     * indoor / humidity / draw strip, then the first eight controls. An owner
     * who never opens the arrange mode sees no change.
     */
    private fun derive(all: List<HaEntity>): List<HomeWidget> {
        if (all.isEmpty()) return emptyList()
        val out = mutableListOf<HomeWidget>()
        all.firstOrNull { it.domain == "climate" }?.let { out += HomeWidget(it.id, wide = true) }
        for (deviceClass in listOf("temperature", "humidity", "power")) {
            all.firstOrNull { it.domain == "sensor" && it.deviceClass == deviceClass }
                ?.let { out += HomeWidget(it.id) }
        }
        all.filter { it.kind == WidgetKind.TILE }.take(8).forEach { out += HomeWidget(it.id) }
        return out.distinctBy { it.entityId }
    }

    /* ---------------- control ---------------- */

    fun toggle(entity: HaEntity) {
        viewModelScope.launch {
            val service = entity.service
            // Optimistic: the websocket corrects us within a beat.
            update(entity.id) { it.flipped() }
            withContext(Dispatchers.IO) {
                post(
                    "/api/services/${entity.domain}/$service",
                    JSONObject().put("entity_id", entity.id).toString(),
                )
            }
        }
    }

    fun nudgeTarget(entity: HaEntity, delta: Double) {
        val next = ((entity.target ?: 21.0) + delta).coerceIn(5.0, 30.0)
        update(entity.id) { it.copy(target = next) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                post(
                    "/api/services/climate/set_temperature",
                    JSONObject().put("entity_id", entity.id).put("temperature", next).toString(),
                )
            }
        }
    }

    private fun update(id: String, change: (HaEntity) -> HaEntity) {
        _all.value = _all.value.map { if (it.id == id) change(it) else it }
    }

    private fun parse(item: JSONObject): HaEntity? {
        val id = item.optString("entity_id")
        if (!id.contains('.') || id.substringBefore('.') !in SUPPORTED) return null
        val attributes = item.optJSONObject("attributes") ?: JSONObject()
        val name = attributes.optString("friendly_name")
            .ifBlank { id.substringAfter('.').replace('_', ' ') }
        return HaEntity(
            id = id,
            name = name,
            state = item.optString("state"),
            unit = attributes.optString("unit_of_measurement"),
            deviceClass = attributes.optString("device_class"),
            target = attributes.optDouble("temperature").takeIf { !it.isNaN() },
            current = attributes.optDouble("current_temperature").takeIf { !it.isNaN() },
        )
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

    /** state_changed events keep the widgets honest without polling. */
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
                            val fresh = parse(newState) ?: return
                            val known = _all.value.any { it.id == fresh.id }
                            _all.value = if (known) {
                                _all.value.map { if (it.id == fresh.id) fresh else it }
                            } else {
                                // A newly exposed entity: keep the list sorted so
                                // it lands where the picker expects it.
                                (_all.value + fresh)
                                    .sortedBy { it.name.lowercase(Locale.getDefault()) }
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

    private companion object {
        /** Domains the pane knows how to draw and drive. */
        val SUPPORTED = setOf(
            "climate",
            "sensor",
            "binary_sensor",
            "light",
            "switch",
            "lock",
            "fan",
            "media_player",
            "humidifier",
            "cover",
            "input_boolean",
            "automation",
            "script",
            "scene",
        )
    }
}
