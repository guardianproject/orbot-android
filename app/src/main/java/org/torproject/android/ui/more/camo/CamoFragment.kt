package org.torproject.android.ui.more.camo

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.torproject.android.R
import org.torproject.android.service.util.getKey
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.more.MoreActionAdapter
import org.torproject.android.ui.OrbotMenuAction
import kotlin.String

class CamoFragment : Fragment() {
    private var selectedApp: String? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camo, container, false)
        val rvCamoApps = view.findViewById<RecyclerView>(R.id.rvCamoApps)
        // defaults to "Orbot" if user never selected anything, aka no camo
        selectedApp = getCamoMapping(requireContext()).getKey(Prefs.selectedCamoApp)
        requireActivity().title = getString(R.string.pref_camo_mode_title)
        // add orbot to front of list, then sort rest of camo apps item by locale
        val listItems = mutableListOf(
            createAppMenuItem(R.drawable.ic_launcher_foreground, R.string.app_name)
        )
        listItems.addAll(
            listOf(
                createAppMenuItem(
                    R.drawable.ic_camouflage_todo,
                    R.string.app_icon_chooser_label_todo
                ),
                createAppMenuItem(
                    R.drawable.ic_camouflage_assistant,
                    R.string.app_icon_chooser_label_assistant
                ),
                createAppMenuItem(
                    R.drawable.ic_camouflage_tetras,
                    R.string.app_icon_chooser_label_tetras
                ),
                createAppMenuItem(
                    R.drawable.ic_camouflage_paint,
                    R.string.app_icon_chooser_label_paint
                ),
                createAppMenuItem(
                    R.drawable.ic_camouflage_night_watch,
                    R.string.app_icon_chooser_label_night_watch
                ),
                createAppMenuItem(
                    R.drawable.ic_camouflage_fitgrit,
                    R.string.app_icon_chooser_label_fit_grit
                ),
                createAppMenuItem(
                    R.drawable.ic_camouflage_birdie,
                    R.string.app_icon_chooser_label_birdie
                )
            ).sortedBy {
                requireContext().getString(it.textId)
            })
        rvCamoApps.adapter = MoreActionAdapter(listItems)
        val spanCount = if (resources.configuration.screenWidthDp < 600) 2 else 4
        rvCamoApps.layoutManager = GridLayoutManager(requireContext(), spanCount)
        if (hasSamsungOneUI()) {
            val tvSamsungOneUI = view.findViewById<TextView>(R.id.tvCamoSamsung)
            tvSamsungOneUI.visibility = View.VISIBLE
            tvSamsungOneUI.setOnClickListener {
                // open "Notifications part of Settings app
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_ASSISTANT_SETTINGS))
                else // just open the Settings app
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }
        return view
    }

    private fun showDialog(@DrawableRes imageId: Int, @StringRes appName: Int) {
        CamoConfirmationDialogFragment.newInstance(imageId, appName)
            .show(requireActivity().supportFragmentManager, CamoConfirmationDialogFragment.TAG)
    }

    private fun createAppMenuItem(
        @DrawableRes imageId: Int,
        @StringRes appName: Int
    ): OrbotMenuAction {
        val isSelected = selectedApp == getString(appName)
        val item = OrbotMenuAction(appName, imageId, removeTint = true) {
            if (!isSelected) {
                showDialog(imageId, appName)
            }
        }
        if (isSelected) item.backgroundColor =
            ContextCompat.getColor(requireContext(), R.color.panel_card_image)
        return item
    }

    /*
     * Samsung phones usually don't allow custom ROMs, so this check is fine.
     */
    private fun hasSamsungOneUI(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
               Build.BRAND.equals("samsung", ignoreCase = true)
    }

    companion object {
        fun getCamoMapping(context: Context): Map<String?, String> = mapOf<String?, String>(
            context.getString(R.string.app_name) to Prefs.DEFAULT_CAMO_DISABLED_ACTIVITY,
            context.getString(R.string.app_icon_chooser_label_fit_grit) to "org.torproject.android.main.FitGrit",
            context.getString(R.string.app_icon_chooser_label_night_watch) to "org.torproject.android.main.NightWatch",
            context.getString(R.string.app_icon_chooser_label_assistant) to "org.torproject.android.main.Assistant",
            context.getString(R.string.app_icon_chooser_label_paint) to "org.torproject.android.main.Paint",
            context.getString(R.string.app_icon_chooser_label_tetras) to "org.torproject.android.main.Tetras",
            context.getString(R.string.app_icon_chooser_label_todo) to "org.torproject.android.main.ToDo",
            context.getString(R.string.app_icon_chooser_label_birdie) to "org.torproject.android.main.Birdie"
        )
    }

}