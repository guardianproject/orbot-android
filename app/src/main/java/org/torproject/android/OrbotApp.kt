package org.torproject.android

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.torproject.android.localization.Languages
import org.torproject.android.localization.LocaleHelper
import org.torproject.android.service.circumvention.Transport.Companion.stateLocation
import org.torproject.android.util.Prefs
import org.torproject.android.util.Settings

import java.util.Locale

class OrbotApp : Application() {


    override fun onCreate() {
        super.onCreate()

        runBlocking {
            Settings.init(this@OrbotApp)
        }

        // set state dir for IPtProxy
        try {
            stateLocation = cacheDir.path
        } catch (_: Exception) {
            Log.e("OrbotApp", "Couldn't set PT state dir")
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                if (!isAuthenticationPromptOpenLegacyFlag)
                    shouldRequestAuthentication = true
            }

        })

//      useful for finding unclosed sockets...
//        StrictMode.setVmPolicy(
//            VmPolicy.Builder()
//                .detectLeakedClosableObjects()
//                .penaltyLog()
//                .build()
//        )

        Prefs.setContext(applicationContext)
        Prefs.orbotServiceLogClear()
        LocaleHelper.onAttach(applicationContext)

        Languages.setup(OrbotActivity::class.java, R.string.menu_settings)

        setLocale()

        // this code only runs on first install and app updates
        if (Settings.currentVersionForUpdate < BuildConfig.VERSION_CODE) {
            // Don't do anything resource intensive here, instead set a flag to do the task later.

            CoroutineScope(Dispatchers.IO).launch {
                Settings.set(
                    currentVersionForUpdate = BuildConfig.VERSION_CODE,

                    // Tell OrbotService it needs to reinstall geoip.
                    isGeoIpReinstallNeeded = true)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        setLocale()
    }

    fun setLocale() {
        val appLocale = Prefs.defaultLocale
        val systemLoc = Locale.getDefault().language
        if (appLocale != systemLoc) {
            Languages.setLanguage(this, appLocale, true)
        }
    }

    companion object {
        var shouldRequestAuthentication: Boolean = true

        // see https://github.com/guardianproject/orbot-android/issues/1340
        var isAuthenticationPromptOpenLegacyFlag: Boolean = false
        fun resetLockFlags() {
            shouldRequestAuthentication = true
            isAuthenticationPromptOpenLegacyFlag = false
        }
    }
}
