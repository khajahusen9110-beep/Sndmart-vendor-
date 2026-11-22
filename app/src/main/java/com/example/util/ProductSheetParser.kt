package com.example.util

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** One product read from a vendor's sheet (CSV or Excel), ready to be created. */
data class ProductSheetRow(
    val rowNumber: Int,
    val name: String,
    val price: Double,
    val mrp: Double? = null,
    val unit: String? = null,
    val category: String? = null,
    val description: String? = null,
    val stock: Int? = null,
    val minOrderQty: Int? = null,
    val imageUrl: String? = null
)

data class ProductSheetProblem(val rowNumber: Int, val message: String)

data class ParsedSheet(val rows: List<ProductSheetRow>, val problems: List<ProductSheetProblem>, val headerError: String? = null)

data class BulkImportResult(val created: Int, val skipped: Int, val failed: Int, val messages: List<String>)

/**
 * Reads a product sheet. CSV (from Excel, Google Sheets or WhatsApp) and Excel (.xlsx) both work. Column names are
 * matched loosely (Name / Product, Price / Rate, MRP, Unit, Category ...) and the order of the columns does not matter.
 */
object ProductSheetParser {
    const val MAX_ROWS = 500

    val SAMPLE_CSV: String = listOf(
        "name,price,mrp,unit,category,description,stock,min_order_qty",
        "Sona Masoori Rice,62,70,1 kg,Rice & Grains,Fresh stock,100,1",
        "Toor Dal,140,150,1 kg,Dals,,100,1",
        "Tata Salt,28,30,1 kg,Essentials,,100,1",
        "Fortune Oil,135,,1 litre,Oils,,50,1",
        "Pen (pack of 10),90,100,pack,Stationery,Blue ink,200,5"
    ).joinToString("\r\n") + "\r\n"

    // ---------------------------------------------------------------- CSV
    /** RFC 4180 style: quotes, doubled quotes, commas / semicolons / tabs, CRLF, an Excel BOM. */
    fun parseCsv(input: String): List<List<String>> {
        val text = input.removePrefix("\uFEFF")
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } }?.takeIf { d -> firstLine.contains(d) } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endCell() { row.add(cell.toString().trim()); cell.setLength(0) }
        fun endRow() { endCell(); if (row.any { it.isNotEmpty() }) rows.add(row); row = ArrayList() }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> cell.append(c)
                }
                c == '"' -> inQuotes = true
                c == delimiter -> endCell()
                c == '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; endRow() }
                c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    // --------------------------------------------------------------- XLSX
    /** The first worksheet of an .xlsx file as a table of text cells. */
    fun parseXlsx(bytes: ByteArray): List<List<String>> {
        var shared = emptyList<String>()
        var sheet: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                when {
                    e.name == "xl/sharedStrings.xml" -> shared = parseSharedStrings(zip.readBytes().toString(Charsets.UTF_8))
                    e.name.startsWith("xl/worksheets/sheet") && e.name.endsWith(".xml") && (sheet == null || e.name == "xl/worksheets/sheet1.xml") ->
                        sheet = zip.readBytes().toString(Charsets.UTF_8)
                }
                e = zip.nextEntry
            }
        }
        return parseSheetXml(sheet ?: return emptyList(), shared)
    }

    private fun unescapeXml(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }.replace("&amp;", "&")

    private fun parseSharedStrings(xml: String): List<String> =
        Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
            Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(si.groupValues[1]).joinToString("") { unescapeXml(it.groupValues[1]) }
        }.toList()

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break }
        return n - 1
    }

    private fun parseSheetXml(xml: String, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        for (r in Regex("<row\\b[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val cells = HashMap<Int, String>()
            for (c in Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL).findAll(r.groupValues[1])) {
                val attrs = c.groupValues[1]
                val body = c.groupValues[2]
                val ref = Regex("\\br=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1) ?: continue
                val type = Regex("\\bt=\"(\\w+)\"").find(attrs)?.groupValues?.get(1)
                val value = when (type) {
                    "s" -> Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(body).joinToString("") { unescapeXml(it.groupValues[1]) }
                    else -> unescapeXml(Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.trim() ?: "")
                }
                if (value.isNotEmpty()) cells[colIndex(ref)] = value.trim()
            }
            if (cells.isEmpty()) continue
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            rows.add((0 until width).map { cells[it] ?: "" })
        }
        return rows
    }

    // ------------------------------------------------------------- mapping
    private fun norm(h: String) = h.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val ALIASES: Map<String, Set<String>> = mapOf(
        "name" to setOf("name", "product", "product name", "item", "item name", "title", "product title"),
        "price" to setOf("price", "selling price", "sale price", "rate", "sp", "amount", "price rs", "price inr"),
        "mrp" to setOf("mrp", "original price", "old price", "list price", "market price"),
        "unit" to setOf("unit", "pack", "pack size", "size", "weight", "quantity per pack", "uom"),
        "category" to setOf("category", "group", "type", "section"),
        "description" to setOf("description", "details", "desc", "about"),
        "stock" to setOf("stock", "stock qty", "stock quantity", "inventory", "available qty"),
        "min" to setOf("min order", "min qty", "minimum order", "minimum order quantity", "moq", "min order qty", "min order quantity"),
        "image" to setOf("image", "image url", "photo", "photo url", "picture", "image link")
    )

    private fun number(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val cleaned = s.replace(Regex("(?i)rs\\.?|inr|₹"), "").replace(",", "").replace(" ", "")
        return cleaned.toDoubleOrNull()
    }

    /** Turns a table (header row first) into products, with a clear message for every row that cannot be imported. */
    fun toProducts(table: List<List<String>>): ParsedSheet {
        if (table.isEmpty()) return ParsedSheet(emptyList(), emptyList(), "The file is empty.")
        // the header is the first row that has a name and a price column
        var headerIdx = -1
        var cols: Map<String, Int> = emptyMap()
        for ((idx, r) in table.take(10).withIndex()) {
            val m = HashMap<String, Int>()
            r.forEachIndexed { ci, h -> val n = norm(h); ALIASES.forEach { (k, set) -> if (n in set && k !in m) m[k] = ci } }
            if ("name" in m && "price" in m) { headerIdx = idx; cols = m; break }
        }
        if (headerIdx < 0) return ParsedSheet(emptyList(), emptyList(), "Could not find the columns. The first row must have at least \"name\" and \"price\". Use the sample file.")

        fun cell(r: List<String>, key: String): String? = cols[key]?.let { r.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
        val rows = ArrayList<ProductSheetRow>()
        val problems = ArrayList<ProductSheetProblem>()
        val seen = HashSet<String>()
        var dataRows = 0
        for ((idx, r) in table.withIndex()) {
            if (idx <= headerIdx || r.all { it.isBlank() }) continue
            dataRows++
            val rowNo = idx + 1
            if (dataRows > MAX_ROWS) { if (dataRows == MAX_ROWS + 1) problems.add(ProductSheetProblem(rowNo, "Only the first $MAX_ROWS products are imported at a time. Import the rest in another file.")); continue }
            val name = cell(r, "name")
            val priceText = cell(r, "price")
            val price = number(priceText)
            when {
                name == null -> { problems.add(ProductSheetProblem(rowNo, "Name is missing")); continue }
                name.length > 120 -> { problems.add(ProductSheetProblem(rowNo, "Name is too long (120 letters at most)")); continue }
                priceText == null -> { problems.add(ProductSheetProblem(rowNo, "\"$name\": price is missing")); continue }
                price == null || price <= 0 || price > 1_000_000 -> { problems.add(ProductSheetProblem(rowNo, "\"$name\": price \"$priceText\" is not a valid amount")); continue }
            }
            val unit = cell(r, "unit")
            val key = name!!.lowercase() + "|" + (unit ?: "").lowercase()
            if (!seen.add(key)) { problems.add(ProductSheetProblem(rowNo, "\"$name\" appears twice in the sheet (the first one is used)")); continue }
            val mrp = number(cell(r, "mrp"))?.takeIf { it > price!! }
            rows.add(
                ProductSheetRow(
                    rowNumber = rowNo, name = name, price = price!!, mrp = mrp, unit = unit,
                    category = cell(r, "category"), description = cell(r, "description"),
                    stock = number(cell(r, "stock"))?.toInt()?.coerceIn(0, 1_000_000),
                    minOrderQty = number(cell(r, "min"))?.toInt()?.coerceIn(1, 1000),
                    imageUrl = cell(r, "image")?.takeIf { it.startsWith("http", ignoreCase = true) }
                )
            )
        }
        if (rows.isEmpty() && problems.isEmpty()) return ParsedSheet(rows, problems, "No products found under the header row.")
        return ParsedSheet(rows, problems)
    }

    /** Reads the picked file: .xlsx (a zip) or text CSV. */
    fun parseFile(bytes: ByteArray): ParsedSheet {
        val isXlsx = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val table = if (isXlsx) parseXlsx(bytes) else {
            var text = bytes.toString(Charsets.UTF_8)
            if (text.contains('\uFFFD')) text = bytes.toString(Charsets.ISO_8859_1) // an Excel CSV saved in the old Windows encoding
            parseCsv(text)
        }
        return toProducts(table)
    }
}
