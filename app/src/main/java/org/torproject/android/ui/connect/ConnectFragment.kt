package org.torproject.android.ui.connect

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.freehaven.tor.control.TorControlCommands
import org.torproject.android.OrbotActivity
import org.torproject.android.R
import org.torproject.android.util.sendIntentToService
import org.torproject.android.databinding.FragmentConnectBinding
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.service.vpn.VpnServicePrepareWrapper
import org.torproject.android.util.Prefs
import org.torproject.android.ui.OrbotMenuAction
import org.torproject.jni.TorService

private const val DEFAULT_THROTTLE_INTERVAL = 4000L

class ConnectFragment : Fragment(),
    ExitNodeBottomSheet.ExitNodeSelectedCallback {

    private lateinit var binding: FragmentConnectBinding

    val viewModel: ConnectViewModel by activityViewModels()

    private val lastStatus: String
        get() = (activity as? OrbotActivity)?.previousReceivedTorStatus ?: ""

    private val startTorVpnResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // The user pressed OK, we can start Tor VPN
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                startTorVpn()
            } else { /* this happens when:
                - the user cancels the system VPN dialog
                - the user is on Android S+ and has another VpnService based app
                  set to be always-on.

                  we are unable to differentiate these two things, so show a generic error msg
                 */
                ignoreStartSwitchListener = true
                binding.switchConnect.isChecked = false
                displayVpnStartError(getString(R.string.unable_to_start_unknown_reason_error_msg))
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state == ConnectUiState.NoInternet)
                        binding.switchConnect.visibility = View.GONE
                    else binding.switchConnect.visibility = View.VISIBLE
                    when (state) {
                        is ConnectUiState.NoInternet -> doLayoutNoInternet()
                        is ConnectUiState.Off -> doLayoutOff()
                        is ConnectUiState.Starting -> {
                            binding.switchConnect.isChecked = true
                            doLayoutStarting(requireContext())
                            state.bootstrapPercent?.let {
                                binding.progressBar.progress = it
                            }
                        }

                        is ConnectUiState.On -> {
                            binding.switchConnect.isChecked = true
                            lastState = TorService.ACTION_START
                            doLayoutOn(requireContext())
                        }

                        is ConnectUiState.Stopping -> {}
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logState.collect { logline ->
                    LocalizedLogsToDisplay.updateLabelIfDisplayed(
                        logline,
                        binding.tvSubtitle,
                        context
                    )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is ConnectEvent.StartTorAndVpn -> attemptToStartTor()
                    is ConnectEvent.RefreshMenuList -> refreshMenuList(requireContext())
                }
            }
        }
        binding.switchConnect.setOnClickListener {
            it.isEnabled = false
            it.alpha = 0.38f
            binding.switchConnect.text = context?.getString(R.string.loading)
            it.postDelayed({
                it.isEnabled = true
                it.alpha = 1f
                binding.switchConnect.text = context?.getString(R.string.connect)
            }, DEFAULT_THROTTLE_INTERVAL)
        }
        binding.switchConnect.setOnCheckedChangeListener { _, value ->
            if (ignoreStartSwitchListener) {
                ignoreStartSwitchListener = false
                return@setOnCheckedChangeListener
            }
            if (value) {
                // display msg if optional outbound proxy config is invalid
                if (Prefs.outboundProxy.second != null) {
                    Toast.makeText(
                        activity,
                        getString(R.string.invalid_outbound_proxy_config),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            attemptToStartTor()
        }
        refreshMenuList(requireContext())
    }

    private var ignoreStartSwitchListener = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentConnectBinding.inflate(inflater, container, false)
        viewModel.updateState(requireContext(), lastStatus)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateState(requireContext(), lastStatus)
    }

    fun stopTorAndVpn() {
        doLayoutOff()
        setState(TorService.ACTION_STOP)
        binding.tvSubtitle.text = ""
    }

    private fun stopAnimations() {
        binding.ivStatus.clearAnimation()
        binding.ivShadow.clearAnimation()
    }

    private fun sendNewnymSignal() {
        requireContext().sendIntentToService(TorControlCommands.SIGNAL_NEWNYM)
        binding.ivStatus.animate().alpha(0f).duration = 500

        lifecycleScope.launch(Dispatchers.Main) {
            delay(600)
            binding.ivStatus.animate().alpha(1f).duration = 500
        }
    }

    fun attemptToStartTorPowerUserMode() {
        // android 14 awkwardly needs this permission to be explicitly granted to use the
        // FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED permission without grabbing a VPN Intent
        val alarmManager =
            requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            PowerUserForegroundPermDialog().createTransactionAndShow(requireActivity())
            return // user can try again after granting permission
        }
        doLayoutStarting(requireContext())
        setState(TorService.ACTION_START)
    }

    fun startTorVpn() {
        doLayoutStarting(requireContext())
        setState(TorService.ACTION_START)
    }

    fun attemptToStartTor() {
        Prefs.putUseVpn(!Prefs.isPowerUserMode)
        if (Prefs.isPowerUserMode) {
            attemptToStartTorPowerUserMode()
        } else {
            val vpnPrepareState =
                VpnServicePrepareWrapper.orbotVpnServicePreparedState(requireContext())
            when (vpnPrepareState) {

                is VpnServicePrepareWrapper.Result.Prepared ->
                    startTorVpn()

                is VpnServicePrepareWrapper.Result.CantPrepare ->
                    displayVpnStartError(vpnPrepareState.errorMsg)

                is VpnServicePrepareWrapper.Result.ShouldAttempt ->
                    // prompt VPN permission dialog
                    startTorVpnResultLauncher.launch(vpnPrepareState.prepareIntent)

            }
        }
        refreshMenuList(requireContext())
    }

    fun displayVpnStartError(msg: String) {
        VpnAlwaysOnDialogFragment.newInstance(msg).show(
            requireActivity().supportFragmentManager,
            VpnAlwaysOnDialogFragment.TAG
        )
    }

    var lastState: String? = null

    @Synchronized
    fun setState(newState: String) {
        if (lastState != newState) {
            requireContext().sendIntentToService(newState)
            lastState = newState
        }
    }

    fun refreshMenuList(context: Context) {

        val connectStr =
            if (Prefs.smartConnect) R.string.smart_connect else when (Prefs.transport) {
                Transport.NONE -> R.string.direct_connect
                Transport.MEEK -> R.string.bridge_meek_azure
                Transport.OBFS4 -> R.string.built_in_bridges_obfs4
                Transport.SNOWFLAKE -> R.string.snowflake
                Transport.SNOWFLAKE_AMP -> R.string.snowflake_amp
                Transport.SNOWFLAKE_SQS -> R.string.snowflake_sqs
                Transport.WEBTUNNEL -> TODO()
                Transport.DNSTT -> R.string.bridge_dnstt
                Transport.CUSTOM -> R.string.custom_bridges
            }

        val connectStrLabel =
            getString(R.string.set_transport) + ": ${context.getString(connectStr)}"

        val listItems =
            arrayListOf(
                OrbotMenuAction(
                    R.string.btn_configure,
                    R.drawable.ic_settings_gear,
                    statusString = connectStrLabel
                ) { openConfigureTorConnection() },
                OrbotMenuAction(R.string.btn_change_exit, 0) {
                    ExitNodeBottomSheet().show(
                        requireActivity().supportFragmentManager,
                        "ExitNodeBottomSheet"
                    )
                },
                OrbotMenuAction(R.string.btn_refresh, R.drawable.ic_refresh) { sendNewnymSignal() })
        if (!Prefs.isPowerUserMode) listItems.add(
            0,
            OrbotMenuAction(R.string.btn_choose_apps, R.drawable.ic_choose_apps) {
                findNavController().navigate(R.id.connectToApps)
            })
        binding.lvConnected.adapter = ConnectMenuActionAdapter(context, listItems)
    }


    private fun doLayoutNoInternet() {

        binding.ivStatus.setImageResource(R.drawable.orbiesleeping)
        binding.ivStatus.setOnClickListener { }
        stopAnimations()

        binding.tvSubtitle.visibility = View.VISIBLE

        binding.progressBar.visibility = View.INVISIBLE
        binding.tvTitle.text = getString(R.string.no_internet_title)
        binding.tvSubtitle.text = getString(R.string.no_internet_subtitle)

        binding.lvConnected.visibility = View.VISIBLE
    }

    fun doLayoutOn(context: Context) {
        if (Prefs.smartConnect) {
            Prefs.smartConnect = false
            refreshMenuList(context)
        }
        binding.ivStatus.setImageResource(R.drawable.orbieon)
        binding.tvSubtitle.visibility = View.VISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvTitle.text = context.getString(R.string.connected_title)
        binding.lvConnected.visibility = View.VISIBLE

        refreshMenuList(context)

        binding.ivStatus.setOnClickListener {
            (activity as OrbotActivity).showLog()
        }
    }

    fun doLayoutOff() {
        binding.ivStatus.setImageResource(R.drawable.orbiesleeping)
        refreshMenuList(requireContext())
        stopAnimations()
        binding.tvSubtitle.visibility = View.VISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.lvConnected.visibility = View.VISIBLE
        binding.tvTitle.text = getString(R.string.secure_your_connection_title)
        binding.tvSubtitle.text = getString(R.string.secure_your_connection_subtitle)
        binding.ivStatus.setOnClickListener(null)
    }

    fun doLayoutStarting(context: Context) {
        binding.tvSubtitle.visibility = View.VISIBLE
        binding.tvSubtitle.text = ""

        with(binding.progressBar) {
            progress = 0
            visibility = View.VISIBLE
        }

        binding.ivStatus.setImageResource(R.drawable.orbie_stuck)
        val animHover = AnimationUtils.loadAnimation(context, R.anim.hover)
        animHover.repeatMode = Animation.REVERSE
        binding.ivStatus.animation = animHover
        animHover.start()

        val animShadow = AnimationUtils.loadAnimation(context, R.anim.shadow)
        animShadow.repeatMode = Animation.REVERSE
        binding.ivShadow.animation = animShadow
        animShadow.start()
        binding.tvTitle.text = context.getString(R.string.trying_to_connect_title)
        binding.tvSubtitle.setOnClickListener {
            (activity as OrbotActivity).showLog()
        }
    }


    private fun openConfigureTorConnection() {
        ConfigConnectionBottomSheet()
            .show(requireActivity().supportFragmentManager, ConfigConnectionBottomSheet.TAG)
    }

    override fun onExitNodeSelected(countryCode: String) {

        //tor format expects "{" for country code
        Prefs.exitNodes = "{$countryCode}"

        requireContext().sendIntentToService(
            Intent(requireActivity(), OrbotService::class.java)
                .setAction(OrbotConstants.CMD_SET_EXIT).putExtra("exit", countryCode)
        )

        refreshMenuList(requireContext())
    }
}
