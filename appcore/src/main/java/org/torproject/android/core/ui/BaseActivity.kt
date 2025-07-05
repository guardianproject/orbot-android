package org.torproject.android.core.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import org.torproject.android.core.LocaleHelper
import org.torproject.android.service.util.Prefs

/**
 * Small subclass of AppCompatActivity, all activities in Orbot apps
 * should be subclassed from BaseActivity. Provides functionality for
 * handling on-the-fly custom Locale changing, as well as settings for
 * preventing screenshotting. If a change should be made to *every*
 * activity the logic should be incorporated into this class.
 */
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetSecureFlags()
        if (!Prefs.enableRotation()) {
            lockActivityOrientation()
        }
    }

    protected fun lockActivityOrientation() { /* TODO TODO TODO TODO TODO
        Currently there are a lot of problems with landscape mode and bugs resulting from
            rotation. To this end, Orbot will be locked into either portrait or landscape
            if the device is a tablet (whichever the app is set when an activity is created)
            until these things are fixed. On smaller devices it's just portrait...
            */
            val isTablet =
                resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE
            requestedOrientation = if (isTablet) {
                val currentOrientation = resources.configuration.orientation
                val lockedInOrientation =
                    if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                lockedInOrientation
            } else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // we need this for on the fly locale changes, especially for supported locales
    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(LocaleHelper.onAttach(newBase))

    override fun onResume() {
        super.onResume()
        LocaleHelper.onAttach(this)
        resetSecureFlags()
    }

    open fun resetSecureFlags() {
        if (Prefs.isSecureWindow)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

    }
}