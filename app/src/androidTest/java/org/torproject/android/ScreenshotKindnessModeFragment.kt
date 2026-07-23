package org.torproject.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.torproject.android.util.Prefs
import org.torproject.android.util.putPref
import tools.fastlane.screengrab.Screengrab

class ScreenshotKindnessModeFragment : BaseScreenshotTest() {
    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)

    @Before
    fun setupPrefs() {

        repeat(5) { Prefs.addSnowflakeServed() }
        Prefs.resetSnowflakesServedWeekly()
        repeat(5) { Prefs.addSnowflakeServed() }
        Prefs.bridgeCountry = ""
        Prefs.snowflakeNeedsQualityCheck = false
        Prefs.beSnowflakeProxy = true
        Prefs.snowflakeProxyRunning = true
        Prefs.lastSnowflakeNatType = IPtProxy.IPtProxy.NATUnrestricted
    }

    @Test
    fun openKindnessModeFragment() {
        onView(withTagValue(`is`(R.id.kindnessFragment)))
            .perform(click())
        Screengrab.screenshot("D-kindness_mode_screen")
    }

    @After
    fun cleanup() {
        getContext()?.contentResolver?.apply {
            putPref("pref_snowflakes_served", 0)
            putPref("pref_snowflakes_served_weekly", 0)
        }
    }

}