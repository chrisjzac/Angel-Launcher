package com.angel.launcher.money

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angel.launcher.BuildConfig
import com.angel.launcher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

data class Holding(
    val symbol: String,
    val name: String,
    val quantity: Double,
    val average: Double,
    val last: Double,
    val open: Double,
) {
    val value: Double get() = quantity * last
    val cost: Double get() = quantity * average
    val profit: Double get() = value - cost
    val profitPct: Double get() = if (cost == 0.0) 0.0 else (profit / cost) * 100
}

data class Portfolio(
    val value: Double,
    val cost: Double,
    val day: Double,
    val profit: Double,
    val profitPct: Double,
)

data class CategoryTotal(val key: String, val tint: Long, val total: Double)

data class Ledger(
    val out: Double = 0.0,
    val inn: Double = 0.0,
    val entries: Int = 0,
    val largest: Double = 0.0,
    val categories: List<CategoryTotal> = emptyList(),
    val parsed: List<Txn> = emptyList(),
    val skipped: List<ParseResult.Skipped> = emptyList(),
    val messages: Int = 0,
) {
    val net: Double get() = inn - out
}

class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val ledger: StateFlow<Ledger> = Prefs.messages(app)
        .map { messages -> fold(SmsParser.parseAll(messages), messages.size) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ledger())

    private val _holdings = MutableStateFlow(emptyList<Holding>())
    val holdings: StateFlow<List<Holding>> = _holdings.asStateFlow()

    private val _spark = MutableStateFlow<List<Double>>(emptyList())
    val spark: StateFlow<List<Double>> = _spark.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    /** Live quotes need a keyed API. Without one, prices are as imported. */
    val liveQuotes: Boolean = BuildConfig.QUOTES_API_KEY.isNotBlank()

    init {
        viewModelScope.launch {
            val saved = Prefs.holdings(app).first()
            if (saved != null) decode(saved)?.let { _holdings.value = it }
            _spark.value = listOf(portfolio().value)
        }
    }

    /**
     * Holdings from a CSV the user picked. There is no depository API open to
     * a launcher, so the export is the way in — CDSL Easi, NSDL or a broker.
     */
    fun importHoldings(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                _importResult.value = "Could not read that file"
                return@launch
            }

            val parsed = withContext(Dispatchers.Default) {
                runCatching {
                    // xlsx is a zip; anything else is read as text.
                    if (bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                        // Equity, mutual funds and combined are separate tabs;
                        // the fullest one is the one worth taking.
                        Xlsx.sheets(bytes.inputStream())
                            .map { HoldingsCsv.fromRows(it) }
                            .maxByOrNull { it.size }
                            .orEmpty()
                    } else {
                        HoldingsCsv.parse(bytes.decodeToString())
                    }
                }.getOrDefault(emptyList())
            }

            if (parsed.isEmpty()) {
                _importResult.value = "No holdings found in that file"
                return@launch
            }
            _holdings.value = parsed
            _spark.value = listOf(portfolio().value)
            Prefs.setHoldings(getApplication(), encode(parsed))
            _importResult.value = "Imported " + parsed.size + " holdings"
        }
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    fun rescan() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            delay(650)
            // Re-reading the store re-runs the parser; the flow republishes.
            Prefs.addMessages(getApplication(), emptyList())
            _scanning.value = false
        }
    }

    fun importText(raw: String) {
        val lines = raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        viewModelScope.launch { Prefs.addMessages(getApplication(), lines) }
    }

    fun forget() {
        viewModelScope.launch { Prefs.clearMessages(getApplication()) }
    }

    fun portfolio(): Portfolio {
        val holdings = _holdings.value
        val value = holdings.sumOf { it.value }
        val cost = holdings.sumOf { it.cost }
        val open = holdings.sumOf { it.quantity * it.open }
        return Portfolio(
            value = value,
            cost = cost,
            day = value - open,
            profit = value - cost,
            profitPct = if (cost == 0.0) 0.0 else ((value - cost) / cost) * 100,
        )
    }

    private fun fold(results: List<ParseResult>, messages: Int): Ledger {
        val parsed = results.filterIsInstance<ParseResult.Parsed>().map { it.txn }
        val skipped = results.filterIsInstance<ParseResult.Skipped>()
        val outs = parsed.filter { it.direction == Direction.OUT }
        val byCategory = outs.groupBy { it.category }
            .map { (category, rows) -> CategoryTotal(category.key, category.tint, rows.sumOf { it.amount }) }
            .sortedByDescending { it.total }
        return Ledger(
            out = outs.sumOf { it.amount },
            inn = parsed.filter { it.direction == Direction.IN }.sumOf { it.amount },
            entries = parsed.size,
            largest = outs.maxOfOrNull { it.amount } ?: 0.0,
            categories = byCategory,
            parsed = parsed,
            skipped = skipped,
            messages = messages,
        )
    }

    private fun encode(holdings: List<Holding>): String {
        val array = JSONArray()
        holdings.forEach {
            array.put(
                JSONObject()
                    .put("symbol", it.symbol)
                    .put("name", it.name)
                    .put("quantity", it.quantity)
                    .put("average", it.average)
                    .put("last", it.last)
                    .put("open", it.open),
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<Holding>? = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Holding(
                symbol = o.getString("symbol"),
                name = o.getString("name"),
                quantity = o.getDouble("quantity"),
                average = o.getDouble("average"),
                last = o.getDouble("last"),
                open = o.getDouble("open"),
            )
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}

private val inrFormat: NumberFormat = NumberFormat.getInstance(Locale("en", "IN"))

/** ₹ with Indian grouping, no decimals unless asked. */
fun inr(amount: Double, decimals: Int = 0): String {
    inrFormat.minimumFractionDigits = decimals
    inrFormat.maximumFractionDigits = decimals
    return "₹" + inrFormat.format(amount)
}

/** Whole share counts stay whole; fund units keep their fraction. */
fun units(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toLong().toString()
    else String.format(Locale.US, "%.3f", quantity)

fun signed(amount: Double, decimals: Int = 0): String =
    (if (amount >= 0) "+" else "−") + inr(abs(amount), decimals)
