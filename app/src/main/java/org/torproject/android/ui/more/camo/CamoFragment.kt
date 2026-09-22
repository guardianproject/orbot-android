package org.torproject.android.ui.more.camo

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import org.torproject.android.R
import org.torproject.android.databinding.FragmentCamoBinding
import org.torproject.android.ui.OrbotMenuAction
import org.torproject.android.ui.more.MoreActionAdapter
import org.torproject.android.util.Prefs
import org.torproject.android.util.Settings
import org.torproject.android.util.getKey
import java.lang.reflect.Field

class CamoFragment : Fragment() {
    private lateinit var binding: FragmentCamoBinding
    private var selectedApp: String? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentCamoBinding.inflate(layoutInflater)
        // defaults to "Orbot" if user never selected anything, aka no camo
        selectedApp = getCamoMapping(requireContext()).getKey(Prefs.selectedCamoApp)
        val listItems = mutableListOf(
            createAppMenuItem(R.drawable.ic_launcher_foreground_kludge, R.string.app_name),
            createAppMenuItem(R.drawable.ic_launcher_foreground_alt1, R.string.app_name, 1),
            createAppMenuItem(R.drawable.ic_launcher_foreground_alt2, R.string.app_name, 2),
            createAppMenuItem(R.drawable.ic_launcher_foreground_alt3, R.string.app_name, 3),
            createAppMenuItem(R.drawable.ic_launcher_foreground_alt4, R.string.app_name, 4),
            createAppMenuItem(
                R.drawable.ic_camouflage_paint,
                R.string.app_icon_chooser_label_paint
            ),
            createAppMenuItem(
                R.drawable.ic_camouflage_tetras,
                R.string.app_icon_chooser_label_tetras
            ),
            createAppMenuItem(
                R.drawable.ic_camouflage_birdie,
                R.string.app_icon_chooser_label_birdie
            ),
            createAppMenuItem(
                R.drawable.ic_camouflage_fitgrit,
                R.string.app_icon_chooser_label_fit_grit
            ),
            createAppMenuItem(
                R.drawable.ic_camouflage_assistant,
                R.string.app_icon_chooser_label_assistant
            ),
            createAppMenuItem(R.drawable.ic_camouflage_todo, R.string.app_icon_chooser_label_todo),
            createAppMenuItem(
                R.drawable.ic_camouflage_night_watch,
                R.string.app_icon_chooser_label_night_watch
            )
        ).onEach { it.roundImageCorner = true }
        binding.rvCamoApps.adapter = MoreActionAdapter(listItems)
        val spanCount = if (resources.configuration.screenWidthDp < 600) 2 else 4
        binding.rvCamoApps.layoutManager = GridLayoutManager(requireContext(), spanCount)
        if (hasSamsungOneUI()) {
            binding.tvCamoSamsung.visibility = View.VISIBLE
            binding.tvCamoSamsung.setOnClickListener {
                // open Notifications part of Settings app
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_ASSISTANT_SETTINGS))
                else // just open the Settings app
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }

        with(binding.toolbar) {
            (context as AppCompatActivity).setSupportActionBar(this)
            setNavigationOnClickListener {
                // do something when click navigation
                (context as AppCompatActivity).supportFragmentManager.popBackStack()
            }
            title = getString(R.string.setting_app_icon_title)
        }
        return binding.root
    }

    private fun showDialog(@DrawableRes imageId: Int, @StringRes appName: Int, altIconValue: Int) {
        CamoConfirmationDialogFragment.newInstance(imageId, appName, altIconValue)
            .show(requireActivity().supportFragmentManager, CamoConfirmationDialogFragment.TAG)
    }

    private fun createAppMenuItem(
        @DrawableRes imageId: Int,
        @StringRes appName: Int,
        altIconVal: Int = Settings.DEFAULT_CAMO_ALT_ICON_VALUE
    ): OrbotMenuAction {
        var altSuffix = ""
        if (altIconVal != Settings.DEFAULT_CAMO_ALT_ICON_VALUE) altSuffix += altIconVal
        val isSelected = selectedApp == (getString(appName) + altSuffix)
        val item = OrbotMenuAction(appName, imageId, removeTint = true) {
            if (!isSelected) {
                showDialog(imageId, appName, altIconVal)
            }
        }
        if (isSelected) item.backgroundColor =
            ContextCompat.getColor(requireContext(), R.color.panel_card_image)
        return item
    }

    // https://stackoverflow.com/questions/60122037/how-can-i-detect-samsung-one-ui
    private fun hasSamsungOneUI(): Boolean {
        try {
            val semPlatformIntField: Field =
                Build.VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT")
            val version: Int = semPlatformIntField.getInt(null) - 90000
            return Build.FINGERPRINT.contains("samsung") && version >= 0
        } catch (_: NoSuchFieldException) {
            return false
        }
    }

    companion object {
        private const val BASE = "org.torproject.android.main."
        private const val ORBOT_ALT = "${BASE}OrbotAlt"
        fun getCamoMapping(context: Context): Map<String?, String> = mapOf(
            context.getString(R.string.app_name) to Settings.DEFAULT_CAMO_DISABLED_ACTIVITY,
            "${context.getString(R.string.app_name)}1" to "${ORBOT_ALT}1",
            "${context.getString(R.string.app_name)}2" to "${ORBOT_ALT}2",
            "${context.getString(R.string.app_name)}3" to "${ORBOT_ALT}3",
            "${context.getString(R.string.app_name)}4" to "${ORBOT_ALT}4",
            context.getString(R.string.app_icon_chooser_label_fit_grit) to "${BASE}FitGrit",
            context.getString(R.string.app_icon_chooser_label_night_watch) to "${BASE}NightWatch",
            context.getString(R.string.app_icon_chooser_label_assistant) to "${BASE}Assistant",
            context.getString(R.string.app_icon_chooser_label_paint) to "${BASE}Paint",
            context.getString(R.string.app_icon_chooser_label_tetras) to "${BASE}Tetras",
            context.getString(R.string.app_icon_chooser_label_todo) to "${BASE}ToDo",
            context.getString(R.string.app_icon_chooser_label_birdie) to "${BASE}Birdie"
        )
    }

}