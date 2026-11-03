package com.example.inventorymaster.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BarcodeColorHasherTest {

    @Test
    fun testSameDiAndBatchProduceIdenticalColor() {
        val udi1 = "(01)06931234567890(10)20261025(17)281025"
        val udi2 = "(01)06931234567890(10)20261025(17)291130" // Different expiry, same DI+Batch

        val hex1 = BarcodeColorHasher.getHexColor(udi1)
        val hex2 = BarcodeColorHasher.getHexColor(udi2)

        assertEquals("Same DI and Batch must produce identical color", hex1, hex2)
    }

    @Test
    fun testDifferentBatchProducesDifferentColor() {
        val udiBatch1 = "(01)06931234567890(10)20261025"
        val udiBatch2 = "(01)06931234567890(10)20261026"

        val hex1 = BarcodeColorHasher.getHexColor(udiBatch1)
        val hex2 = BarcodeColorHasher.getHexColor(udiBatch2)

        assertNotEquals("Different batch must produce different color", hex1, hex2)

        val hue1 = BarcodeColorHasher.calculateHue("06931234567890#20261025")
        val hue2 = BarcodeColorHasher.calculateHue("06931234567890#20261026")
        val diff = abs(hue1 - hue2)
        val minDiff = if (diff > 180f) 360f - diff else diff

        assertTrue("Golden ratio should provide significant hue divergence", minDiff > 40f)
    }

    @Test
    fun testNonUdiBarcodeProducesValidColor() {
        val raw = "CUSTOM-BOX-998877"
        val hex = BarcodeColorHasher.getHexColor(raw)

        assertTrue(hex.startsWith("#"))
        assertEquals(7, hex.length)
    }
}
