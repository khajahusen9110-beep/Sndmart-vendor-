package com.example.ui.orders

import org.junit.Assert.*
import org.junit.Test

class OrderCardStyleTest {
    @Test fun everyKindOfOrderHasItsOwnLook() {
        val kinds = listOf("confirmed", "pending", "preparing", "ready", "out_for_delivery", "delivered", "cancelled").map { orderCardTheme(it) }
        assertTrue("stripes differ", kinds.map { it.stripe }.distinct().size >= 6)
        assertEquals(kinds.size, kinds.map { it.kind }.distinct().size)
    }

    @Test fun newOrderGlows_cancelledIsDashed_deliveredIsCalm() {
        assertTrue(orderCardTheme("confirmed").glow)
        assertTrue(orderCardTheme("cancelled").dashedBorder)
        assertFalse(orderCardTheme("delivered").glow); assertFalse(orderCardTheme("delivered").dashedBorder)
        assertEquals(orderCardTheme("cancelled").kind, orderCardTheme("rejected").kind)
        assertEquals(orderCardTheme("cancelled").kind, orderCardTheme(" CANCELED ").kind)
    }

    @Test fun cancelledHasNoShadow_newHasTheBiggest() {
        val all = listOf("confirmed", "preparing", "ready", "delivered", "cancelled").map { orderCardTheme(it) }
        assertEquals(0f, orderCardTheme("cancelled").shadow.value, 0f)
        assertEquals(all.maxOf { it.shadow.value }, orderCardTheme("confirmed").shadow.value, 0f)
    }

    @Test fun unknownStatus_isPlain() = assertEquals("other", orderCardTheme(null).kind)
}
