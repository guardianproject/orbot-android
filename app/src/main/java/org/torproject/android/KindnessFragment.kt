package org.torproject.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

import org.torproject.android.core.sendIntentToService
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.util.Prefs

class KindnessFragment : Fragment() {

    private lateinit var tvAllTimeTotal: TextView
    private lateinit var tvWeeklyTotal: TextView
    private lateinit var swVolunteerMode: SwitchCompat
    private lateinit var btnActionActivate: Button
    private lateinit var pnlActivate: View
    private lateinit var pnlStatus: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_kindness, container, false)
        tvAllTimeTotal = view.findViewById(R.id.tvAlltimeTotal)
        tvWeeklyTotal = view.findViewById(R.id.tvWeeklyTotal)
        swVolunteerMode = view.findViewById(R.id.swVolunteerMode)
        btnActionActivate = view.findViewById(R.id.btnActionActivate)
        pnlActivate = view.findViewById(R.id.panel_kindness_activate)
        pnlStatus = view.findViewById(R.id.panel_kindness_status)
        tvAllTimeTotal.text = Prefs.getSnowflakesServed().toString()
        tvWeeklyTotal.text = (Prefs.getSnowflakesServedWeekly()).toString()

        swVolunteerMode.isChecked = Prefs.beSnowflakeProxy()
        swVolunteerMode.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setBeSnowflakeProxy(isChecked)
            showPanelStatus(isChecked)
            requireContext().sendIntentToService(OrbotConstants.CMD_SNOWFLAKE_PROXY)
        }

        view.findViewById<TextView>(R.id.swVolunteerAdjust).setOnClickListener {
            KindessConfigBottomSheet().show(requireActivity().supportFragmentManager, CustomBridgeBottomSheet.TAG)
        }

        btnActionActivate.setOnClickListener {
            swVolunteerMode.isChecked = true
        }

        showPanelStatus(Prefs.beSnowflakeProxy())
        return view
    }

    private fun showPanelStatus(isActivated: Boolean) {
        val duration = 250L
        if (isActivated) {
            pnlActivate.animate().alpha(0f).setDuration(duration).withEndAction {
                pnlActivate.visibility = View.GONE
            }

            pnlStatus.visibility = View.VISIBLE
            pnlStatus.animate().alpha(1f).duration = duration
        } else {
            pnlActivate.visibility = View.VISIBLE
            pnlActivate.animate().alpha(1f).duration = duration

            pnlStatus.animate().alpha(0f).setDuration(duration).withEndAction {
                pnlStatus.visibility = View.GONE
            }
        }
    }
}