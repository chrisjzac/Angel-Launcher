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
import kotlin.random.Random

data class Holding(
    val symbol: String,
    val name: String,
    val quantity: Int,
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

private val DEFAULT_HOLDINGS = listOf(
    Holding("INFY", "Infosys", 40, 1480.0, 1596.40, 1596.40),
    Holding("TCS", "Tata Consultancy", 12, 3720.0, 3588.15, 3588.15),
    Holding("HDFCBANK", "HDFC Bank", 25, 1520.0, 1673.80, 1673.80),
    Holding("RELIANCE", "Reliance", 18, 2740.0, 2891.05, 2891.05),
    Holding("NIFTYBEES", "Nifty 50 ETF", 150, 248.0, 271.60, 271.60),
)

class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val ledger: StateFlow<Ledger> = Prefs.messages(app)
        .map { messages -> fold(SmsParser.parseAll(messages), messages.size) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ledger())

    private val _holdings = MutableStateFlow(DEFAULT_HOLDINGS)
    val holdings: StateFlow<List<Holding>> = _holdings.asStateFlow()

    private val _spark = MutableStateFlow<List<Double>>(emptyList())
    val spark: StateFlow<List<Double>> = _spark.asStateFlow()

    /** True once holdings came from a real export rather than the sample set. */
    private val _imported = MutableStateFlow(false)
    val imported: StateFlow<Boolean> = _imported.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    /** Live quotes need a keyed API; without one the ticks are simulated. */
    val liveQuotes: Boolean = BuildConfig.QUOTES_API_KEY.isNotBlank()

    init {
        viewModelScope.launch {
            val saved = Prefs.holdings(app).first()
            if (saved != null) decode(saved)?.let { _holdings.value = it }
            _imported.value = Prefs.holdingsImported(app).first()
            _spark.value = listOf(portfolio().value)
        }
    }

    /**
     * Holdings from a CSV the user picked. There is no depository API open to
     * a launcher, so the export is the way in — CDSL Easi, NSDL or a broker.
     */
    fun importHoldings(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                _importResult.value = "Could not read that file"
                return@launch
            }
            val parsed = HoldingsCsv.parse(text)
            if (parsed.isEmpty()) {
                _importResult.value = "No holdings found in that file"
                return@launch
            }
            _holdings.value = parsed
            _imported.value = true
            _spark.value = listOf(portfolio().value)
            Prefs.setHoldings(getApplication(), encode(parsed))
            Prefs.setHoldingsImported(getApplication(), true)
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

    /** One market tick. Replace with a quotes API when a key is present. */
    fun tick() {
        if (liveQuotes || _imported.value) return
        _holdings.value = _holdings.value.map {
            it.copy(last = (it.last * (1 + (Random.nextDouble() - 0.5) * 0.005)).round2())
        }
        _spark.value = (_spark.value + portfolio().value).takeLast(40)
        viewModelScope.launch { Prefs.setHoldings(getApplication(), encode(_holdings.value)) }
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
                quantity = o.getInt("quantity"),
                average = o.getDouble("average"),
                last = o.getDouble("last"),
                open = o.getDouble("open"),
            )
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}

private fun Double.round2(): Double = Math.round(this * 100.0) / 100.0

private val inrFormat: NumberFormat = NumberFormat.getInstance(Locale("en", "IN"))

/** ₹ with Indian grouping, no decimals unless asked. */
fun inr(amount: Double, decimals: Int = 0): String {
    inrFormat.minimumFractionDigits = decimals
    inrFormat.maximumFractionDigits = decimals
    return "₹" + inrFormat.format(amount)
}

fun signed(amount: Double, decimals: Int = 0): String =
    (if (amount >= 0) "+" else "−") + inr(abs(amount), decimals)
