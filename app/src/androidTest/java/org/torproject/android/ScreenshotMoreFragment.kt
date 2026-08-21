package org.torproject.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers.`is`
import org.junit.Rule
import org.junit.Test
import tools.fastlane.screengrab.Screengrab

class ScreenshotMoreFragment : BaseScreenshotTest() {
    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)

    @Test
    fun openMoreFragment() {
        onView(withTagValue(`is`(R.id.moreFragment)))
            .perform(click())
        Screengrab.screenshot("E-more_screen")
    }
}