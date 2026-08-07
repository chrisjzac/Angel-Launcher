package com.angel.launcher.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Shapes taken from a real Zerodha holdings export: a summary preamble above
 * the header, the table starting at column B, quantity split across available
 * and pledged columns, and equity/funds/combined on separate sheets.
 */
class XlsxTest {

    private val sharedStrings = """
        <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <si><t>Client ID</t></si><si><t>Invested Value</t></si><si><t>Symbol</t></si>
        <si><t>ISIN</t></si><si><t>Sector</t></si><si><t>Quantity Available</t></si>
        <si><t>Quantity Long Term</t></si><si><t>Quantity Pledged (Margin)</t></si>
        <si><t>Average Price</t></si><si><t>Previous Closing Price</t></si>
        <si><t>Unrealized P&amp;L</t></si><si><t>PAYTM</t></si>
        <si><t>INE982J01020</t></si><si><t>FINANCIAL SERVICES</t></si>
        </sst>
    """.trimIndent()

    private val equitySheet = """
        <worksheet><sheetData>
        <row r="1"></row>
        <row r="2"><c r="B2" t="s"><v>0</v></c><c r="C2" t="s"><v>11</v></c></row>
        <row r="3"><c r="B3" t="s"><v>1</v></c><c r="C3"><v>23650.0000</v></c></row>
        <row r="4"></row>
        <row r="5"><c r="B5" t="s"><v>2</v></c><c r="C5" t="s"><v>3</v></c><c r="D5" t="s"><v>4</v></c>
        <c r="E5" t="s"><v>5</v></c><c r="F5" t="s"><v>6</v></c><c r="G5" t="s"><v>7</v></c>
        <c r="H5" t="s"><v>8</v></c><c r="I5" t="s"><v>9</v></c><c r="J5" t="s"><v>10</v></c></row>
        <row r="6"><c r="B6" t="s"><v>11</v></c><c r="C6" t="s"><v>12</v></c><c r="D6" t="s"><v>13</v></c>
        <c r="E6"><v>11.0000</v></c><c r="F6"><v>11.0000</v></c><c r="G6"><v>4.0000</v></c>
        <c r="H6"><v>2150.0000</v></c><c r="I6"><v>1447.5000</v></c><c r="J6"><v>-7727.5000</v></c></row>
        </sheetData></worksheet>
    """.trimIndent()

    /** No funds held: a summary and a header, no rows. */
    private val fundsSheet = """
        <worksheet><sheetData>
        <row r="1"><c r="B1" t="s"><v>1</v></c><c r="C1"><v>0.0000</v></c></row>
        <row r="2"><c r="B2" t="s"><v>2</v></c><c r="C2" t="s"><v>5</v></c></row>
        </sheetData></worksheet>
    """.trimIndent()

    private fun workbook(vararg parts: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `reads a zerodha equity export`() {
        val bytes = workbook(
            "xl/worksheets/sheet1.xml" to equitySheet,
            "xl/sharedStrings.xml" to sharedStrings,
        )

        val sheets = Xlsx.sheets(bytes.inputStream())
        val holdings = sheets.map { HoldingsCsv.fromRows(it) }.maxByOrNull { it.size }.orEmpty()

        assertEquals(1, holdings.size)
        assertEquals("PAYTM", holdings[0].symbol)
        // Available plus pledged; the long-term column is a subset of available.
        assertEquals(15.0, holdings[0].quantity, 0.001)
        assertEquals(2150.0, holdings[0].average, 0.001)
        assertEquals(1447.50, holdings[0].last, 0.001)
    }

    @Test
    fun `the sheet with holdings wins over the empty one`() {
        val bytes = workbook(
            "xl/worksheets/sheet1.xml" to fundsSheet,
            "xl/worksheets/sheet2.xml" to equitySheet,
            "xl/sharedStrings.xml" to sharedStrings,
        )

        val holdings = Xlsx.sheets(bytes.inputStream())
            .map { HoldingsCsv.fromRows(it) }
            .maxByOrNull { it.size }
            .orEmpty()

        assertEquals(1, holdings.size)
        assertEquals("PAYTM", holdings[0].symbol)
    }

    @Test
    fun `shared strings keep their escaped characters`() {
        val bytes = workbook(
            "xl/worksheets/sheet1.xml" to equitySheet,
            "xl/sharedStrings.xml" to sharedStrings,
        )

        val header = Xlsx.sheets(bytes.inputStream()).first().first { it.contains("Symbol") }

        assertTrue(header.contains("Unrealized P&L"))
    }

    @Test
    fun `a file that is not a workbook yields nothing`() {
        assertTrue(Xlsx.sheets(workbook("docProps/core.xml" to "<x/>").inputStream()).isEmpty())
    }
}
