package org.torproject.android.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs

class OnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (Build.FINGERPRINT.contains("sdk_gphone")) {
                // on pixels emulated in new android studio on boot
                // gets launched every time you click run. this is annoying
                // for debugging and gets in the way of automated screenshots
                return
            }
            if (Prefs.startOnBoot() && !sReceivedBoot) {
                startService(context)
                sReceivedBoot = true
            }
        } catch (re: java.lang.RuntimeException) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }

    private fun startService(context: Context) {
        try {
            val intent = Intent(context, OrbotService::class.java).apply {
                this.action = OrbotConstants.ACTION_START
            }.putNotSystem()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else {
                context.startService(intent)
            }
        } catch (re: java.lang.RuntimeException) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }

    companion object {
        private var sReceivedBoot = false
    }
}