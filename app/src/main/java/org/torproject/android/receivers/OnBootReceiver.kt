package org.torproject.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import org.torproject.android.Regionalization
import org.torproject.android.service.OrbotService
import org.torproject.android.ui.kindness.SnowflakeProxyService
import org.torproject.android.util.Prefs
import org.torproject.android.util.Settings
import org.torproject.android.util.putNotSystem
import org.torproject.jni.TorService.ACTION_START

class OnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
                intent.action != "android.intent.action.BOOT_COMPLETED"
            )
                return

            // deploying code in Android Studio falsely triggers boot event
            if (SystemClock.uptimeMillis() > TEN_MINUTES_MS)
                return

            if (!sReceivedBoot) {
                if (Prefs.startOnBoot()) {
                    startService(context)
                }
                // Kindness Mode is its own standing choice: when the user leaves it on
                // they expect the proxy back after a reboot, independent of the
                // VPN's start-on-boot setting (#1799, #1783). BOOT_COMPLETED is
                // an exempted context for starting a foreground service.
                if (Settings.beSnowflakeProxy && !Regionalization.isKindnessModeDisabledForCountry()) {
                    SnowflakeProxyService.startSnowflakeProxyForegroundService(context)
                }

                sReceivedBoot = true
            }
        } catch (_: RuntimeException) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }

    private fun startService(context: Context) {
        try {
            val intent = Intent(context, OrbotService::class.java).apply {
                this.action = ACTION_START
            }.putNotSystem()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else {
                context.startService(intent)
            }
        } catch (_: RuntimeException) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }

    companion object {
        private var sReceivedBoot = false
        private const val TEN_MINUTES_MS = 60 * 10 * 1000
    }
}
