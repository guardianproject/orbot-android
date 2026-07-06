package org.torproject.android.ui.kindness

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import org.torproject.android.R
import org.torproject.android.util.NetworkUtils
import org.torproject.android.util.openSystemSettings

class UPnPDialogFragment : DialogFragment() {
    @SuppressLint("InlinedApi")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        var msg = String.format(
            "%s\n\n%s",
            getString(R.string.kindness_quality_upgrade_line1),
            getString(R.string.kindness_quality_upgrade_line2)
        )
        val builder = AlertDialog.Builder(requireContext())
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(R.string.kindness_quality_upgrade_title)
            .setMessage(msg)

        val accessLocalNetworkNeeded =
            NetworkUtils.needsAccessLocalNetworkPermission(requireContext())
                ?: return builder.create()
        if (accessLocalNetworkNeeded) {
            msg += "\n\n${getString(R.string.kindness_quality_upgrade_need_local_network)}"
            val repeatedlyDenied = ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                Manifest.permission.ACCESS_LOCAL_NETWORK
            )
            val permissionButtonText =
                if (repeatedlyDenied) R.string.set_permission_in_settings else R.string.grant_permission
            builder
                .setNeutralButton(permissionButtonText) { _, _ ->
                    if (repeatedlyDenied) {
                        openSystemSettings()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    }
                }
        } else {
            msg += "\n\n${getString(R.string.kindness_quality_upgrade_perm_granted)}"
        }
        return builder
            .setMessage(msg)
            .create()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }


    companion object {
        private const val TAG = "UPnPDialog"

        fun show(fragmentManager: FragmentManager) {
            UPnPDialogFragment().show(fragmentManager, TAG)
        }
    }
}