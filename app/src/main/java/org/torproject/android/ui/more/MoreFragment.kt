package org.torproject.android.ui.more

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.torproject.android.OrbotActivity
import org.torproject.android.R
import org.torproject.android.util.sendIntentToService
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.ui.OrbotMenuAction
import org.torproject.android.ui.v3onionservice.OnionServiceActivity
import org.torproject.android.ui.v3onionservice.clientauth.ClientAuthActivity

class MoreFragment : Fragment() {
    private var httpPort = -1
    private var socksPort = -1

    private lateinit var tvStatus: TextView

    override fun onAttach(context: Context) {
        super.onAttach(context)

        httpPort = (context as OrbotActivity).portHttp
        socksPort = context.portSocks

        if (view != null) updateStatus()
    }

    private fun updateStatus() {
        val labels = listOf("HTTP proxy", "SOCKS proxy", "Orbot", "Tor")
        val labelWidth = labels.maxOf { it.length } + 6

        fun row(label: String, value: String): String {
            return label.padEnd(labelWidth) + value
        }

        val rows = mutableListOf<String>()

        rows += if (httpPort != -1 && socksPort != -1) {
            listOf(
                row("HTTP proxy", httpPort.toString()),
                row("SOCKS proxy", socksPort.toString())
            )
        } else {
            listOf(
                row("HTTP proxy", getString(R.string.ports_not_set)),
                row("SOCKS proxy", getString(R.string.ports_not_set))
            )
        }

        val pm = requireActivity().packageManager
        val info = pm.getPackageInfo(requireActivity().packageName, 0)
        val normalizedVersion = info.versionName?.substringBefore("tor")?.dropLast(1)

        rows += row("Orbot", "v$normalizedVersion")
        rows += row("Tor", "v${getTorVersion()}")

        tvStatus.text = rows.joinToString("\n")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_more, container, false)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (context as AppCompatActivity).setSupportActionBar(toolbar)
        toolbar?.title = requireContext().getString(R.string.app_name)
        view.findViewById<TextView>(R.id.tvExit)?.setOnClickListener { doExit() }

        tvStatus = view.findViewById(R.id.tvVersion)

        updateStatus()

        val rvMore = view.findViewById<RecyclerView>(R.id.rvMoreActions)
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
                activity?.startActivity(
                    Intent("android.net.vpn.SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            OrbotMenuAction(R.string.menu_log, R.drawable.ic_log) { showLog() },
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
            },
        )
        rvMore.adapter = MoreActionAdapter(listItems)

        val spanCount = if (resources.configuration.screenWidthDp < 600) 2 else 4
        rvMore.layoutManager = GridLayoutManager(requireContext(), spanCount)

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
        requireContext().sendIntentToService(killIntent)
        requireActivity().finish()
    }

    private fun showLog() {
        (activity as OrbotActivity).showLog()
    }
}