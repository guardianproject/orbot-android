package org.torproject.android.core

import android.content.Context
import android.content.Intent

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
fun Context.sendIntentToService(intent: Intent) =
    ContextCompat.startForegroundService(this, intent.putNotSystem())

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
