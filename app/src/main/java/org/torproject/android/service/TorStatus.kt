package org.torproject.android.service

import org.torproject.jni.TorService

enum class TorStatus(val value: String) {
    OFF(TorService.STATUS_OFF),
    STARTING(TorService.STATUS_STARTING),
    ON(TorService.STATUS_ON),
    STOPPING(TorService.STATUS_STOPPING);

    override fun toString(): String = value

    companion object {
        @JvmStatic
        fun from(value: String?): TorStatus? = entries.firstOrNull { it.value == value }
    }
}
