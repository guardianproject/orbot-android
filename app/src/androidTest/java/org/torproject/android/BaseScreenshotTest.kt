package org.torproject.android

import android.content.Context
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.torproject.android.util.Prefs
import tools.fastlane.screengrab.locale.LocaleTestRule

@LargeTest
@RunWith(AndroidJUnit4::class)
abstract class BaseScreenshotTest {

    @Rule @JvmField
    val localeTestRule = LocaleTestRule()

    fun isGone() = getViewAssertion(ViewMatchers.Visibility.GONE)

    fun isVisible() = getViewAssertion(ViewMatchers.Visibility.VISIBLE)

    fun isInvisible() = getViewAssertion(ViewMatchers.Visibility.INVISIBLE)


    // all tests need this for OrbotService's notification
    @get:Rule
    var mGrantPermissionRule: GrantPermissionRule? =
        GrantPermissionRule.grant(
            "android.permission.POST_NOTIFICATIONS"
        )

    private fun getViewAssertion(visibility: ViewMatchers.Visibility): ViewAssertion? {
        return ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(visibility))
    }

    @Before
    fun setPrefs(){
        Prefs.setContext(getContext())
        Prefs.isSecureWindow = false
    }

    open fun getContext(): Context? {
        return InstrumentationRegistry.getInstrumentation().targetContext
    }

}