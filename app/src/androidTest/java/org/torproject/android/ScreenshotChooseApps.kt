package org.torproject.android

import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.torproject.android.util.Prefs
import tools.fastlane.screengrab.Screengrab

class ScreenshotChooseApps : BaseScreenshotTest() {

    @Before
    fun setupVpnAppsInSharedPrefs() {
        // "select" for the user Signal and Google Chrome
        // the test won't complain if Signal is missing, but having it installed shows the
        // suggested apps part of the AppManagerFragment (and we <3 Signal)
        // on your emulator, open Chrome and go to https://signal.org/android/apk and install it.
        Prefs.setContext(getContext())
        Prefs.isSecureWindow = false
        Prefs.torifiedApps = "org.thoughtcrime.securesms|com.android.chrome"
    }

    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)


    @Test
    fun screenshotChooseApps() {
        onView(withTagValue(`is`(R.id.moreFragment)))
            .perform(click())
        onView(withId(R.id.rvMoreActions))
            .perform(actionOnItemAtPosition<ViewHolder>(0, click()))
        Thread.sleep(1650) // wait a long time for apps to load on the "Choose Apps" screen
        // TODO properly wait for desired view to appear in a proper espresso/unit-testy way
        Screengrab.screenshot("C-Choose_Apps")
    }
}
