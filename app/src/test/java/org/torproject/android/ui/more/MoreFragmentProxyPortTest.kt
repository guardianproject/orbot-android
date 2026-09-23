package org.torproject.android.ui.more

import kotlin.test.Test
import kotlin.test.assertEquals

// Related to issue https://github.com/guardianproject/orbot-android/issues/1134
// Introduced in PR https://github.com/guardianproject/orbot-android/pull/1812
class MoreFragmentProxyPortTest {
    private val notSet = MoreFragment.PORT_NOT_SET_STRING

    @Test
    fun disabledHttpDoesNotHideSocksPort() {
        assertEquals(notSet to "9050", proxyPortDisplayValues(0, 9050))
    }

    @Test
    fun disabledSocksDoesNotHideHttpPort() {
        assertEquals("8118" to notSet, proxyPortDisplayValues(8118, 0))
    }

    @Test
    fun activePortsAreBothShown() {
        assertEquals("8118" to "9050", proxyPortDisplayValues(8118, 9050))
    }
}
