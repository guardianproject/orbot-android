package org.torproject.android.ui.connect

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.torproject.android.R
import org.torproject.android.service.vpn.VpnServicePrepareWrapper

class VpnAlwaysOnDialog : DialogFragment() {

    private var msg: String? = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        msg = arguments?.getString(EXTRA_MESSAGE)
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.unable_to_start_orbot_vpn_title)
            .setIcon(R.drawable.ic_vpn_key)
            .setMessage(msg)
        if (msg == getString(R.string.unable_to_start_unknown_reason_error_msg)) {
            builder.setNeutralButton(R.string.open_vpn_settings) { _, _ ->
                VpnServicePrepareWrapper.openVpnSystemSettings(this)
            }
            builder.setPositiveButton(android.R.string.ok, null)
        } else {
            builder.setNegativeButton(android.R.string.cancel, null)
            builder.setPositiveButton(R.string.open_vpn_settings) { _, _ ->
                VpnServicePrepareWrapper.openVpnSystemSettings(this)
            }
        }
        return builder.create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_MESSAGE, msg)
    }

    companion object {
        const val EXTRA_MESSAGE = "msg"
        const val TAG = "AlwaysOnDialog"
        fun newInstance(message: String): VpnAlwaysOnDialog =
            VpnAlwaysOnDialog().apply {
                arguments = Bundle().apply { putString(EXTRA_MESSAGE, message) }
            }
    }

}