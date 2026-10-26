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

    private fun parseToLocalCalendar(createdUtcIso: String?): java.util.Calendar? {
        if (createdUtcIso.isNullOrBlank()) return null
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val created = try {
            parser.parse(createdUtcIso.trim().take(19))
        } catch (_: Exception) {
            null
        } ?: return null
        return java.util.Calendar.getInstance().apply { time = created }
    }

    /** "7:05 PM" in the device's local time zone, for showing on every order card. */
    fun timeLabel(createdUtcIso: String?): String {
        val cal = parseToLocalCalendar(createdUtcIso) ?: return ""
        val formatter = SimpleDateFormat("h:mm a", Locale.US)
        return formatter.format(cal.time)
    }

    /** Groups an order's date into "Today" / "Yesterday" / "ddd, d MMM" for list section headers. */
    fun dateGroup(createdUtcIso: String?, nowMillis: Long = System.currentTimeMillis()): String {
        val cal = parseToLocalCalendar(createdUtcIso) ?: return "Older"
        val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val yesterday = (now.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }

        fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
            a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
                a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

        return when {
            sameDay(cal, now) -> "Today"
            sameDay(cal, yesterday) -> "Yesterday"
            else -> SimpleDateFormat("EEE, d MMM", Locale.US).format(cal.time)
        }
    }
}
