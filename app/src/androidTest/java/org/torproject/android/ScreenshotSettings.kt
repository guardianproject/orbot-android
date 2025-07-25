package org.torproject.android

import androidx.preference.CheckBoxPreference
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.torproject.android.ui.more.SettingsActivity
import org.torproject.android.ui.more.SettingsPreferenceFragment
import tools.fastlane.screengrab.Screengrab

@Suppress("DEPRECATION")
class ScreenshotSettings : BaseScreenshotTest() {
    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(SettingsActivity::class.java)

    @Before
    fun foo() {
        mActivityScenarioRule.scenario.onActivity { activity ->
            val frag = activity.supportFragmentManager.fragments[0] as SettingsPreferenceFragment

            // using !! to force test crash if preferences.xml is changed

            // hide some boring preferences
            frag.findPreference<CheckBoxPreference>("pref_allow_background_starts")!!.isVisible = false
            frag.findPreference<CheckBoxPreference>("pref_detect_root")!!.isVisible = false
            frag.findPreference<CheckBoxPreference>("pref_enable_rotation")!!.isVisible = false

            // turn on device authentication
            frag.findPreference<CheckBoxPreference>("pref_require_password")!!.isChecked = true
            frag.findPreference<CheckBoxPreference>("pref_auth_no_biometrics")!!.isEnabled = true
        }
    }

    @Test
    fun screenshotSettings() {
        onView(withId(R.id.settings_container)).isVisible()
        Screengrab.screenshot("settings_screen")
    }

}