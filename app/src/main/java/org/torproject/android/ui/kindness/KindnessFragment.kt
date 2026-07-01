package org.torproject.android.ui.kindness

import IPtProxy.IPtProxy
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.torproject.android.R
import org.torproject.android.Regionalization
import org.torproject.android.databinding.FragmentKindnessBinding
import org.torproject.android.util.Prefs

class KindnessFragment : Fragment() {

    private lateinit var mBinding: FragmentKindnessBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        mBinding = FragmentKindnessBinding.inflate(inflater)
        mBinding.swVolunteerMode.isChecked = Prefs.beSnowflakeProxy()
        mBinding.swVolunteerMode.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setBeSnowflakeProxy(isChecked)
            activity?.let {
                if (isChecked) {
                    SnowflakeProxyService.startSnowflakeProxyForegroundService(it)
                } else {
                    SnowflakeProxyService.stopSnowflakeProxyForegroundService(it)
                    updateNatTypeUi(IPtProxy.NATUnknown)
                }
                drawHeaderIcon()
            }
        }

        mBinding.rowUsageLimits.setOnClickListener {
            KindnessConfigBottomSheet.show(parentFragmentManager)
        }

        updateNatTypeUi(IPtProxy.NATUnknown)

        mBinding.rowProxyQuality.setOnClickListener {
            if (Prefs.lastSnowflakeNatType == IPtProxy.NATRestricted) {
                UPnPDialogFragment.show(parentFragmentManager)
            }
        }

        mBinding.btnActionContinue.setOnClickListener {
            TestingDialogFragment.show(parentFragmentManager)
        }

        if (Regionalization.isKindnessModeDisabledForCountry()) {
            mBinding.btnActionContinue.isEnabled = false

            // set text explaining that kindness mode isn't available from the user's country
            mBinding.tvActivateInstructions.text =
                getString(
                    R.string.kindness_mode_unsupported_country,
                    Regionalization.getLocalizedNameForCountryCode()
                )

            // set the activate button to be gray, making it not the primary button
            ViewCompat.setBackgroundTintList(
                mBinding.btnActionContinue,
                ColorStateList.valueOf(resources.getColor(R.color.orbot_btn_disable_grey, null))
            )
            ViewCompat.setBackgroundTintList(
                mBinding.btnActionLearnMore,
                ColorStateList.valueOf(resources.getColor(R.color.orbot_btn_enabled_purple, null))
            )
        }

        mBinding.btnActionLearnMore.setOnClickListener {
            activity?.let {
                startActivity(Intent(Intent.ACTION_VIEW, URL_ABOUT_KINDNESS.toUri()))
            }
        }

        showPanelStatus(!Prefs.snowflakeNeedsQualityCheck)

        parentFragmentManager.setFragmentResultListener(
            KindnessConfigBottomSheet.KEY_CONFIG_CHANGED,
            viewLifecycleOwner
        ) { _, _ ->
            // restart snowflake proxy if a setting has changed
            mBinding.swVolunteerMode.toggle()
            mBinding.swVolunteerMode.toggle()
            updateUsageLimitsUi()
        }

        parentFragmentManager.setFragmentResultListener(
            TestingDialogFragment.KEY_RESULT,
            viewLifecycleOwner
        ) { _, bundle ->

            if (bundle.getBoolean(TestingDialogFragment.KEY_RESULT)) {
                if (!Prefs.snowflakeNeedsQualityCheck) {
                    mBinding.swVolunteerMode.isChecked = true
                    showPanelStatus(true)
                }
            }
        }
        return mBinding.root
    }

    private fun drawHeaderIcon() {
        fun grayIcon() {
            mBinding.ivHeader.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
            mBinding.ivHeader.alpha = 1f
        }
        if (!Prefs.beSnowflakeProxy()) {
            mBinding.swVolunteerHeader.text = getString(R.string.Disabled)
            grayIcon()
            return
        }

        if (Prefs.snowflakeProxyRunning) {
            mBinding.ivHeader.clearColorFilter()
            mBinding.ivHeader.alpha = 0.5f
            mBinding.swVolunteerHeader.text = getString(R.string.Enabled)
        } else {
            grayIcon()
            mBinding.swVolunteerHeader.text = getString(R.string.Paused)
        }
    }

    override fun onResume() {
        super.onResume()
        // Updates these values when user returns to screen after running snowflake proxy for some time.
        updateUsageLimitsUi()
        updateNatTypeUi(Prefs.lastSnowflakeNatType)
        mBinding.tvAlltimeTotal.text = "${Prefs.snowflakesServed}"
        mBinding.tvWeeklyTotal.text = "${Prefs.snowflakesServedWeekly}"
        drawHeaderIcon()
    }

    private val natTypeObserver =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == Prefs.PREF_LAST_SNOWFLAKE_NAT_TYPE)
                updateNatTypeUi(Prefs.lastSnowflakeNatType)
            else if (key == Prefs.PREF_LAST_SNOWFLAKE_ACTIVE)
                drawHeaderIcon()
        }

    override fun onStart() {
        super.onStart()
        PreferenceManager
            .getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(natTypeObserver)
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager
            .getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(natTypeObserver)
    }

    private fun updateNatTypeUi(natType: String) {
        mBinding.tvProxyQualityStatus.text = when (natType) {
            IPtProxy.NATUnknown -> getString(R.string.kindness_proxy_quality_unknown)
            IPtProxy.NATRestricted -> getString(R.string.kindness_proxy_quality_restricted)
            IPtProxy.NATUnrestricted -> getString(R.string.kindness_proxy_quality_unrestricted)
            else -> natType
        }

        if (natType == IPtProxy.NATRestricted) {
            mBinding.redDot.visibility = View.VISIBLE
            mBinding.chevron2.visibility = View.VISIBLE
        } else {
            mBinding.redDot.visibility = View.GONE
            mBinding.chevron2.visibility = View.GONE
        }
    }

    private fun updateUsageLimitsUi() {
        mBinding.tvUsageLimitsStatus.text =
            getString(
                if (Prefs.limitSnowflakeProxyingWifi() || Prefs.limitSnowflakeProxyingCharging())
                    R.string.kindness_usage_limits_status_on
                else R.string.kindness_usage_limits_status_off
            )
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

    companion object {
        private const val URL_ABOUT_KINDNESS = "https://orbot.app/kindness"
    }
}
