package org.torproject.android.ui.connect

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.torproject.android.R
import org.torproject.android.util.createWithCurves

class DNSTTConfirmationDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireActivity(), R.style.OrbotDialogTheme)
            .setTitle(R.string.limit_dns_tunnel_use)
            .setMessage(R.string.dns_tunnel_usage_description)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.connect) { _, _ ->
                dismiss()
                val parent = requireActivity().supportFragmentManager.findFragmentByTag(
                    ConfigConnectionBottomSheet.TAG
                ) as ConfigConnectionBottomSheet
                parent.closeAndConnect()
            }
            .createWithCurves()

    companion object {
        const val TAG = "DNSTTConfirmDialog"
    }
}