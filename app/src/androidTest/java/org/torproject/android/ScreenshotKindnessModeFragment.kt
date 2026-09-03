package org.torproject.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers.allOf
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
        Prefs.refreshWeeklyServedIfNeeded()
        repeat(5) { Prefs.addSnowflakeServed() }
        Prefs.bridgeCountry = ""
        Prefs.snowflakeNeedsQualityCheck = false
        Prefs.beSnowflakeProxy = true
        Prefs.snowflakeProxyRunning = true
        Prefs.lastSnowflakeNatType = IPtProxy.IPtProxy.NATUnrestricted
    }

    @Test
    fun openKindnessModeFragment() {
        val label = getContext()?.getString(R.string.menu_kindness)
        val bottomNavigationItemView = onView(
            allOf(
                withId(R.id.kindnessFragment), withContentDescription(label),
                childAtPosition(
                    childAtPosition(
                        withId(R.id.bottom_navigation),
                        0
                    ),
                    1
                ),
                isDisplayed()
            )
        )
        bottomNavigationItemView.perform(click())
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