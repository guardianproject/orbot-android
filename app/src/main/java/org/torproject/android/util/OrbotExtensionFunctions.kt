package org.torproject.android.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import java.text.Normalizer
import androidx.core.net.toUri

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
    if (canStartForegroundServices()) {
        ContextCompat.startForegroundService(this, intent.putNotSystem())
    } else {
        Log.e(
            "OrbotService",
            "Need additional permissions to start OrbotService in foreground (action=${intent.action})"
        )
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

// gently take users to a screen, where if they dig, they may disable battery optimizations for Orbot
fun Context.openBatteryOptimizationAppListScreen(): Intent {
    return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}

// requires android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
@SuppressLint("BatteryLife")
fun Context.disableBatteryOptimizationAggressive(): Intent {
    return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        data = "package:${packageName}".toUri()
    }
}

fun Context.areBatteryOptimizationsDisabled(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
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

// remove accent marks and characters from a String, useful when searching
fun String.normalizie(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

/**
 * Useful when Fragment is being modified in another Scope/Thread ie an API Call, or IO Scope
 * Make sure it's added before accessing its UI... Returns true if unsafe to access
 *
 * https://github.com/guardianproject/orbot-android/issues/1564
 * https://stackoverflow.com/questions/50213823/ongetlayoutinflater-cannot-be-executed-until-the-fragment-is-attached-to-the-f
 *
 */
fun Fragment.haveIBeenDetached(): Boolean {
    val retVal = activity == null || isDetached || isRemoving
    if (retVal)
        Log.d(javaClass.simpleName, "has been detached on (other) Thread, aborting...")
    return retVal
}