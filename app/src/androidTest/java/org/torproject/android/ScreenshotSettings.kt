package org.torproject.android

import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers.`is`
import org.junit.Rule
import org.junit.Test
import tools.fastlane.screengrab.Screengrab

class ScreenshotSettings : BaseScreenshotTest() {
    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)

    @Test
    fun takeScreenshotOfGeneralSettings() {
        onView(withTagValue(`is`(R.id.moreFragment)))
            .perform(click())
        onView(withId(R.id.rvMoreActions))
            .perform(actionOnItemAtPosition<ViewHolder>(2, click()))
        Thread.sleep(300)

        Screengrab.screenshot("F-settings_screen")

    }
}