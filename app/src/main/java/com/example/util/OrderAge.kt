package com.example.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** How long ago an order was placed, for the "New order" label on the Orders screen. */
object OrderAge {
    /** Whole minutes between [createdUtcIso] (Supabase timestamptz, UTC) and [nowMillis]; null if unparseable. */
    fun minutesSince(createdUtcIso: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (createdUtcIso.isNullOrBlank()) return null
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val created = parser.parse(createdUtcIso.trim().take(19))?.time ?: return null
            ((nowMillis - created) / 60_000L).coerceAtLeast(0L)
        } catch (_: Exception) {
            null
        }
    }

    fun label(minutes: Long?): String = when {
        minutes == null -> ""
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} day ago"
    }
}
