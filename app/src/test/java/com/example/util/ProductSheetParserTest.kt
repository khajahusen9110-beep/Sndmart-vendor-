package com.example.util

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProductSheetParserTest {
    @Test fun sampleFile_parsesAllFiveProducts() {
        val r = ProductSheetParser.toProducts(ProductSheetParser.parseCsv(ProductSheetParser.SAMPLE_CSV))
        assertNull(r.headerError); assertEquals(5, r.rows.size); assertTrue(r.problems.isEmpty())
        val rice = r.rows[0]
        assertEquals("Sona Masoori Rice", rice.name); assertEquals(62.0, rice.price, 0.0); assertEquals(70.0, rice.mrp!!, 0.0)
        assertEquals("1 kg", rice.unit); assertEquals("Rice & Grains", rice.category); assertEquals(100, rice.stock)
        assertEquals(5, r.rows[4].minOrderQty)
        assertNull(r.rows[3].mrp) // empty cell
    }

    @Test fun csv_quotesCommasAndExcelBom() {
        val csv = "﻿Product,Price,Unit\r\n\"Salt, iodised\",\"1,200\",\"1 kg\"\r\n\"Say \"\"hi\"\" pen\",15,pc\r\n"
        val r = ProductSheetParser.toProducts(ProductSheetParser.parseCsv(csv))
        assertEquals(2, r.rows.size)
        assertEquals("Salt, iodised", r.rows[0].name); assertEquals(1200.0, r.rows[0].price, 0.0)
        assertEquals("Say \"hi\" pen", r.rows[1].name)
    }

    @Test fun semicolonSeparated_andFlexibleHeaders_andRupeeSign() {
        val csv = "Item Name;Rate;Pack Size\nPen;₹ 12.50;1 pc\nBook;Rs. 40;1 pc"
        val r = ProductSheetParser.toProducts(ProductSheetParser.parseCsv(csv))
        assertEquals(2, r.rows.size); assertEquals(12.5, r.rows[0].price, 0.0); assertEquals(40.0, r.rows[1].price, 0.0); assertEquals("1 pc", r.rows[0].unit)
    }

    @Test fun badRows_areReportedWithTheirRowNumber() {
        val csv = "name,price\nGood,10\n,20\nNoPrice,\nBadPrice,abc\nNegative,-5\nGood,10\n"
        val r = ProductSheetParser.toProducts(ProductSheetParser.parseCsv(csv))
        assertEquals(1, r.rows.size)
        assertEquals(5, r.problems.size)
        assertTrue(r.problems[0].message.contains("Name is missing"))
        assertTrue(r.problems.last().message.contains("twice"))
    }

    @Test fun mrpNotAbovePrice_isIgnored_andMissingColumns_giveAHelpfulError() {
        val ok = ProductSheetParser.toProducts(ProductSheetParser.parseCsv("name,price,mrp\nA,50,40\nB,50,60\n"))
        assertNull(ok.rows[0].mrp); assertEquals(60.0, ok.rows[1].mrp!!, 0.0)
        val bad = ProductSheetParser.toProducts(ProductSheetParser.parseCsv("foo,bar\n1,2\n"))
        assertNotNull(bad.headerError); assertTrue(bad.rows.isEmpty())
        assertNotNull(ProductSheetParser.toProducts(emptyList()).headerError)
    }

    @Test fun tooManyRows_onlyTheFirstFiveHundredAreTaken() {
        val csv = "name,price\n" + (1..520).joinToString("\n") { "P$it,$it" }
        val r = ProductSheetParser.toProducts(ProductSheetParser.parseCsv(csv))
        assertEquals(500, r.rows.size); assertEquals(1, r.problems.size)
    }

    private fun xlsx(sheet: String, shared: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/sharedStrings.xml")); z.write(shared.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(sheet.toByteArray()); z.closeEntry()
        }
        return out.toByteArray()
    }

    @Test fun xlsx_sharedStrings_numbers_gaps_andEntities() {
        val shared = "<sst><si><t>name</t></si><si><t>price</t></si><si><t>unit</t></si><si><t>Tom &amp; Jerry Biscuit</t></si><si><r><t>1 </t></r><r><t>kg</t></r></si></sst>"
        val sheet = """<worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
            <row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2"><v>25.5</v></c><c r="C2" t="s"><v>4</v></c></row>
            <row r="3"><c r="A3" t="inlineStr"><is><t>Cash Pen</t></is></c><c r="B3"><v>10</v></c><c r="C3" s="2"/></row>
            </sheetData></worksheet>"""
        val r = ProductSheetParser.parseFile(xlsx(sheet, shared))
        assertNull(r.headerError); assertEquals(2, r.rows.size)
        assertEquals("Tom & Jerry Biscuit", r.rows[0].name); assertEquals(25.5, r.rows[0].price, 0.0); assertEquals("1 kg", r.rows[0].unit)
        assertEquals("Cash Pen", r.rows[1].name); assertNull(r.rows[1].unit)
    }

    @Test fun csvInWindowsEncoding_isStillRead() {
        val bytes = "name,price\nCafé Biscuit,20\n".toByteArray(Charsets.ISO_8859_1)
        val r = ProductSheetParser.parseFile(bytes)
        assertEquals(1, r.rows.size); assertEquals("Café Biscuit", r.rows[0].name)
    }
}
