package com.angel.launcher.money

/**
 * Holdings from a CSV export — CDSL Easi, NSDL, or a broker's own download.
 *
 * There is no public depository API a launcher may call, so the file the user
 * already has is the honest route in. Every exporter names its columns
 * differently, so headers are matched by synonym rather than position, and the
 * header row is found by content because exports often carry a preamble.
 */
object HoldingsCsv {

    // Order is preference, not position: the first name that appears wins.
    private val SYMBOL = listOf(
        "symbol", "tradingsymbol", "instrument", "scrip", "scripname", "scripcode",
        "security", "securityid", "stock", "ticker", "isin",
    )
    private val NAME = listOf(
        "name", "company", "companyname", "securityname", "scripdescription", "description",
    )
    private val QUANTITY = listOf(
        "quantityavailable", "qty", "quantity", "shares", "units", "holding", "holdings",
        "freebalance", "currentbalance", "currentbal", "closingbalance", "balance",
    )

    /**
     * Pledged stock is still owned, so it is added to the free quantity.
     * "Long term" and "discrepant" are subsets of it and must not be.
     */
    private val ALSO_HELD = listOf("quantitypledgedmargin", "quantitypledgedloan")
    private val AVERAGE = listOf(
        "avg", "avgprice", "averageprice", "avgcost", "averagecost",
        "buyavg", "buyaverage", "costprice", "cost",
    )
    private val PRICE = listOf(
        "ltp", "lastprice", "lasttradedprice", "previousclosingprice", "previousclose",
        "prevclose", "marketprice", "closingprice", "closeprice", "close",
        "currentprice", "marketrate", "rate", "price", "nav",
    )

    fun parse(text: String): List<Holding> = fromRows(text.lineSequence().map { splitCsv(it) }.toList())

    /**
     * Shared by both importers: a spreadsheet and a CSV differ only in how the
     * rows were obtained.
     */
    fun fromRows(input: List<List<String>>): List<Holding> {
        val rows = input.filter { row -> row.any { it.isNotBlank() } }

        val headerAt = rows.indexOfFirst { row ->
            val keys = row.map { normalise(it) }
            keys.any { it in SYMBOL } && keys.any { it in QUANTITY }
        }
        if (headerAt < 0) return emptyList()

        val header = rows[headerAt].map { normalise(it) }
        fun column(names: List<String>): Int {
            for (name in names) {
                val at = header.indexOf(name)
                if (at >= 0) return at
            }
            return -1
        }

        val alsoHeldAt = ALSO_HELD.mapNotNull { name -> header.indexOf(name).takeIf { it >= 0 } }
        val symbolAt = column(SYMBOL)
        val quantityAt = column(QUANTITY)
        val nameAt = column(NAME)
        val averageAt = column(AVERAGE)
        val priceAt = column(PRICE)

        return rows.drop(headerAt + 1).mapNotNull { row ->
            val symbol = row.getOrNull(symbolAt)?.trim().orEmpty()
            val quantity = (number(row.getOrNull(quantityAt)) ?: 0.0) +
                alsoHeldAt.sumOf { number(row.getOrNull(it)) ?: 0.0 }
            if (symbol.isBlank() || quantity <= 0.0) return@mapNotNull null

            val average = number(row.getOrNull(averageAt))
            val price = number(row.getOrNull(priceAt))
            // An export without cost basis still charts fine; it just opens at
            // no profit rather than inventing one.
            val resolvedAverage = average ?: price ?: return@mapNotNull null
            val resolvedPrice = price ?: resolvedAverage

            Holding(
                symbol = symbol.uppercase(),
                name = row.getOrNull(nameAt)?.trim().orEmpty().ifBlank { symbol },
                quantity = quantity,
                average = resolvedAverage,
                last = resolvedPrice,
                open = resolvedPrice,
            )
        }
    }

    /** Header keys differ by punctuation and case far more than by wording. */
    private fun normalise(cell: String) = cell.lowercase().filter { it.isLetterOrDigit() }

    private fun number(cell: String?): Double? {
        val cleaned = cell?.filter { it.isDigit() || it == '.' || it == '-' }.orEmpty()
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Enough CSV for real exports: quoted fields, escaped quotes, commas within. */
    private fun splitCsv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    cells.add(cell.toString()); cell.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        cells.add(cell.toString())
        return cells.map { it.trim() }
    }
}
