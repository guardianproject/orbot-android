package org.torproject.android.ui.connect

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import org.torproject.android.R
import org.torproject.android.util.Prefs
import org.torproject.android.util.areBatteryOptimizationsDisabled
import org.torproject.android.util.disableBatteryOptimizationAggressive

class PowerUserBatteryOptimizations : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimizations_dialog_msg_power_user)
            .setNegativeButton(R.string.btn_connect_anyway, null)
            .setNeutralButton(R.string.btn_connect_anyway_stop_showing) { _, _ ->
                Prefs.stopShowingPowerUserBatteryOptDialog = true
            }
            .setPositiveButton(R.string.battery_optimization_title) { _, _ ->
                dismiss()
                requireActivity().startActivity(requireActivity().disableBatteryOptimizationAggressive())
            }
            .show()
    }

    companion object {
        const val TAG = "PowerUserBatteryDialog"
        fun shouldShowDialog(context: Context): Boolean {
            if (Prefs.stopShowingPowerUserBatteryOptDialog) return false
            return !context.areBatteryOptimizationsDisabled()
        }
    }
}