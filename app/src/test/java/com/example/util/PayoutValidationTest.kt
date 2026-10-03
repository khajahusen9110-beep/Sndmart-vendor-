package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PayoutValidationTest {

    @Test
    fun `method must be chosen`() {
        assertEquals("Choose Bank account or UPI", PayoutValidation.validate(null, "Ravi", "", "", ""))
    }

    @Test
    fun `holder name is required`() {
        assertEquals(
            "Account holder name is required",
            PayoutValidation.validate("upi", "  ", "", "", "ravi@okaxis")
        )
    }

    @Test
    fun `valid bank account passes after normalising spaces and case`() {
        assertNull(PayoutValidation.validate("bank", "Ravi", "1234 5678 9012", "sbin0001234", ""))
    }

    @Test
    fun `bank account number must be 9 to 18 digits`() {
        val msg = "Enter a valid bank account number (9 to 18 digits)"
        assertEquals(msg, PayoutValidation.validate("bank", "Ravi", "12345678", "SBIN0001234", ""))
        assertEquals(msg, PayoutValidation.validate("bank", "Ravi", "1234567890123456789", "SBIN0001234", ""))
        assertEquals(msg, PayoutValidation.validate("bank", "Ravi", "12345ABC90", "SBIN0001234", ""))
    }

    @Test
    fun `ifsc must have bank code, zero and branch code`() {
        val msg = "Enter a valid IFSC code (e.g. SBIN0001234)"
        assertEquals(msg, PayoutValidation.validate("bank", "Ravi", "123456789", "SBIN1001234", ""))
        assertEquals(msg, PayoutValidation.validate("bank", "Ravi", "123456789", "SBI0001234", ""))
    }

    @Test
    fun `upi id is validated`() {
        val msg = "Enter a valid UPI ID (e.g. name@okaxis)"
        assertNull(PayoutValidation.validate("upi", "Ravi", "", "", " Ravi.K@OkAxis "))
        assertEquals(msg, PayoutValidation.validate("upi", "Ravi", "", "", "ravi"))
        assertEquals(msg, PayoutValidation.validate("upi", "Ravi", "", "", "ravi@ok1"))
    }
}
