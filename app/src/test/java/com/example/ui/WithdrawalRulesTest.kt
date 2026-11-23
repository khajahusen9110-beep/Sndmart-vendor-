package com.example.ui

import com.example.data.model.WithdrawalRules
import org.junit.Assert.assertEquals
import org.junit.Test

class WithdrawalRulesTest {
    private val rs: (Double) -> String = { "₹" + it.toLong() }

    @Test fun noRules_saysNothing() = assertEquals("", WithdrawalRules().summary(rs))

    @Test fun allRules_areOneLine() =
        assertEquals("Minimum ₹500 · Mon, Thu · up to ₹10000 at a time", WithdrawalRules(500.0, 10000.0, "Mon, Thu", false).summary(rs))

    @Test fun onlyMinimum() = assertEquals("Minimum ₹100", WithdrawalRules(minWithdrawal = 100.0).summary(rs))
}
