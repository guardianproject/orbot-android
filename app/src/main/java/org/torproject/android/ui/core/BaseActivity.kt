package org.torproject.android.ui.core

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import org.torproject.android.localization.LocaleHelper
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
    }

    // we need this for on the fly locale changes, especially for supported locales
    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.attachBaseContext(newBase)
        } else
            super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
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
