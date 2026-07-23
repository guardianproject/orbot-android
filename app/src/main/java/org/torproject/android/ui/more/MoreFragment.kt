package org.torproject.android.ui.more

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import org.torproject.android.OrbotActivity
import org.torproject.android.R
import org.torproject.android.databinding.FragmentMoreBinding
import org.torproject.android.util.sendIntentToService
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.vpn.VpnServicePrepareWrapper
import org.torproject.android.ui.OrbotMenuAction
import org.torproject.android.ui.v3onionservice.OnionServiceActivity
import org.torproject.android.ui.v3onionservice.clientauth.ClientAuthActivity
import org.torproject.android.widget.StatusSection
import org.torproject.jni.TorService

class MoreFragment : Fragment() {
    private var httpPort = -1
    private var socksPort = -1

    override fun onAttach(context: Context) {
        super.onAttach(context)

        httpPort = (context as OrbotActivity).portHttp
        socksPort = context.portSocks

        if (view != null) updateStatus()
    }

    private fun updateStatus() {
        val pm = requireActivity().packageManager
        val info = pm.getPackageInfo(requireActivity().packageName, 0)

        val normalizedVersion = info.versionName
            ?.substringBefore("tor")
            ?.dropLast(1)
            .orEmpty()

        binding.tvPortAndVersionInfo.setContent {
            StatusSection(
                httpPort = httpPort,
                socksPort = socksPort,
                orbotVersion = normalizedVersion,
                torVersion = getTorVersion()
            )
        }
    }

    private lateinit var binding: FragmentMoreBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMoreBinding.inflate(layoutInflater)
        (context as AppCompatActivity).setSupportActionBar(binding.toolbar)
        binding.toolbar.title = requireContext().getString(R.string.app_name)
        binding.tvExit.setOnClickListener { doExit() }

        updateStatus()

        val listItems = listOf(
            OrbotMenuAction(R.string.btn_choose_apps, R.drawable.ic_choose_apps) {
                findNavController().navigate(R.id.more_to_apps)
            },
            OrbotMenuAction(R.string.title_safety, R.drawable.lock_24px) {
                findNavController().navigate(R.id.more_to_safety)

            },
            OrbotMenuAction(R.string.menu_settings, R.drawable.ic_settings_gear) {
                findNavController().navigate(R.id.more_to_settings)
            },
            OrbotMenuAction(R.string.system_vpn_settings, R.drawable.ic_vpn_key) {
                VpnServicePrepareWrapper.openVpnSystemSettings(this)
            },
            OrbotMenuAction(R.string.menu_log, R.drawable.ic_log) {
                LogBottomSheet.show(
                    parentFragmentManager
                )
            },
            OrbotMenuAction(R.string.v3_hosted_services, R.drawable.ic_menu_onion) {
                startActivity(Intent(requireActivity(), OnionServiceActivity::class.java))
            },
            OrbotMenuAction(R.string.v3_client_auth_activity_title, R.drawable.ic_shield) {
                startActivity(Intent(requireActivity(), ClientAuthActivity::class.java))
            },
            OrbotMenuAction(R.string.menu_about, R.drawable.ic_about) {
                AboutDialogFragment().show(
                    requireActivity().supportFragmentManager,
                    AboutDialogFragment.TAG
                )
            }
        )

        binding.rvMoreActions.adapter = MoreActionAdapter(listItems)
        val spanCount = if (resources.configuration.screenWidthDp < 600) 2 else 4
        binding.rvMoreActions.layoutManager = GridLayoutManager(requireContext(), spanCount)

        return binding.root
    }

    private fun getTorVersion(): String =
        TorService.VERSION_NAME.split("-").toTypedArray()[0]


    private fun doExit() {
        val killIntent = Intent(
            requireActivity(), OrbotService::class.java
        ).setAction(TorService.ACTION_STOP)
            .putExtra(OrbotConstants.ACTION_STOP_FOREGROUND_TASK, true)
        requireContext().sendIntentToService(killIntent)
        requireActivity().finish()
    }

}