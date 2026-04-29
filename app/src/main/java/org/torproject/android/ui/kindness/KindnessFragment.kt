package org.torproject.android.ui.kindness

import android.Manifest.permission.ACCESS_LOCAL_NETWORK
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.torproject.android.R
import org.torproject.android.service.circumvention.BuiltInBridges
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.ui.connect.RequestPostNotificationPermission
import org.torproject.android.util.Prefs
import java.util.Locale
import kotlin.collections.contains

class KindnessFragment : Fragment() {

    companion object {
        const val TAG = "KindnessFragment"
    }

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
        getErrorStringIfAny()?.let {
            Prefs.setBeSnowflakeProxy(false)
        }
        swVolunteerMode.isChecked = Prefs.beSnowflakeProxy()
        swVolunteerMode.setOnCheckedChangeListener { _, isChecked ->
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

        view.findViewById<View>(R.id.ivGear).setOnClickListener {
            KindnessConfigBottomSheet.openKindnessSettings(requireActivity())
        }

        view.findViewById<View>(R.id.swVolunteerAdjust)
            .setOnClickListener { KindnessConfigBottomSheet.openKindnessSettings(requireActivity()) }

        btnActionActivate.setOnClickListener {
            if (!accessLocalNetworkGranted()) {
                @SuppressLint("NewApi") // already checking for API level in previous function
                requestPermissionLauncher.launch(ACCESS_LOCAL_NETWORK)
                return@setOnClickListener
            }
            getErrorStringIfAny()?.let {
                showDisabledDialog(it)
                return@setOnClickListener
            }
            swVolunteerMode.isChecked = true
        }

        showPanelStatus(Prefs.beSnowflakeProxy())
        return view
    }

    private fun accessLocalNetworkGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 37) return true
        val permCheck = ContextCompat.checkSelfPermission(
            requireContext(),
            ACCESS_LOCAL_NETWORK
        )
        when (permCheck) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.d(
                    TAG,
                    "Granted Permission $ACCESS_LOCAL_NETWORK"
                )
                return true
            }

            else -> {
                Log.d(
                    TAG,
                    "Try Prompting For $ACCESS_LOCAL_NETWORK"
                )
                return false
            }
        }

    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog.
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "User just granted $ACCESS_LOCAL_NETWORK")
        } else {
            Log.d(TAG, "$ACCESS_LOCAL_NETWORK denied")
            RequestPostNotificationPermission().show(
                requireActivity().supportFragmentManager,
                "RequestNotificationDialog"
            )
        }
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
        tvAllTimeTotal.text = "${Prefs.snowflakesServed}"
        tvWeeklyTotal.text = "${Prefs.snowflakesServedWeekly}"
    }

    private fun showPanelStatus(isActivated: Boolean) {
        val duration = 250L
        if (isActivated) {
            pnlActivate.animate().alpha(0f).setDuration(0).withEndAction {
                pnlActivate.visibility = View.GONE
            }

            pnlStatus.visibility = View.VISIBLE
            pnlStatus.animate().alpha(1f).duration = duration
        } else {
            pnlActivate.visibility = View.VISIBLE
            pnlActivate.animate().alpha(1f).duration = duration

            pnlStatus.animate().alpha(0f).setDuration(0).withEndAction {
                pnlStatus.visibility = View.GONE
            }
        }
    }
}