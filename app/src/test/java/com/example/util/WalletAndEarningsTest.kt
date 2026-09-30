package com.example.util

import com.example.data.model.Order
import com.example.data.model.WalletTransaction
import com.example.data.model.WalletTransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WalletAndEarningsTest {

    // --- 1. CurrencyFormatter & BigDecimal Tests ---

    @Test
    fun testCurrencyFormatter_roundsHalfUpToTwoDecimals() {
        val bd1 = CurrencyFormatter.toBigDecimal(120.456)
        assertEquals(BigDecimal("120.46"), bd1)

        val bd2 = CurrencyFormatter.toBigDecimal(120.454)
        assertEquals(BigDecimal("120.45"), bd2)

        val bd3 = CurrencyFormatter.toBigDecimal(120.455)
        assertEquals(BigDecimal("120.46"), bd3)
    }

    @Test
    fun testCurrencyFormatter_nullReturnsDoubleDash() {
        val nullDouble: Double? = null
        assertEquals("--", CurrencyFormatter.format(nullDouble))

        val nullBd: BigDecimal? = null
        assertEquals("--", CurrencyFormatter.format(nullBd))
    }

    @Test
    fun testCurrencyFormatter_standardIndianRupeeFormat() {
        assertEquals("₹1,250.00", CurrencyFormatter.format(1250.0))
        assertEquals("₹10,500.50", CurrencyFormatter.format(10500.50))
        assertEquals("₹0.00", CurrencyFormatter.format(0.0))
    }

    @Test
    fun testCurrencyFormatter_formatSigned() {
        assertEquals("+₹120.00", CurrencyFormatter.formatSigned(120.0, isCredit = true))
        assertEquals("-₹50.00", CurrencyFormatter.formatSigned(50.0, isCredit = false))
        assertEquals("-₹50.00", CurrencyFormatter.formatSigned(-50.0, isCredit = false))
        assertEquals("--", CurrencyFormatter.formatSigned(null as Double?, isCredit = true))
    }

    @Test
    fun testCurrencyFormatter_stringParsing() {
        val parsed1 = CurrencyFormatter.toBigDecimal("₹1,250.50")
        assertEquals(BigDecimal("1250.50"), parsed1)

        val parsed2 = CurrencyFormatter.toBigDecimal("500")
        assertEquals(BigDecimal("500.00"), parsed2)

        assertNull(CurrencyFormatter.toBigDecimal(""))
        assertNull(CurrencyFormatter.toBigDecimal(null as String?))
        assertNull(CurrencyFormatter.toBigDecimal("invalid"))
    }

    // --- 2. WalletTransactionKind Mapping Tests ---

    @Test
    fun testWalletTransactionKind_creditAndOrderCredit() {
        val tx1 = WalletTransaction(type = "credit", amount = 100.0)
        assertEquals(WalletTransactionKind.CREDIT, tx1.kind)
        assertTrue(tx1.isCredit)
        assertFalse(tx1.isDebit)
        assertFalse(tx1.isNeutral)

        val tx2 = WalletTransaction(type = "order_credit", amount = 250.0)
        assertEquals(WalletTransactionKind.CREDIT, tx2.kind)
        assertTrue(tx2.isCredit)

        val txUpper = WalletTransaction(type = "CREDIT", amount = 150.0)
        assertEquals(WalletTransactionKind.CREDIT, txUpper.kind)
    }

    @Test
    fun testWalletTransactionKind_debitAndWithdrawal() {
        val tx1 = WalletTransaction(type = "debit", amount = 50.0)
        assertEquals(WalletTransactionKind.DEBIT, tx1.kind)
        assertTrue(tx1.isDebit)

        val tx2 = WalletTransaction(type = "withdrawal", amount = 500.0)
        assertEquals(WalletTransactionKind.DEBIT, tx2.kind)
        assertTrue(tx2.isDebit)

        val txPenalty = WalletTransaction(type = "penalty", amount = 20.0)
        assertEquals(WalletTransactionKind.DEBIT, txPenalty.kind)
        assertTrue(txPenalty.isDebit)
    }

    @Test
    fun testWalletTransactionKind_unknownTypesNeverSilentlyCredit() {
        // Unknown type with positive amount MUST NOT be CREDIT; should be NEUTRAL
        val txUnknownPositive = WalletTransaction(type = "bonus_adjustment", amount = 100.0)
        assertEquals(WalletTransactionKind.NEUTRAL, txUnknownPositive.kind)
        assertFalse(txUnknownPositive.isCredit)
        assertTrue(txUnknownPositive.isNeutral)

        // Refund with positive amount -> NEUTRAL
        val txRefund = WalletTransaction(type = "refund", amount = 80.0)
        assertEquals(WalletTransactionKind.NEUTRAL, txRefund.kind)
        assertFalse(txRefund.isCredit)
        assertTrue(txRefund.isNeutral)

        // Adjustment with positive amount -> NEUTRAL
        val txAdjustment = WalletTransaction(type = "adjustment", amount = 30.0)
        assertEquals(WalletTransactionKind.NEUTRAL, txAdjustment.kind)
        assertFalse(txAdjustment.isCredit)

        // Unknown type with negative amount -> DEBIT
        val txNegative = WalletTransaction(type = "unknown_fee", amount = -45.0)
        assertEquals(WalletTransactionKind.DEBIT, txNegative.kind)
        assertTrue(txNegative.isDebit)
    }

    // --- 3. IstDateUtils Tests ---

    @Test
    fun testIstDateUtils_startAndEndDifferenceIsExactly24Hours() {
        val (startUtc, endUtc) = IstDateUtils.getTodayIstRangeUtcStrings()
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startDate = format.parse(startUtc)!!
        val endDate = format.parse(endUtc)!!
        val diffHours = (endDate.time - startDate.time) / (1000 * 60 * 60)
        assertEquals(24L, diffHours)
    }

    @Test
    fun testIstDateUtils_isWithinUtcRange() {
        val startUtc = "2026-09-29T18:30:00Z"
        val endUtc = "2026-09-30T18:30:00Z"

        assertTrue(IstDateUtils.isWithinUtcRange("2026-09-29T18:30:00Z", startUtc, endUtc))
        assertTrue(IstDateUtils.isWithinUtcRange("2026-09-30T06:00:00Z", startUtc, endUtc))
        assertTrue(IstDateUtils.isWithinUtcRange("2026-09-30T18:29:59Z", startUtc, endUtc))

        assertFalse(IstDateUtils.isWithinUtcRange("2026-09-29T18:29:59Z", startUtc, endUtc))
        assertFalse(IstDateUtils.isWithinUtcRange("2026-09-30T18:30:00Z", startUtc, endUtc))
        assertFalse(IstDateUtils.isWithinUtcRange(null, startUtc, endUtc))
        assertFalse(IstDateUtils.isWithinUtcRange("", startUtc, endUtc))
    }

    // --- 4. Today Earnings Vendor Amount Logic Tests ---

    @Test
    fun testTodayEarningsVendorAmount_whenAllDeliveredHaveVendorAmount() {
        val deliveredOrders = listOf(
            Order(id = "1", status = "delivered", vendor_amount = 120.50),
            Order(id = "2", status = "delivered", vendor_amount = 89.25),
            Order(id = "3", status = "delivered", vendor_amount = 210.00)
        )

        val totalVendorAmount: BigDecimal? = if (deliveredOrders.isEmpty()) {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        } else {
            val allHave = deliveredOrders.all { it.vendor_amount != null }
            if (allHave) {
                deliveredOrders.fold(BigDecimal.ZERO) { acc, o ->
                    acc + (CurrencyFormatter.toBigDecimal(o.vendor_amount) ?: BigDecimal.ZERO)
                }.setScale(2, RoundingMode.HALF_UP)
            } else null
        }

        assertEquals(BigDecimal("419.75"), totalVendorAmount)
        assertEquals("₹419.75", CurrencyFormatter.format(totalVendorAmount))
    }

    @Test
    fun testTodayEarningsVendorAmount_whenAnyDeliveredMissingVendorAmount_returnsNull() {
        val deliveredOrders = listOf(
            Order(id = "1", status = "delivered", vendor_amount = 120.50),
            Order(id = "2", status = "delivered", vendor_amount = null), // missing!
            Order(id = "3", status = "delivered", vendor_amount = 210.00)
        )

        val totalVendorAmount: BigDecimal? = if (deliveredOrders.isEmpty()) {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        } else {
            val allHave = deliveredOrders.all { it.vendor_amount != null }
            if (allHave) {
                deliveredOrders.fold(BigDecimal.ZERO) { acc, o ->
                    acc + (CurrencyFormatter.toBigDecimal(o.vendor_amount) ?: BigDecimal.ZERO)
                }.setScale(2, RoundingMode.HALF_UP)
            } else null
        }

        assertNull(totalVendorAmount)
        // If null, UI shows "--"
        assertEquals("--", CurrencyFormatter.format(totalVendorAmount))
    }

    @Test
    fun testTodayEarningsVendorAmount_whenNoDeliveredOrders_returnsZero() {
        val deliveredOrders = emptyList<Order>()

        val totalVendorAmount: BigDecimal? = if (deliveredOrders.isEmpty()) {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        } else {
            val allHave = deliveredOrders.all { it.vendor_amount != null }
            if (allHave) {
                deliveredOrders.fold(BigDecimal.ZERO) { acc, o ->
                    acc + (CurrencyFormatter.toBigDecimal(o.vendor_amount) ?: BigDecimal.ZERO)
                }.setScale(2, RoundingMode.HALF_UP)
            } else null
        }

        assertEquals(BigDecimal("0.00"), totalVendorAmount)
        assertEquals("₹0.00", CurrencyFormatter.format(totalVendorAmount))
    }
}
