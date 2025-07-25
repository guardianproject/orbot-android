package org.torproject.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import tools.fastlane.screengrab.Screengrab

class ScreenshotMoreFragment : BaseScreenshotTest() {
    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)

    @Test
    fun openMoreFragment() {

        val label = getContext()?.getString(R.string.menu_more)
        val bottomNavigationItemView = onView(
            allOf(
                withId(R.id.moreFragment), withContentDescription(label),
                childAtPosition(
                    childAtPosition(
                        withId(R.id.bottom_navigation),
                        0
                    ),
                    2
                ),
                isDisplayed()
            )
        )
        bottomNavigationItemView.perform(click())
        Screengrab.screenshot("more_screen")
    }
}