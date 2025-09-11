package org.torproject.android.service.util

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService

/**
 * Extension function for `Intent` to add a flag that marks the intent as originating
 * from this application, rather than the system. This is necessary to distinguish
 * between Intents sent by the system (e.g., during boot) and those triggered by Orbot.
 *
 * @return The modified Intent with the EXTRA_NOT_SYSTEM flag set to `true`.
 */
fun Intent.putNotSystem(): Intent = this.putExtra(OrbotConstants.EXTRA_NOT_SYSTEM, true)

/**
 * Extension function for `Context` to send an Intent to a foreground service.
 * It ensures the Intent is marked with the `EXTRA_NOT_SYSTEM` flag by calling
 * the `putNotSystem()` extension.
 *
 * @param intent The Intent to be sent to the service.
 */
fun Context.sendIntentToService(intent: Intent) {
    //    Log.d("Orbot", "sendIntentToService-${intent.action}")
    if (canStartForegroundServices()) {
        ContextCompat.startForegroundService(this, intent.putNotSystem())
    } else {
        // Log.e("Orbot", "Need additional permissions to start OrbotService in foreground")
    }
}

fun Context.canStartForegroundServices(): Boolean {
    // https://developer.android.com/develop/background-work/services/fgs/service-types
    // if we are below API 34 we don't need additional permissions
    // on API 34+ we need the user to have granted the VPN status to Orbot,
    // or an explicit granting of the SCHEDULE_EXACT_ALARMS permission

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        return true

    // prepare returns null if the calling app is the active VPN app (has key icon)
    if (VpnService.prepare(this) == null)
        return true

    val alarmManager = ContextCompat.getSystemService(this, AlarmManager::class.java)
    return alarmManager?.canScheduleExactAlarms() ?: false
}

/**
 * Overloaded extension function for `Context` to send an Intent to a foreground service
 * using an action string. The action is applied to an Intent targeting the `OrbotService` class.
 *
 * Internally, it uses the `sendIntentToService(Intent)` method to dispatch the Intent.
 *
 * @param action The action string to set on the Intent before sending it to the service.
 */
fun Context.sendIntentToService(action: String) =
    sendIntentToService(
        Intent(this, OrbotService::class.java).apply {
            this.action = action
        }
    )

/**
 * Returns the first key corresponding to the given [value], or `null`
 * if such a value is not present in the map.
 *
 * This is O(n) complex which is pretty slow, only use for small
 * reverse map lookups and nothing that requires performance
 */
fun <K, V> Map<K, V>.getKey(value: V) =
    entries.firstOrNull { it.value == value }?.key

fun Context.showToast(msg: CharSequence) =
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

fun Context.showToast(@StringRes msgId: Int) =
    Toast.makeText(this, msgId, Toast.LENGTH_LONG).show()
