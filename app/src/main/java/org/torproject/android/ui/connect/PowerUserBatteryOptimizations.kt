package org.torproject.android.ui.connect

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import org.torproject.android.R
import org.torproject.android.util.disableBatteryOptimizationAggressive

class PowerUserBatteryOptimizations : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimizations_dialog_msg_power_user)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.battery_optimization_title) { _, _ ->
                requireActivity().startActivity(requireActivity().disableBatteryOptimizationAggressive())
            }
            .show()
    }
}