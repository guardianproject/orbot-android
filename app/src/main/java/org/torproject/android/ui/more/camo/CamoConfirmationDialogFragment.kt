package org.torproject.android.ui.more.camo

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import `in`.myinnos.library.AppIconNameChanger
import org.torproject.android.BuildConfig
import org.torproject.android.R
import org.torproject.android.service.util.Prefs

class CamoConfirmationDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()

        val mapping = CamoFragment.getCamoMapping(requireContext())

        val camoAppName = getString(args.getInt(BUNDLE_KEY_NAME))
        return AlertDialog.Builder(context)
            .setIcon(args.getInt(BUNDLE_KEY_IMAGE_ID))
            .setTitle(getTitle(camoAppName))
            .setMessage(getMessage(camoAppName))
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                dismiss()
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val activePackageName = mapping[camoAppName]
                Prefs.setCamoAppPackage(activePackageName)
                Prefs.camoAppDisplayName = camoAppName
                val disabledNames = mapping.values.filter { s -> s != activePackageName }
                AppIconNameChanger.Builder(requireActivity())
                    .packageName(BuildConfig.APPLICATION_ID)
                    .activeName(activePackageName)
                    .disableNames(disabledNames)
                    .build()
                    .setNow()
                activity?.finishAffinity()
            }
            .create()
    }

    private fun getTitle(camoAppName: String): String {
        return if (camoAppName == getString(R.string.app_name))
            getString(R.string.camo_dialog_disable_title)
        else getString(
            R.string.camo_dialog_title,
            camoAppName
        )
    }

    private fun getMessage(camoAppName: String): String {
        return if (camoAppName == getString(R.string.app_name))
            getString(R.string.camo_dialog_disable_confirm_msg)
        else getString(
            R.string.camo_dialog_enable_confirm_msg,
            camoAppName, camoAppName
        )
    }

    companion object {
        const val BUNDLE_KEY_IMAGE_ID = "id"
        const val BUNDLE_KEY_NAME = "name"
        const val TAG = "CamoConfirmDialog"
        fun newInstance(
            drawableId: Int,
            name: Int
        ): CamoConfirmationDialogFragment = CamoConfirmationDialogFragment().apply {
            arguments = bundleOf(
                BUNDLE_KEY_IMAGE_ID to drawableId,
                BUNDLE_KEY_NAME to name
            )
        }
    }
}