package org.torproject.android.ui.core

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity

abstract class RequestScheduleExactAlarmDialogFragment : DialogFragment() {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireActivity())
            .setTitle(getTitleId())
            .setMessage(getMessageId())
            .setNegativeButton(
                android.R.string.cancel
            ) { dialog: DialogInterface?, which: Int -> dialog!!.cancel() }
            .setPositiveButton(
                getPositiveButtonId()
            ) { dialog: DialogInterface?, which: Int ->
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    setData(Uri.fromParts("package", requireContext().packageName, null))
                }
                startActivity(intent)
                dismiss()
            }.create()

    fun createTransactionAndShow(activity: FragmentActivity) {
        show(activity.supportFragmentManager, "RequestAlarmPermDialog")
    }


    protected abstract fun getTitleId(): Int
    protected abstract fun getMessageId(): Int

    protected open fun getPositiveButtonId() = android.R.string.ok
}