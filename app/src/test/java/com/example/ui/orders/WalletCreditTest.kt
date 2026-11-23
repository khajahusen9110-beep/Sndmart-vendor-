package com.example.ui.orders

import com.example.data.model.Order
import org.junit.Assert.*
import org.junit.Test

class WalletCreditTest {
    @Test fun usesTheServersNetAmount() =
        assertEquals(85.0, walletCreditFor(Order(id = "1", vendor_amount = 85.0, commission_amount = 15.0), 100.0), 0.0)

    @Test fun withoutServerAmount_itemAmountMinusCommission() =
        assertEquals(85.0, walletCreditFor(Order(id = "1", commission_amount = 15.0), 100.0), 0.0)

    @Test fun noCommissionInfo_itemAmount() = assertEquals(100.0, walletCreditFor(Order(id = "1"), 100.0), 0.0)

    @Test fun neverNegative() = assertEquals(0.0, walletCreditFor(Order(id = "1", commission_amount = 150.0), 100.0), 0.0)

    @Test fun noteOnlyWhenThereIsCommission() {
        assertNull(commissionNote(Order(id = "1"), 100.0))
        assertNotNull(commissionNote(Order(id = "1", commission_amount = 15.0), 100.0))
    }
}
