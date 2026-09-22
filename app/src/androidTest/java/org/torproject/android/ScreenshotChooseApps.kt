package org.torproject.android

import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.torproject.android.util.Prefs
import org.torproject.android.util.Settings
import tools.fastlane.screengrab.Screengrab

class ScreenshotChooseApps : BaseScreenshotTest() {

    @Before
    suspend fun setupVpnAppsInSharedPrefs() {
        // "select" for the user Signal and Google Chrome
        // the test won't complain if Signal is missing, but having it installed shows the
        // suggested apps part of the AppManagerFragment (and we <3 Signal)
        // on your emulator, open Chrome and go to https://signal.org/android/apk and install it.
        getContext()?.let {
            Prefs.setContext(it)
            Settings.init(it)
        }
        Prefs.isSecureWindow = false
        Settings.torifiedApps = "org.thoughtcrime.securesms|com.android.chrome"
    }

    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)


    @Test
    fun screenshotChooseApps() {
        val bottomNavigationItemView = onView(
            allOf(
                withId(R.id.moreFragment),
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

        val recyclerView = onView(
            allOf(
                withId(R.id.rvMoreActions),
                childAtPosition(
                    withClassName(`is`("android.widget.LinearLayout")),
                    1
                )
            )
        )
        recyclerView.perform(actionOnItemAtPosition<ViewHolder>(0, click()))
        Thread.sleep(1650) // wait a long time for apps to load on the "Choose Apps" screen
        // TODO properly wait for desired view to appear in a proper espresso/unit-testy way
        Screengrab.screenshot("C-Choose_Apps")
    }
}
