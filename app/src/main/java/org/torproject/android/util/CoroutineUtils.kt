package org.torproject.android.util

import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

object CoroutineUtils {

    /**
     * Blocks until a StateFlow's value changes to the value specified by targetValue or until
     * a given duration passes.
     *
     * If printValueWithTag is supplied, every change of the value will be logged with that tag
     */
    @Suppress("SameParameterValue") // currently for printValueWithTag
    suspend fun <T> waitUntilStateFlowEquals(
        flow: StateFlow<T>,
        targetValue: T,
        timeoutMillis: Duration,
        printValueWithTag: String? = null
    ): T? = withTimeoutOrNull(timeoutMillis) {
        if (flow.value == targetValue) return@withTimeoutOrNull flow.value
        flow.first { newValue ->
            printValueWithTag?.let { tag -> Log.d(tag, "$newValue") }
            newValue == targetValue
        }
    }
}