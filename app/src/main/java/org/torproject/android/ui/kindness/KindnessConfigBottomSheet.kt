package org.torproject.android.ui.kindness

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.SwitchCompat
import org.torproject.android.R
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.components.OrbotBottomSheetDialogFragment

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

            closeAllSheets()
        }

        configWifi.isChecked = Prefs.limitSnowflakeProxyingWifi()
        configCharging.isChecked = Prefs.limitSnowflakeProxyingCharging()
        return v
    }
}