package org.torproject.android.ui.connect

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.freehaven.tor.control.TorControlCommands
import org.torproject.android.OrbotActivity
import org.torproject.android.R
import org.torproject.android.service.util.putNotSystem
import org.torproject.android.service.util.sendIntentToService
import org.torproject.android.databinding.FragmentConnectBinding
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.AppManagerActivity
import org.torproject.android.ui.OrbotMenuAction

class ConnectFragment : Fragment(),
    ExitNodeBottomSheet.ExitNodeSelectedCallback {

    private lateinit var binding: FragmentConnectBinding

    private val viewModel: ConnectViewModel by activityViewModels()

    private val lastStatus: String
        get() = (activity as? OrbotActivity)?.previousReceivedTorStatus ?: ""

    private val startTorResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                startTorAndVpn()
            }
        }

    private val restartTorResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                requireContext().sendIntentToService(OrbotConstants.ACTION_RESTART_VPN) // is this enough todo?
                refreshMenuList(requireContext())
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ConnectUiState.NoInternet -> doLayoutNoInternet()
                        is ConnectUiState.Off -> doLayoutOff()
                        is ConnectUiState.Starting -> {
                            doLayoutStarting(requireContext())
                            state.bootstrapPercent?.let {
                                binding.progressBar.progress = it
                            }
                        }

                        is ConnectUiState.On -> doLayoutOn(requireContext())
                        is ConnectUiState.Stopping -> {}
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is ConnectEvent.StartTorAndVpn -> startTorAndVpn()
                    is ConnectEvent.RefreshMenuList -> refreshMenuList(requireContext())
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentConnectBinding.inflate(inflater, container, false)

        if (Prefs.isPowerUserMode) {
            binding.btnStart.text = getString(R.string.connect)
        }

        viewModel.updateState(requireContext(), lastStatus)

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateState(requireContext(), lastStatus)
    }

    private fun stopTorAndVpn() {
        requireContext().sendIntentToService(OrbotConstants.ACTION_STOP)
        doLayoutOff()
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

    fun startTorAndVpn() {
        val vpnIntent = VpnService.prepare(requireActivity())?.putNotSystem()
        if (vpnIntent != null && !Prefs.isPowerUserMode) {
            // prompt VPN permission dialog
            startTorResultLauncher.launch(vpnIntent)
        } else { // either the vpn permission hasn't been granted or we are in power user mode
            Prefs.putUseVpn(!Prefs.isPowerUserMode)
            if (Prefs.isPowerUserMode) {
                // android 14 awkwardly needs this permission to be explicitly granted to use the
                // FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED permission without grabbing a VPN Intent
                val alarmManager =
                    requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    PowerUserForegroundPermDialog().createTransactionAndShow(requireActivity())
                    return // user can try again after granting permission
                } else {
                    binding.ivStatus.setImageResource(R.drawable.torstarting)
                    with(binding.btnStart) {
                        text = context.getString(android.R.string.cancel)
                    }
                    requireContext().sendIntentToService(OrbotConstants.ACTION_START)
                }
            }
            binding.ivStatus.setImageResource(R.drawable.torstarting)
            with(binding.btnStart) {
                text = context.getString(android.R.string.cancel)
            }
            requireContext().sendIntentToService(OrbotConstants.ACTION_START)
        }
    }

    fun refreshMenuList(context: Context) {
        val listItems =
            arrayListOf(
                OrbotMenuAction(R.string.btn_change_exit, 0) {
                    ExitNodeBottomSheet().show(
                        requireActivity().supportFragmentManager,
                        "ExitNodeBottomSheet"
                    )
                },
                OrbotMenuAction(R.string.btn_refresh, R.drawable.ic_refresh) { sendNewnymSignal() },
                OrbotMenuAction(R.string.btn_tor_off, R.drawable.ic_power) { stopTorAndVpn() })
        if (!Prefs.isPowerUserMode) listItems.add(
            0,
            OrbotMenuAction(R.string.btn_choose_apps, R.drawable.ic_choose_apps) {
                restartTorResultLauncher.launch(
                    Intent(
                        requireActivity(),
                        AppManagerActivity::class.java
                    )
                )
            })
        binding.lvConnected.adapter = ConnectMenuActionAdapter(context, listItems)
    }


    private fun doLayoutNoInternet() {

        binding.ivStatus.setImageResource(R.drawable.nointernet)

        stopAnimations()

        binding.tvSubtitle.visibility = View.VISIBLE

        binding.progressBar.visibility = View.INVISIBLE
        binding.tvTitle.text = getString(R.string.no_internet_title)
        binding.tvSubtitle.text = getString(R.string.no_internet_subtitle)

        binding.btnStart.visibility = View.GONE
        binding.lvConnected.visibility = View.GONE
        binding.swSmartConnect.visibility = View.GONE
        binding.tvConfigure.visibility = View.GONE
    }

    fun doLayoutOn(context: Context) {
        binding.ivStatus.setImageResource(R.drawable.toron)

        binding.tvSubtitle.visibility = View.GONE
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvTitle.text = context.getString(R.string.connected_title)
        binding.btnStart.visibility = View.GONE
        binding.lvConnected.visibility = View.VISIBLE
        binding.swSmartConnect.visibility = View.GONE
        binding.tvConfigure.visibility = View.GONE

        refreshMenuList(context)

        binding.ivStatus.setOnClickListener {}
    }

    fun doLayoutOff() {
        binding.ivStatus.setImageResource(R.drawable.toroff)
        stopAnimations()
        binding.tvSubtitle.visibility = View.VISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.lvConnected.visibility = View.GONE
        binding.tvTitle.text = getString(R.string.secure_your_connection_title)
        binding.tvSubtitle.text = getString(R.string.secure_your_connection_subtitle)

        binding.swSmartConnect.visibility = View.VISIBLE
        binding.swSmartConnect.isChecked = Prefs.smartConnect
        binding.swSmartConnect.setOnCheckedChangeListener { _, value ->
            Prefs.smartConnect = value
            doLayoutOff()
        }

        binding.tvConfigure.visibility = View.VISIBLE
        binding.tvConfigure.text = getString(R.string.btn_configure)
        binding.tvConfigure.paintFlags = Paint.UNDERLINE_TEXT_FLAG
        binding.tvConfigure.setOnClickListener { openConfigureTorConnection() }

        with(binding.btnStart) {
            visibility = View.VISIBLE

            val connectStr: String

            if (Prefs.smartConnect) {
                connectStr = getString(R.string.action_use_, getString(R.string.smart_connect))
            } else {
                connectStr = when (Prefs.transport) {
                    Transport.NONE -> getString(
                        R.string.action_use_,
                        getString(R.string.direct_connect)
                    )

                    Transport.MEEK_AZURE -> getString(
                        R.string.action_use_,
                        getString(R.string.bridge_meek_azure)
                    )

                    Transport.OBFS4 -> getString(
                        R.string.action_use_,
                        getString(R.string.built_in_bridges_obfs4)
                    )

                    Transport.SNOWFLAKE -> getString(
                        R.string.action_use_,
                        getString(R.string.snowflake)
                    )

                    Transport.SNOWFLAKE_AMP -> getString(
                        R.string.action_use_,
                        getString(R.string.snowflake_amp)
                    )

                    Transport.SNOWFLAKE_SQS -> getString(
                        R.string.action_use_,
                        getString(R.string.snowflake_sqs)
                    )

                    Transport.WEBTUNNEL -> getString(R.string.action_use_, Transport.WEBTUNNEL.id)
                    Transport.CUSTOM -> getString(
                        R.string.action_use_,
                        getString(R.string.custom_bridges)
                    )
                }
            }

            text = when {
                Prefs.isPowerUserMode -> getString(R.string.connect)
                connectStr.isEmpty() -> SpannableStringBuilder()
                    .append(
                        getString(R.string.btn_start_vpn),
                        AbsoluteSizeSpan(18, true),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                else -> SpannableStringBuilder()
                    .append(
                        getString(R.string.btn_start_vpn),
                        AbsoluteSizeSpan(18, true),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    .append("\n")
                    .append(
                        connectStr,
                        AbsoluteSizeSpan(12, true),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
            }

            isEnabled = true
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.orbot_btn_enabled_purple)
            )
            setOnClickListener { startTorAndVpn() }
        }

        binding.ivStatus.setOnClickListener {
            startTorAndVpn()
        }
    }

    fun doLayoutStarting(context: Context) {
        binding.tvSubtitle.visibility = View.GONE
        with(binding.progressBar) {
            progress = 0
            visibility = View.VISIBLE
        }

        binding.ivStatus.setImageResource(R.drawable.torstarting)
        val animHover = AnimationUtils.loadAnimation(context, R.anim.hover)
        animHover.repeatCount = 7
        animHover.repeatMode = Animation.REVERSE
        binding.ivStatus.animation = animHover
        animHover.start()

        val animShadow = AnimationUtils.loadAnimation(context, R.anim.shadow)
        animShadow.repeatCount = 7
        animShadow.repeatMode = Animation.REVERSE
        binding.ivShadow.animation = animShadow
        animShadow.start()

        binding.tvTitle.text = context.getString(R.string.trying_to_connect_title)
        with(binding.btnStart) {
            text = context.getString(android.R.string.cancel)
            isEnabled = true
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context, R.color.orbot_btn_enabled_purple
                )
            )
            setOnClickListener {
                stopTorAndVpn()
            }
        }

        binding.swSmartConnect.visibility = View.GONE
        binding.tvConfigure.visibility = View.GONE
    }


    private fun openConfigureTorConnection() {
        ConfigConnectionBottomSheet()
            .show(requireActivity().supportFragmentManager, ConfigConnectionBottomSheet.TAG)
    }

    override fun onExitNodeSelected(countryCode: String, displayCountryName: String) {

        //tor format expects "{" for country code
        Prefs.exitNodes = "{$countryCode}"

        requireContext().sendIntentToService(
            Intent(requireActivity(), OrbotService::class.java)
                .setAction(OrbotConstants.CMD_SET_EXIT).putExtra("exit", countryCode)
        )

        refreshMenuList(requireContext())
    }
}