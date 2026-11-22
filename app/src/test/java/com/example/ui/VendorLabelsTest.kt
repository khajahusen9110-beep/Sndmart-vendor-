package com.example.ui

import org.junit.Assert.*
import org.junit.Test

class VendorLabelsTest {
    @Test fun hotel_keepsFoodWords_noShopOptions() {
        val l = VendorLabels()
        assertTrue(l.isHotel); assertFalse(l.isShop)
        assertEquals("Menu", l.navMenu); assertEquals("Add New Menu Item", l.itemDialogTitle(false)); assertEquals("🍳 Cooking Instructions", l.orderInstructions)
        assertFalse(l.allowsCustomisation); assertFalse(l.unitFirst)
    }

    @Test fun standardShop_getsProductWords_noCustomisation() {
        val l = VendorLabels.forShop("standard", "Kirana & Grocery")
        assertTrue(l.isShop); assertEquals(VendorKind.SHOP_STANDARD, l.kind)
        assertEquals("Products", l.navMenu); assertEquals("Add New Product", l.itemDialogTitle(false)); assertEquals("Edit Product", l.itemDialogTitle(true))
        assertEquals("Manage your Kirana & Grocery products", l.catalogSubtitle)
        assertFalse(l.allowsCustomisation); assertFalse(l.unitFirst)
    }

    @Test fun customisedShop_bakery_showsMadeToOrderOptions() {
        val l = VendorLabels.forShop("custom", "Bakery & Cakes")
        assertEquals(VendorKind.SHOP_CUSTOM, l.kind); assertTrue(l.allowsCustomisation); assertFalse(l.unitFirst)
    }

    @Test fun weightShop_isUnitFirst_andSpecsShopHasNoCustomisation() {
        assertTrue(VendorLabels.forShop("weight", "Vegetables & Fruits").unitFirst)
        val specs = VendorLabels.forShop("specs", "Footwear")
        assertEquals(VendorKind.SHOP_SPECS, specs.kind); assertFalse(specs.allowsCustomisation)
    }

    @Test fun unknownOrMissingTemplate_fallsBackToStandardProducts() {
        assertEquals(VendorKind.SHOP_STANDARD, VendorLabels.forShop(null, null).kind)
        assertEquals(VendorKind.SHOP_STANDARD, VendorLabels.forShop("prescription", "Medical").kind)
        assertEquals("Manage your shop products", VendorLabels.forShop(null, null).catalogSubtitle)
    }

    @Test fun shopWallet_wording_notHotel() {
        val l = VendorLabels.forShop("standard", null)
        assertTrue(l.creditedNote.contains("after commission")); assertEquals("Your Earning:", l.earningLabel); assertEquals("Items Subtotal:", l.itemsSubtotal)
    }
}
