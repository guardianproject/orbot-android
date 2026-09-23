package org.torproject.android.ui.kindness

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.torproject.android.ui.kindness.SnowflakeProxyService.Companion.shouldIgnoreSnowflakePreferenceChange
import org.torproject.android.util.Prefs

class SnowflakeProxyServiceTest {

    // see: https://github.com/guardianproject/orbot-android/pull/1808
    // see: https://github.com/guardianproject/orbot-android/pull/1810
    @Test
    fun relevantPreferenceChangesAreHandled() {
        assertFalse(shouldIgnoreSnowflakePreferenceChange(Prefs.PREF_BRIDGE_COUNTRY))
        assertFalse(shouldIgnoreSnowflakePreferenceChange(Prefs.PREF_CAMO_APP_PACKAGE))
    }

    // see: https://github.com/guardianproject/orbot-android/pull/1808
    // see: https://github.com/guardianproject/orbot-android/pull/1810
    @Test
    fun unrelatedPreferenceChangesAreIgnored() {
        assertTrue(shouldIgnoreSnowflakePreferenceChange("unrelated_preference"))
        assertTrue(shouldIgnoreSnowflakePreferenceChange(null))
    }
}
