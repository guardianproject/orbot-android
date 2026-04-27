package org.torproject.android.ui.kindness

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import org.torproject.android.R
import org.torproject.android.util.Prefs
import org.torproject.android.ui.OrbotBottomSheetDialogFragment

class KindnessConfigBottomSheet : OrbotBottomSheetDialogFragment() {

    private lateinit var btnAction: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.kindess_config_bottom_sheet, container, false)
        v.findViewById<View>(R.id.tvCancel).setOnClickListener { dismiss() }
        btnAction = v.findViewById(R.id.btnAction)

        val configWifi = v.findViewById<SwitchCompat>(R.id.swKindnessConfigWifi)
        val configCharging = v.findViewById<SwitchCompat>(R.id.swKindnessConfigCharging)

        btnAction.setOnClickListener {
            Prefs.setBeSnowflakeProxyLimitWifi(configWifi.isChecked)
            Prefs.setBeSnowflakeProxyLimitCharging(configCharging.isChecked)
            setFragmentResult(BUNDLE_KEY_CONFIG_CHANGED, Bundle())
            dismiss()
        }

        configWifi.isChecked = Prefs.limitSnowflakeProxyingWifi()
        configCharging.isChecked = Prefs.limitSnowflakeProxyingCharging()
        return v
    }

    companion object {
        const val BUNDLE_KEY_CONFIG_CHANGED = "kindness_config_changed"

        fun openKindnessSettings(fragmentManager: FragmentManager) {
            KindnessConfigBottomSheet().show(
                fragmentManager,
                "KindnessConfig"
            )
        }
    }
}
