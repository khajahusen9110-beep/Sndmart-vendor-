package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderAgeTest {
    // 2026-10-05T10:00:00Z
    private val now = 1_791_194_400_000L

    @Test
    fun minutesSince_parsesSupabaseTimestamp() {
        assertEquals(5L, OrderAge.minutesSince("2026-10-05T09:55:00.123456+00:00", now))
        assertEquals(0L, OrderAge.minutesSince("2026-10-05T09:59:40Z", now))
    }

    @Test
    fun minutesSince_futureClampsToZero_andBadInputIsNull() {
        assertEquals(0L, OrderAge.minutesSince("2026-10-05T10:03:00Z", now))
        assertNull(OrderAge.minutesSince(null, now))
        assertNull(OrderAge.minutesSince("not a date", now))
    }

    @Test
    fun label_formatsRanges() {
        assertEquals("", OrderAge.label(null))
        assertEquals("Just now", OrderAge.label(0))
        assertEquals("12 min ago", OrderAge.label(12))
        assertEquals("2 hr ago", OrderAge.label(130))
        assertEquals("1 day ago", OrderAge.label(1500))
    }
}
