package org.torproject.android


import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test
import tools.fastlane.screengrab.Screengrab

class ScreenshotChooseHowToConnect : BaseScreenshotTest() {

    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)

    @Test
    fun openOrbotUnconnectedTest() {
        onView(withId(R.id.tvConfigure)).perform(click())
        onView(withId(R.id.rbDirect)).isVisible()
        Screengrab.screenshot("choose_how_to_connect")
    }
}
