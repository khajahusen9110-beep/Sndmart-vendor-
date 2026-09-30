package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Shared currency and monetary calculation utility.
 * Handles monetary values using BigDecimal with RoundingMode.HALF_UP (2 decimals)
 * and provides consistent Indian Rupee (₹) formatting across the entire app.
 */
object CurrencyFormatter {
    private val decimalSymbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    // Standard 2 decimal places format with comma grouping: #,##0.00
    private val standardFormat = DecimalFormat("#,##0.00", decimalSymbols)

    fun toBigDecimal(amount: Double?): BigDecimal? {
        if (amount == null || amount.isNaN() || amount.isInfinite()) return null
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
    }

    fun toBigDecimal(amount: String?): BigDecimal? {
        if (amount.isNullOrBlank()) return null
        return try {
            val cleaned = amount.replace("₹", "").replace(",", "").trim()
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
        } catch (_: Exception) {
            null
        }
    }

    fun toBigDecimal(amount: Number?): BigDecimal {
        if (amount == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        return try {
            BigDecimal(amount.toString()).setScale(2, RoundingMode.HALF_UP)
        } catch (_: Exception) {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }
    }

    /**
     * Formats an amount into standard Indian Rupee notation: e.g. ₹1,250.00
     * Returns "--" if amount is null.
     */
    fun format(amount: BigDecimal?): String {
        if (amount == null) return "--"
        val scaled = amount.setScale(2, RoundingMode.HALF_UP)
        return "₹${standardFormat.format(scaled)}"
    }

    fun format(amount: Double?): String {
        val bd = toBigDecimal(amount) ?: return "--"
        return format(bd)
    }

    fun format(amount: Number?): String {
        if (amount == null) return "--"
        return format(toBigDecimal(amount))
    }

    /**
     * Formats signed currency, e.g. +₹120.00 or -₹50.00
     */
    fun formatSigned(amount: BigDecimal?, isCredit: Boolean): String {
        if (amount == null) return "--"
        val prefix = if (isCredit) "+" else "-"
        val absScaled = amount.abs().setScale(2, RoundingMode.HALF_UP)
        return "$prefix₹${standardFormat.format(absScaled)}"
    }

    fun formatSigned(amount: Double?, isCredit: Boolean): String {
        val bd = toBigDecimal(amount) ?: return "--"
        return formatSigned(bd, isCredit)
    }
}

// Convenient extension functions
fun Double?.toCurrencyString(): String = CurrencyFormatter.format(this)
fun BigDecimal?.toCurrencyString(): String = CurrencyFormatter.format(this)
fun Number?.toCurrencyString(): String = CurrencyFormatter.format(this)
fun Double?.toCurrencyBigDecimal(): BigDecimal = CurrencyFormatter.toBigDecimal(this) ?: BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
fun String?.toCurrencyBigDecimalOrNull(): BigDecimal? = CurrencyFormatter.toBigDecimal(this)
