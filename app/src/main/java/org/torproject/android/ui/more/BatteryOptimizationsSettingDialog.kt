package org.torproject.android.ui.more

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import org.torproject.android.R
import org.torproject.android.service.vpn.VpnServicePrepareWrapper
import org.torproject.android.util.areBatteryOptimizationsDisabled
import org.torproject.android.util.openBatteryOptimizationAppListScreen

class BatteryOptimizationsSettingDialog : DialogFragment() {
    companion object {
        const val TAG = "BatteryOptimizationsSettingDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val msgBody =
            getString(R.string.battery_optimization_dialog_msg) + "\n\n" + getString(R.string.battery_optimization_dialog_msg_instructions) + "\n\n" + getString(
                R.string.battery_optimization_dialog_msg_disclaimer
            ) + "\n\n" + if (context?.areBatteryOptimizationsDisabled() == true) getString(R.string.battery_optimizations_are_disabled) else getString(
                R.string.battery_optimizations_are_enabled
            )

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.battery_optimization_title)
            .setMessage(msgBody)
            .setNeutralButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.battery_optimizations_dialog_btn_disable) { _, _ ->
                context?.startActivity(openBatteryOptimizationAppListScreen())
            }
            .setNegativeButton(R.string.open_vpn_settings) { _, _ ->
                VpnServicePrepareWrapper.openVpnSystemSettings(this)
            }
            .show()
    }
}