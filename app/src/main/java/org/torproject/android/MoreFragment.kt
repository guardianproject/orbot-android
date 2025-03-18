package org.torproject.android

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView

import androidx.fragment.app.Fragment

import org.torproject.android.OrbotActivity.Companion.REQUEST_CODE_SETTINGS
import org.torproject.android.OrbotActivity.Companion.REQUEST_VPN_APP_SELECT
import org.torproject.android.core.sendIntentToService
import org.torproject.android.core.ui.SettingsActivity
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.ui.*
import org.torproject.android.ui.v3onionservice.OnionServiceActivity
import org.torproject.android.ui.v3onionservice.clientauth.ClientAuthActivity

class MoreFragment : Fragment() {
    private lateinit var lvMore: ListView

    private var httpPort = -1
    private var socksPort = -1

    private lateinit var tvStatus: TextView

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        (activity as OrbotActivity).fragMore = this
    }


    fun setPorts(newHttpPort: Int, newSocksPort: Int) {
        httpPort = newHttpPort
        socksPort = newSocksPort
        if (view != null) updateStatus()
    }

    private fun updateStatus() {
        val sb = StringBuilder()

        sb.append(getString(R.string.proxy_ports)).append(" ")

        if (httpPort != -1 && socksPort != -1) {
            sb.append("\nHTTP: ").append(httpPort).append(" - ").append(" SOCKS: ")
                .append(socksPort)
        } else {
            sb.append(": " + getString(R.string.ports_not_set))
        }

        sb.append("\n\n")

        val manager = requireActivity().packageManager
        val info =
            manager.getPackageInfo(requireActivity().packageName, PackageManager.GET_ACTIVITIES)
        sb.append(getString(R.string.app_name)).append(" ").append(info.versionName).append("\n")
        sb.append("Tor v").append(getTorVersion())

        tvStatus.text = sb.toString()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_more, container, false)
        tvStatus = view.findViewById(R.id.tvVersion)

        updateStatus()
        lvMore = view.findViewById(R.id.lvMoreActions)

        val listItems =
            arrayListOf(OrbotMenuAction(R.string.v3_hosted_services, R.drawable.ic_menu_onion) {
                startActivity(Intent(requireActivity(), OnionServiceActivity::class.java))
            },
                OrbotMenuAction(
                    R.string.v3_client_auth_activity_title, R.drawable.ic_shield
                ) { startActivity(Intent(requireActivity(), ClientAuthActivity::class.java)) },
                OrbotMenuAction(R.string.btn_choose_apps, R.drawable.ic_choose_apps) {
                    activity?.startActivityForResult(
                        Intent(
                            requireActivity(), AppManagerActivity::class.java
                        ), REQUEST_VPN_APP_SELECT
                    )
                },
                OrbotMenuAction(R.string.menu_settings, R.drawable.ic_settings_gear) {

                    activity?.startActivityForResult(
                        Intent(context, SettingsActivity::class.java), REQUEST_CODE_SETTINGS
                    )
                },
                OrbotMenuAction(R.string.menu_log, R.drawable.ic_log) { showLog() },
                OrbotMenuAction(R.string.menu_about, R.drawable.ic_about) {
                    AboutDialogFragment().show(
                        requireActivity().supportFragmentManager, AboutDialogFragment.TAG
                    )
                },
                OrbotMenuAction(R.string.menu_exit, R.drawable.ic_exit) { doExit() })
        lvMore.adapter = MoreActionAdapter(requireActivity(), listItems)

        return view
    }

    private fun getTorVersion(): String {
        return OrbotService.BINARY_TOR_VERSION.split("-").toTypedArray()[0]
    }


    private fun doExit() {
        val killIntent = Intent(
            requireActivity(), OrbotService::class.java
        ).setAction(OrbotConstants.ACTION_STOP)
            .putExtra(OrbotConstants.ACTION_STOP_FOREGROUND_TASK, true)
        requireContext().sendIntentToService(OrbotConstants.ACTION_STOP_VPN)
        requireContext().sendIntentToService(killIntent)
        requireActivity().finish()
    }

    private fun showLog() {
        (activity as OrbotActivity).showLog()
    }

}