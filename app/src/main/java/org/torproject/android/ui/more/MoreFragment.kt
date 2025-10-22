package org.torproject.android.ui.more

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.torproject.android.OrbotActivity
import org.torproject.android.R
import org.torproject.android.service.util.sendIntentToService
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.ui.AppManagerActivity
import org.torproject.android.ui.OrbotMenuAction
import org.torproject.android.ui.v3onionservice.OnionServiceActivity
import org.torproject.android.ui.v3onionservice.clientauth.ClientAuthActivity

class MoreFragment : Fragment() {
    private val viewModel: MoreViewModel by activityViewModels()

    private var httpPort = -1
    private var socksPort = -1

    private lateinit var tvStatus: TextView

    override fun onAttach(context: Context) {
        super.onAttach(context)

        httpPort = (context as OrbotActivity).portHttp
        socksPort = context.portSocks
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_more, container, false)
        tvStatus = view.findViewById(R.id.tvVersion)

        val rvMore = view.findViewById<RecyclerView>(R.id.rvMoreActions)

        lifecycleScope.launch {
            viewModel.statusText.collect { text ->
                tvStatus.text = text
            }
        }

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    MoreEvent.OpenSettings -> {
                        activity?.startActivityForResult(
                            Intent(context, SettingsActivity::class.java),
                            OrbotActivity.REQUEST_CODE_SETTINGS
                        )
                    }
                    MoreEvent.OpenVpnSettings -> {
                        startActivity(Intent("android.net.vpn.SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    MoreEvent.ChooseApps -> {
                        startActivity(Intent(requireActivity(), AppManagerActivity::class.java))
                    }
                    MoreEvent.OpenLog -> showLog()
                    MoreEvent.OpenOnionServices -> {
                        startActivity(Intent(requireActivity(), OnionServiceActivity::class.java))
                    }
                    MoreEvent.OpenClientAuth -> {
                        startActivity(Intent(requireActivity(), ClientAuthActivity::class.java))
                    }
                    MoreEvent.OpenAbout -> {
                        AboutDialogFragment().show(
                            requireActivity().supportFragmentManager,
                            AboutDialogFragment.TAG
                        )
                    }
                    MoreEvent.ExitApp -> doExit()
                }
            }
        }

        viewModel.updateStatus(requireContext(), httpPort, socksPort)

        val listItems = listOf(
            OrbotMenuAction(R.string.menu_settings, R.drawable.ic_settings_gear) {
                viewModel.triggerEvent(MoreEvent.OpenSettings)
            },
            OrbotMenuAction(R.string.system_vpn_settings, R.drawable.ic_vpn_key) {
                viewModel.triggerEvent(MoreEvent.OpenVpnSettings)
            },
            OrbotMenuAction(R.string.btn_choose_apps, R.drawable.ic_choose_apps) {
                viewModel.triggerEvent(MoreEvent.ChooseApps)
            },
            OrbotMenuAction(R.string.menu_log, R.drawable.ic_log) {
                viewModel.triggerEvent(MoreEvent.OpenLog)
            },
            OrbotMenuAction(R.string.v3_hosted_services, R.drawable.ic_menu_onion) {
                viewModel.triggerEvent(MoreEvent.OpenOnionServices)
            },
            OrbotMenuAction(R.string.v3_client_auth_activity_title, R.drawable.ic_shield) {
                viewModel.triggerEvent(MoreEvent.OpenClientAuth)
            },
            OrbotMenuAction(R.string.menu_about, R.drawable.ic_about) {
                viewModel.triggerEvent(MoreEvent.OpenAbout)
            },
            OrbotMenuAction(R.string.menu_exit, R.drawable.ic_exit) {
                viewModel.triggerEvent(MoreEvent.ExitApp)
            }
        )

        rvMore.adapter = MoreActionAdapter(listItems)
        val spanCount = if (resources.configuration.screenWidthDp < 600) 2 else 4
        rvMore.layoutManager = GridLayoutManager(requireContext(), spanCount)

        return view
    }

    private fun doExit() {
        val killIntent = Intent(requireActivity(), OrbotService::class.java)
            .setAction(OrbotConstants.ACTION_STOP)
            .putExtra(OrbotConstants.ACTION_STOP_FOREGROUND_TASK, true)
        requireContext().sendIntentToService(killIntent)
        requireActivity().finish()
    }

    private fun showLog() {
        (activity as OrbotActivity).showLog()
    }
}