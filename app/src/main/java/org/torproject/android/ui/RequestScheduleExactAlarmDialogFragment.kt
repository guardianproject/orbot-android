package org.torproject.android.ui

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
import org.torproject.android.R

class RequestScheduleExactAlarmDialogFragment : DialogFragment() {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.power_user_mode_permission)
            .setMessage(R.string.power_user_mode_permission_msg)
            .setNegativeButton(
                android.R.string.cancel,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> dialog!!.cancel() })
            .setPositiveButton(
                android.R.string.yes,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                    startActivity(intent)
                    dismiss()
                })
            .create()
}