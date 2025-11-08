package org.torproject.android.service.util

import android.content.Intent

import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

import org.torproject.android.R

import kotlin.reflect.KClass

sealed class NavigationTarget {
    data class FragmentTarget(val fragmentClass: KClass<out Fragment>) : NavigationTarget()
    data class ActivityTarget(val activityClass: KClass<out ComponentActivity>) : NavigationTarget()
}

fun FragmentActivity.navigateTo(
    target: NavigationTarget,
    addToBackStack: Boolean = true,
    animateTransition: Boolean = false,
    isForward: Boolean = true,
    finishCurrent: Boolean = false,
    containerId: Int = R.id.nav_fragment
) {
    when (target) {
        is NavigationTarget.FragmentTarget -> {
            val fragment = target.fragmentClass.java.getDeclaredConstructor().newInstance()
            val tx = supportFragmentManager.beginTransaction()

            if (animateTransition) {
                val animIn = if (isForward) R.anim.slide_in_right else R.anim.slide_in_left
                val animOut = if (isForward) R.anim.slide_out_left else R.anim.slide_out_right
                val popIn = if (isForward) R.anim.slide_in_left else R.anim.slide_in_right
                val popOut = if (isForward) R.anim.slide_out_right else R.anim.slide_out_left

                tx.setCustomAnimations(animIn, animOut, popIn, popOut)
            }

            tx.replace(containerId, fragment, target.fragmentClass.simpleName)

            if (addToBackStack) {
                tx.addToBackStack(target.fragmentClass.simpleName)
            }

            tx.commit()
        }

        is NavigationTarget.ActivityTarget -> {
            val intent = Intent(this, target.activityClass.java)
            startActivity(intent)
            if (finishCurrent) finish()
        }
    }
}

fun Fragment.navigateTo(
    target: NavigationTarget,
    addToBackStack: Boolean = true,
    animateTransition: Boolean = false,
    isForward: Boolean = true,
    finishCurrent: Boolean = false,
    containerId: Int = R.id.nav_fragment
) {
    requireActivity().navigateTo(
        target = target,
        addToBackStack = addToBackStack,
        animateTransition = animateTransition,
        isForward = isForward,
        finishCurrent = finishCurrent,
        containerId = containerId
    )
}
