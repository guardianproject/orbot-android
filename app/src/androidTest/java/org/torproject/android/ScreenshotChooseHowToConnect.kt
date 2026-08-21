package org.torproject.android

import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers.anything
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.torproject.android.util.Prefs
import tools.fastlane.screengrab.Screengrab

class ScreenshotChooseHowToConnect : BaseScreenshotTest() {

    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)

    @Before
    fun setup() {
        Regionalization.countriesWithDnsttSupport
        when (Screengrab.getLocale()) {
            "fa" -> Prefs.bridgeCountry = Regionalization.IRAN
            "tr" -> Prefs.bridgeCountry = Regionalization.TURKEY
            "ru" -> Prefs.bridgeCountry = Regionalization.RUSSIA
            "zh_Hans", "zh_Hant" -> Prefs.bridgeCountry = Regionalization.CHINA
            else -> Prefs.bridgeCountry = ""
        }
    }

    @Test
    fun screenshotChooseHowToConnect() {
        onData(anything())
            .inAdapterView(withId(R.id.lvConnected))
            .atPosition(1)
            .perform(scrollTo(), click())

        Screengrab.screenshot("B-choose-how")
    }
}
