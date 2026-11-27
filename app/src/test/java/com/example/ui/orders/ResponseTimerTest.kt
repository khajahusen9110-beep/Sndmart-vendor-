package com.example.ui.orders

import com.example.data.model.Order
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ResponseTimerTest {
    private val placed = Instant.parse("2026-11-27T10:00:00Z")

    private fun order(status: String, createdAt: String? = placed.toString()) = Order(id = "o1", status = status, created_at = createdAt)

    @Test fun fifteenMinutesFromPlacing() {
        assertEquals(15 * 60L, responseSecondsLeft(order("pending"), placed.toEpochMilli()))
        assertEquals(600L, responseSecondsLeft(order("pending"), placed.plusSeconds(5 * 60).toEpochMilli()))
    }

    @Test fun overdueIsNegative() {
        assertEquals(-60L, responseSecondsLeft(order("pending"), placed.plusSeconds(16 * 60).toEpochMilli()))
    }

    @Test fun onlyAWaitingOrderHasATimer() {
        assertNull(responseSecondsLeft(order("confirmed"), placed.toEpochMilli()))
        assertNull(responseSecondsLeft(order("pending", createdAt = null), placed.toEpochMilli()))
    }

    @Test fun clockFormat() {
        assertEquals("12:05", formatMinSec(12 * 60 + 5))
        assertEquals("0:09", formatMinSec(9))
    }
}
