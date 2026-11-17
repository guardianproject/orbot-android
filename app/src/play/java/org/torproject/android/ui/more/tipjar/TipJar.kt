package org.torproject.android.ui.more.tipjar

import androidx.fragment.app.FragmentManager

/**
 * Google Play builds: the Orbot Tip Jar uses Google Play Billing for one-time tips.
 * The "foss" flavor provides a stub that hides the Tip Jar.
 */
object TipJar {
    const val isAvailable = true

    fun show(fragmentManager: FragmentManager) = TipJarBottomSheet.show(fragmentManager)
}
