package org.torproject.android.ui.more.tipjar

import androidx.fragment.app.FragmentManager

/**
 * FOSS (non-Google Play) builds have no in-app billing, so the Orbot Tip Jar is hidden.
 * The "play" flavor provides the real implementation.
 */
object TipJar {
    const val isAvailable = false

    fun show(fragmentManager: FragmentManager) = Unit
}
