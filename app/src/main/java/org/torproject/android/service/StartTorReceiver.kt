package org.torproject.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import androidx.core.content.ContextCompat
import org.torproject.android.service.OrbotConstants.STATUS_STARTS_DISABLED
import org.torproject.android.util.Prefs
import org.torproject.jni.TorService.ACTION_START
import org.torproject.jni.TorService.ACTION_STATUS
import org.torproject.jni.TorService.EXTRA_PACKAGE_NAME
import org.torproject.jni.TorService.EXTRA_STATUS

class StartTorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            /* sanitize the Intent before forwarding it to OrbotService */
            Prefs.setContext(context.applicationContext)
            val action = intent.action
            if (TextUtils.equals(action, ACTION_START)) {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (Prefs.allowBackgroundStarts) {
                    val startTorIntent = Intent(context, OrbotService::class.java)
                        .setAction(action)
                        .putExtra(OrbotConstants.EXTRA_NOT_SYSTEM, true)
                    if (packageName != null) {
                        startTorIntent.putExtra(EXTRA_PACKAGE_NAME, packageName)
                    }
                    ContextCompat.startForegroundService(context, startTorIntent)
                } else if (!TextUtils.isEmpty(packageName)) {
                    // let the requesting app know that the user has disabled starting via Intent
                    val startsDisabledIntent = Intent(ACTION_STATUS).apply {
                        putExtra(EXTRA_STATUS, STATUS_STARTS_DISABLED)
                        `package` = packageName
                    }
                    context.sendBroadcast(startsDisabledIntent)
                }
            }
        } catch (_: RuntimeException) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }
}
