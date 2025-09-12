package org.torproject.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import org.torproject.android.R

/**
Class to setup default bottom sheet behavior for Config Connection, MOAT and any other
bottom sheets to come
 */
open class OrbotBottomSheetDialogFragment : BottomSheetDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireActivity(), theme)
        dialog.setOnShowListener {
            val bottomSheetView =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout
            bottomSheetView?.let {
                it.setBackgroundResource(R.drawable.bottom_sheet_rounded)
                it.setBackgroundColor(Color.TRANSPARENT)
                setHeightIfAttached(activity, it)
                val behavior = BottomSheetBehavior.from<FrameLayout>(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        return dialog
    }

    private fun setHeightIfAttached(activity: Activity?, bottomSheet: View) {
        activity?.let {
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            val height = (displayMetrics.heightPixels * getHeightRatio()).toInt()
            val layoutParams = bottomSheet.layoutParams
            layoutParams.height = height
            bottomSheet.layoutParams = layoutParams
        }
    }

    open fun getHeightRatio(): Float = 4 / 5f

    @SuppressLint("ClickableViewAccessibility")
    protected fun configureMultilineEditTextScrollEvent(editText: EditText) {
        // need this for scrolling an edittext in a BSDF
        editText.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }
}