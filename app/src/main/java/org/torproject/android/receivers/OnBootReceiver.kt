package org.torproject.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.torproject.android.service.OrbotService
import org.torproject.android.util.Prefs
import org.torproject.android.util.putNotSystem
import org.torproject.jni.TorService.ACTION_START
import java.lang.RuntimeException

class OnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
                intent.action != "android.intent.action.BOOT_COMPLETED")
                return

            // deploying code in Android Studio falsely triggers boot event
            if (Build.HARDWARE == "goldfish") {
                Log.w("OnBootReceiver", "Android emulator detected")
                return
            }

            if (Prefs.startOnBoot() && !sReceivedBoot) {
                startService(context)
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
    }
}
