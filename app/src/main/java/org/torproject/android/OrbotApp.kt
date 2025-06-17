package org.torproject.android

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.torproject.android.core.Languages
import org.torproject.android.core.LocaleHelper
import org.torproject.android.service.util.Prefs

import java.util.Locale

class OrbotApp : Application() {


    override fun onCreate() {
        super.onCreate()

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
        LocaleHelper.onAttach(applicationContext)

        Languages.setup(OrbotActivity::class.java, R.string.menu_settings)

        if (Prefs.getDefaultLocale() != Locale.getDefault().language) {
            Languages.setLanguage(this, Prefs.getDefaultLocale(), true)
        }

        // this code only runs on first install and app updates
        if (Prefs.getCurrentVersionForUpdate() < BuildConfig.VERSION_CODE) {
            Prefs.setCurrentVersionForUpdate(BuildConfig.VERSION_CODE)
            // don't do anything resource intensive here, instead set a flag to do the task later

            // tell OrbotService it needs to reinstall geoip
            Prefs.setIsGeoIpReinstallNeeded(true)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (Prefs.getDefaultLocale() != Locale.getDefault().language) {
            Languages.setLanguage(this, Prefs.getDefaultLocale(), true)
        }
    }

    fun setLocale() {
        val appLocale = Prefs.getDefaultLocale()
        val systemLoc = Locale.getDefault().language

        if (appLocale != systemLoc) {
            Languages.setLanguage(this, appLocale, true)
        }
    }

    companion object {
        var shouldRequestAuthentication: Boolean = true
        // see https://github.com/guardianproject/orbot-android/issues/1340
        var isAuthenticationPromptOpenLegacyFlag: Boolean = false
    }
}
