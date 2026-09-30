package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility for Indian Standard Time (IST, Asia/Kolkata) date boundaries.
 * Accurately computes UTC ISO 8601 timestamps for IST day start (00:00:00)
 * and day end (00:00:00 next day) to avoid UTC vs IST day-boundary bugs.
 */
object IstDateUtils {
    val istTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

    /**
     * Returns a Pair of UTC ISO 8601 strings (startUtc, endUtc) representing
     * today's 00:00:00 to 24:00:00 in Asia/Kolkata.
     * E.g. for 2026-09-30 IST, returns:
     * ("2026-09-29T18:30:00Z", "2026-09-30T18:30:00Z")
     */
    fun getTodayIstRangeUtcStrings(nowMillis: Long = System.currentTimeMillis()): Pair<String, String> {
        val istCal = Calendar.getInstance(istTimeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfTodayIstEpoch = istCal.timeInMillis
        istCal.add(Calendar.DAY_OF_MONTH, 1)
        val endOfTodayIstEpoch = istCal.timeInMillis

        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = utcTimeZone
        }

        val startUtc = utcFormat.format(Date(startOfTodayIstEpoch))
        val endUtc = utcFormat.format(Date(endOfTodayIstEpoch))
        return Pair(startUtc, endUtc)
    }

    /**
     * Checks if a given UTC ISO 8601 string falls within the IST range [startUtcIso, endUtcIso).
     */
    fun isWithinUtcRange(createdUtcIso: String?, startUtcIso: String, endUtcIso: String): Boolean {
        if (createdUtcIso.isNullOrBlank()) return false
        val clean = createdUtcIso.trim()
        return clean >= startUtcIso && clean < endUtcIso
    }
}
