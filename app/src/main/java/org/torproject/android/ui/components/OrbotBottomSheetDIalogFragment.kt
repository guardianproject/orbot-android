package org.torproject.android.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.widget.EditText

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import org.torproject.android.R

/**
Class to setup default bottom sheet behavior for Config Connection, MOAT and any other
bottom sheets to come
 */
open class OrbotBottomSheetDialogFragment : BottomSheetDialogFragment() {
    private var backPressed = false
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = object : BottomSheetDialog(requireActivity(), theme) {
                @Deprecated("Deprecated in Java")
                override fun onBackPressed() {
                    super.onBackPressed()
                    backPressed = true
                }
            }
            dialog.setOnShowListener {setupRatio(dialog)}
            return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        if (!backPressed) {
            // todo this method only works for now because OrbotActivity is locked in portrait mode
           // closeAllSheetsInternal()
            dismiss()
        }
    }

    protected fun closeAllSheets() {
       // closeAllSheetsInternal()
        dismiss()
    }

    private fun setupRatio(bsd: BottomSheetDialog) {
        val bottomSheet = bsd.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            it.setBackgroundResource(R.drawable.bottom_sheet_rounded)
            it.setBackgroundColor(Color.TRANSPARENT)
            val behavior = BottomSheetBehavior.from(it)
            val layoutParams = it.layoutParams
            layoutParams.height = getHeight()
            bottomSheet.layoutParams = layoutParams
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun getHeight() : Int{
        // todo handle bigger device heights
        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics.heightPixels * 65 / 100
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun configureMultilineEditTextScrollEvent(editText: EditText) {
        // need this for scrolling an edittext in a BSDF
        editText.setOnTouchListener {v , event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }
}