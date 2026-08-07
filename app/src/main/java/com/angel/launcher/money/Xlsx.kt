package com.angel.launcher.money

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Just enough of the xlsx format to read a holdings export: every worksheet,
 * flattened to rows of strings.
 *
 * An xlsx is a zip of XML. A full spreadsheet library would be tens of
 * megabytes for the one thing needed here, so this reads the two parts that
 * matter — the shared string table and the sheet — with the standard library
 * and a small scanner. Pure Kotlin, so it is testable off-device.
 */
object Xlsx {

    /** Every worksheet, in file order: exports put holdings on varying tabs. */
    fun sheets(input: InputStream): List<List<List<String>>> {
        var shared: List<String> = emptyList()
        val sheets = sortedMapOf<String, String>()

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" ->
                        shared = sharedStrings(zip.readBytes().decodeToString())
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") ->
                        sheets[name] = zip.readBytes().decodeToString()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        return sheets.values.map { rowsOf(it, shared) }
    }

    /** Each <si> is one string, possibly split across styled runs of <t>. */
    private fun sharedStrings(xml: String): List<String> =
        blocks(xml, "si").map { item ->
            blocks(item, "t").joinToString("") { unescape(textOf(it)) }
        }

    private fun rowsOf(xml: String, shared: List<String>): List<List<String>> =
        blocks(xml, "row").map { row ->
            val cells = mutableListOf<String>()
            for (cell in blocks(row, "c")) {
                val at = columnOf(attribute(cell, "r"))
                // Empty cells are omitted from the file; keep the columns aligned.
                while (cells.size < at) cells.add("")
                cells.add(valueOf(cell, shared))
            }
            cells
        }

    private fun valueOf(cell: String, shared: List<String>): String {
        val type = attribute(cell, "t")
        val raw = blocks(cell, "v").firstOrNull()?.let { unescape(textOf(it)) }
        return when (type) {
            "s" -> raw?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "inlineStr" -> blocks(cell, "t").joinToString("") { unescape(textOf(it)) }
            else -> raw.orEmpty()
        }
    }

    /** "BC12" is column 54. Letters only, one-based in the file. */
    private fun columnOf(reference: String?): Int {
        var index = 0
        for (c in reference.orEmpty()) {
            if (!c.isLetter()) break
            index = index * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return (index - 1).coerceAtLeast(0)
    }

    /**
     * Every <name ...>…</name> in order, including self-closing <name/>, each
     * returned whole so it can be scanned again for what it contains.
     */
    private fun blocks(xml: String, name: String): List<String> {
        val found = mutableListOf<String>()
        var at = 0
        while (true) {
            val open = xml.indexOf("<$name", at)
            if (open < 0) break
            val after = open + name.length + 1
            // Guard against <c> matching <cols>: a tag ends at space, / or >.
            if (after < xml.length && xml[after].isLetterOrDigit()) {
                at = after
                continue
            }
            val headEnd = xml.indexOf('>', open)
            if (headEnd < 0) break
            if (xml[headEnd - 1] == '/') {
                found.add(xml.substring(open, headEnd + 1))
                at = headEnd + 1
                continue
            }
            val close = xml.indexOf("</$name>", headEnd)
            if (close < 0) break
            found.add(xml.substring(open, close + name.length + 3))
            at = close + name.length + 3
        }
        return found
    }

    /** The text between a block's opening and closing tag. */
    private fun textOf(block: String): String {
        val start = block.indexOf('>')
        val end = block.lastIndexOf("</")
        return if (start < 0 || end <= start) "" else block.substring(start + 1, end)
    }

    private fun attribute(block: String, name: String): String? {
        val key = "$name=\""
        val at = block.indexOf(key)
        if (at < 0) return null
        val end = block.indexOf('"', at + key.length)
        return if (end < 0) null else block.substring(at + key.length, end)
    }

    private fun unescape(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
