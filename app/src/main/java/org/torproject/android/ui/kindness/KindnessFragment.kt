package org.torproject.android.ui.kindness

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.torproject.android.R
import org.torproject.android.databinding.FragmentKindnessBinding
import org.torproject.android.service.circumvention.BuiltInBridges
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.util.Prefs
import java.net.URI
import java.util.Locale
import androidx.core.net.toUri

class KindnessFragment : Fragment() {

    private lateinit var mBinding: FragmentKindnessBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        mBinding = FragmentKindnessBinding.inflate(inflater)

        getErrorStringIfAny()?.let {
            Prefs.setBeSnowflakeProxy(false)
        }

        mBinding.swVolunteerMode.isChecked = Prefs.beSnowflakeProxy()
        mBinding.swVolunteerMode.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setBeSnowflakeProxy(isChecked)
            showPanelStatus(isChecked)
            activity?.let {
                if (isChecked) {
                    SnowflakeProxyService.startSnowflakeProxyForegroundService(it)
                } else {
                    SnowflakeProxyService.stopSnowflakeProxyForegroundService(it)
                }
            }
        }

        mBinding.ivGear.setOnClickListener {
            KindnessConfigBottomSheet.openKindnessSettings(requireActivity())
        }

        mBinding.swVolunteerAdjust
            .setOnClickListener { KindnessConfigBottomSheet.openKindnessSettings(requireActivity()) }

        mBinding.btnActionActivate.setOnClickListener {
            getErrorStringIfAny()?.let {
                showDisabledDialog(it)
                return@setOnClickListener
            }
            mBinding.swVolunteerMode.isChecked = true
        }

        mBinding.btnActionLearnMore.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, "https://orbot.app/kindness".toUri())
            val pm = context?.packageManager

            if (pm != null && i.resolveActivity(pm) != null) {
                startActivity(i)
            }
        }

        showPanelStatus(Prefs.beSnowflakeProxy())

        return mBinding.root
    }

    private fun getErrorStringIfAny(): Int? {
        val country = Prefs.bridgeCountry?.lowercase(Locale.getDefault())
        if (BuiltInBridges.dnsCountries.contains(country))
            return R.string.kindness_mode_cant_run_in_your_country
        if (Prefs.useVpn() && Prefs.transport != Transport.NONE)
            R.string.kindness_mode_cant_run_with_bridge
        if (!Prefs.hasDirectConnected) {
            return R.string.kindness_never_had_a_direct_connection
        }
        return null
    }

    private fun showDisabledDialog(msg: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.kindness_mode_cant_start)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // updates these values when user returns to screen after running snowflake proxy for some time
        mBinding.tvAlltimeTotal.text = "${Prefs.snowflakesServed}"
        mBinding.tvWeeklyTotal.text = "${Prefs.snowflakesServedWeekly}"
    }

    private fun showPanelStatus(isActivated: Boolean) {
        val duration = 250L
        if (isActivated) {
            mBinding.panelKindnessActivate.animate().alpha(0f).setDuration(0).withEndAction {
                mBinding.panelKindnessActivate.visibility = View.GONE
            }

            mBinding.panelKindnessStatus.visibility = View.VISIBLE
            mBinding.panelKindnessStatus.animate().alpha(1f).duration = duration
        } else {
            mBinding.panelKindnessActivate.visibility = View.VISIBLE
            mBinding.panelKindnessActivate.animate().alpha(1f).duration = duration

            mBinding.panelKindnessStatus.animate().alpha(0f).setDuration(0).withEndAction {
                mBinding.panelKindnessStatus.visibility = View.GONE
            }
        }
    }
}