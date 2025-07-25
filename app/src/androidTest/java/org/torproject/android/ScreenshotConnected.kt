package org.torproject.android

import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHost
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test
import org.torproject.android.ui.connect.ConnectFragment
import org.torproject.android.ui.connect.ConnectUiState

class ScreenshotConnected : BaseScreenshotTest() {
    @get:Rule
    var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)

    @Test
    fun screenshotConnected() {
        mActivityScenarioRule.scenario.onActivity { activity ->
            val navHost = activity.supportFragmentManager.primaryNavigationFragment
            val connectFrag = navHost!!.childFragmentManager.fragments[0] as ConnectFragment
            Thread.sleep(200)
        }
    }

}