package org.torproject.android.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomBridgeBottomSheetTest {

    // U+00A0 NO-BREAK SPACE, the character HTML email substitutes for a regular space.
    private val nbsp = "\u00A0"

    @Test
    fun nbspContaminatedObfs4BridgeIsValid() {
        val line = "obfs4${nbsp}192.0.2.5:443${nbsp}D9A82D2F9C2F65A18407B1D2B764F130847F8B5D" +
                "${nbsp}cert=bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo6ZgQMzLsaTzzQNTlm7hNcb+Sg"

        assertTrue(CustomBridgeBottomSheet.isValidBridge(line))
    }

    @Test
    fun nbspContaminatedVanillaBridgeIsValid() {
        val line = "192.0.2.5:443${nbsp}D9A82D2F9C2F65A18407B1D2B764F130847F8B5D"

        assertTrue(CustomBridgeBottomSheet.isValidBridge(line))
    }

    @Test
    fun plainAsciiSpaceBridgesStillValidate() {
        val obfs4 = "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D " +
                "cert=bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo6ZgQMzLsaTzzQNTlm7hNcb+Sg iat-mode=0"
        val vanilla = "192.0.2.5:443 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D"

        assertTrue(CustomBridgeBottomSheet.isValidBridge(obfs4))
        assertTrue(CustomBridgeBottomSheet.isValidBridge(vanilla))
    }

    @Test
    fun crlfSeparatedBridgesValidateAndSaveClean() {
        val input = "192.0.2.5:443 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D\r\n" +
                "192.0.2.6:443 A998F319ADB60EE344540EC4B21524CC484F96BE"

        assertTrue(CustomBridgeBottomSheet.isValidBridge(input))
        val lines = CustomBridgeBottomSheet.cleanBridgeLines(input)
        assertEquals(2, lines.size)
        assertFalse(lines.any { it.contains("\r") })
    }

    @Test
    fun blankLinesAreDroppedFromCleanedBridges() {
        val input = "192.0.2.5:443 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D\n\n" +
                "192.0.2.6:443 A998F319ADB60EE344540EC4B21524CC484F96BE\n"

        assertTrue(CustomBridgeBottomSheet.isValidBridge(input))
        assertEquals(2, CustomBridgeBottomSheet.cleanBridgeLines(input).size)
    }

    @Test
    fun malformedLineIsStillRejected() {
        assertFalse(CustomBridgeBottomSheet.isValidBridge("this is not a bridge line"))
    }

    @Test
    fun cleanBridgeLinesReplacesUnicodeSpacesOnly() {
        val input = "dnstt${nbsp}192.0.2.4:1${nbsp}A998F319ADB60EE344540EC4B21524CC484F96BE" +
                "${nbsp}pubkey=241169008830694749fe96bb070c4855c5bb5b9c47b3833ed7d88521ba30a43f" +
                "${nbsp}domain=t.ruhnama.net"
        val expected = "dnstt 192.0.2.4:1 A998F319ADB60EE344540EC4B21524CC484F96BE " +
                "pubkey=241169008830694749fe96bb070c4855c5bb5b9c47b3833ed7d88521ba30a43f " +
                "domain=t.ruhnama.net"

        assertEquals(listOf(expected), CustomBridgeBottomSheet.cleanBridgeLines(input))
    }
}
