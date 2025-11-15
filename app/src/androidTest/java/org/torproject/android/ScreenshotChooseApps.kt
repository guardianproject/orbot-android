package org.torproject.android

import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.torproject.android.service.OrbotConstants
import org.torproject.android.util.Prefs
import tools.fastlane.screengrab.Screengrab

@RunWith(AndroidJUnit4::class)
class ScreenshotChooseApps : BaseScreenshotTest() {


    @Before
    fun setupVpnAppsInSharedPrefs() {
        // "select" for the user Signal and Google Chrome
        // the test won't complain if Signal is missing, but having it installed shows the
        // suggested apps part of the AppManagerFragment (and we <3 Signal)
        // on your emulator, open Chrome and go to https://signal.org/android/apk and install it.
        Prefs.setContext(getContext())
        Prefs.isSecureWindow = false
        val prefs = Prefs.getSharedPrefs(getContext())!!
        prefs.edit {
            // Signal, if installed, should be at the top of the list since it's suggested by Orbot
            // Chrome, and whatever other non-suggested apps, will be alphabetized beneath signal.
            // Then sorted list of non-selected non-suggested apps
            putString(
                OrbotConstants.PREFS_KEY_TORIFIED,
                "org.thoughtcrime.securesms|com.android.chrome"
            )

        }
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
                    0
                )
            )
        )
        recyclerView.perform(actionOnItemAtPosition<ViewHolder>(2, click()))
        Thread.sleep(1500) // wait a long time for apps to load on the "Choose Apps" screen
        // TODO properly wait for desired view to appear in a proper espresso/unit-testy way
        Screengrab.screenshot("C-Choose_Apps")
    }
}
