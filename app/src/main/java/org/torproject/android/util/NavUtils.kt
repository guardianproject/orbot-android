package org.torproject.android.util

import androidx.navigation.NavOptions
import org.torproject.android.R

object NavUtils {
    val navOptionsLeftToRight by lazy {
        NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_right)
            .setPopExitAnim(R.anim.slide_out_left)
            .build()
    }

    val navOptionsRightToLeft by lazy {
        NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_left)
            .setExitAnim(R.anim.slide_out_right)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()
    }

    val navOrder = listOf(
        R.id.connectFragment,
        R.id.kindnessFragment,
        R.id.moreFragment
    )

    fun navIndex(id: Int) = navOrder.indexOf(id)
}
