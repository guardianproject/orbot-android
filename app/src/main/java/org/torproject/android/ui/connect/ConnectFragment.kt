package org.torproject.android.ui.connect

import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.CompoundButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
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
import org.torproject.android.util.putNotSystem
import org.torproject.android.util.sendIntentToService
import org.torproject.android.databinding.FragmentConnectBinding
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.util.Prefs
import org.torproject.android.ui.OrbotMenuAction

private var lastChange = 0L
private const val DEFAULT_THROTTLE_INTERVAL = 5000L

/**
 * Prevents rapid consecutive checked-change callbacks on this CompoundButton.
 * Only the first change in each [intervalMs] window will be delivered.
 */
fun CompoundButton.setOnThrottledCheckedChangeListener(
    intervalMs: Long = DEFAULT_THROTTLE_INTERVAL,
    onCheckedChange: (button: CompoundButton, isChecked: Boolean) -> Unit
) {
    setOnCheckedChangeListener { buttonView, isChecked ->
        val now = System.currentTimeMillis()
        if (now - lastChange >= intervalMs) {
            lastChange = now
            onCheckedChange(buttonView, isChecked)
        } else {
            buttonView.isChecked = !isChecked
            val timeRemain = intervalMs - (now - lastChange)

            val progressBar = ProgressBar(buttonView.context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = intervalMs.toInt()
                progress = (now - lastChange).toInt()
            }

            val dialog = AlertDialog.Builder(buttonView.context)
                .setMessage(buttonView.context.getString(R.string.toast_throttle_wait, timeRemain/1000))
                .setView(progressBar)
                .setCancelable(false)
                .create()
                .apply { show() }

            val handler = Handler(Looper.getMainLooper())
            val updateRunnable = object : Runnable {
                override fun run() {
                    val currentTime = System.currentTimeMillis()
                    val elapsed = (currentTime - lastChange).toInt()
                    progressBar.progress = elapsed

                    if (elapsed < intervalMs) {
                        handler.postDelayed(this, 16) // ~60fps update
                    } else {
                        dialog.dismiss()
                    }
                }
            }

            handler.post(updateRunnable)
        }
    }
}

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
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
                            lastState = OrbotConstants.ACTION_START
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
                    if (logline.isNotEmpty()) {

                        if (logline.contains("Connected to tor control port"))
                        {
                            binding.tvSubtitle.text =
                                getString(R.string.status_connected_control_port)
                        }
                        else if (logline.contains("(handshake)"))
                        {
                            binding.tvSubtitle.text = getString(R.string.status_handshake)
                        }
                        else if (logline.contains("(handshake_done)"))
                        {
                            binding.tvSubtitle.text = getString(R.string.status_handshake_done)
                        }
                        else if (logline.contains("(circuit_create)"))
                        {
                            binding.tvSubtitle.text = getString(R.string.status_circuit_create)
                        }
                        else if (logline.contains("(done)"))
                        {
                            binding.tvSubtitle.text = getString(R.string.status_done)
                        }
                        else if (logline.contains("(conn_pt)"))
                        {
                            binding.tvSubtitle.text = getString(R.string.status_conn_pt)
                        }
                        else if (logline.contains("(conn_done_pt)"))
                        {
                            binding.tvSubtitle.text = getString(R.string.status_conn_done_pt)
                        }
                        else if (logline.contains("(conn_done)"))
                        {
                            binding.tvSubtitle.text = getString(R.string.status_conn_done)
                        }
                        else if (logline.contains("(onehop_create)"))
                        {
                            binding.tvSubtitle.text = getString(R.string.status_onehop_create)
                        }
                        else if (logline.contains("(enough_dirinfo)"))
                        {
                            binding.tvSubtitle.text = getString(R.string.status_enough_dirinfo)
                        }
                        else if (logline.contains("(requesting_status)")) {
                            binding.tvSubtitle.text = getString(R.string.status_requesting_status)
                        }
                        else if (logline.contains("(loading_status)")) {
                            binding.tvSubtitle.text = getString(R.string.status_loading_status)
                        }
                        else if (logline.contains("(loading_keys)")) {
                            binding.tvSubtitle.text = getString(R.string.status_loading_keys)
                        }
                        else if (logline.contains("(requesting_descriptors)")) {
                            binding.tvSubtitle.text =
                                getString(R.string.status_requesting_descriptors)
                        }
                        else if (logline.contains("(loading_descriptors)")) {
                            binding.tvSubtitle.text = getString(R.string.status_loading_descriptors)
                        }



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

        binding.switchConnect.setOnThrottledCheckedChangeListener{ _, value ->

            if (value == true)
            {
                startTorAndVpn()
            }
            else
            {
                stopTorAndVpn()
            }

        }
        refreshMenuList(requireContext())

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
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

    fun stopTorAndVpn() {
        doLayoutOff()
        setState(OrbotConstants.ACTION_STOP)
        binding.tvSubtitle.text=""
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
                    binding.ivStatus.setImageResource(R.drawable.orbieon)
                    with(binding.btnStart) {
                        text = context.getString(android.R.string.cancel)
                    }
                }
            }
            doLayoutStarting(requireContext())
            setState(OrbotConstants.ACTION_START)
        }
        refreshMenuList(requireContext())
    }

    private var lastState :String? = null

    @Synchronized
    fun setState (newState: String) {

        if (lastState != newState)
        {
            requireContext().sendIntentToService(newState)
            lastState = newState
        }
    }

    fun refreshMenuList(context: Context) {

        val connectStr: String

        if (Prefs.smartConnect) {
            connectStr = getString(R.string.smart_connect)
        } else {
            connectStr = when (Prefs.transport) {
                Transport.NONE ->
                    getString(R.string.direct_connect)

                Transport.MEEK_AZURE ->
                    getString(R.string.bridge_meek_azure)

                Transport.OBFS4 ->
                    getString(R.string.built_in_bridges_obfs4)

                Transport.SNOWFLAKE ->
                    getString(R.string.snowflake)

                Transport.SNOWFLAKE_AMP ->
                    getString(R.string.snowflake_amp)

                Transport.SNOWFLAKE_SQS ->
                    getString(R.string.snowflake_sqs)

                Transport.WEBTUNNEL ->  Transport.WEBTUNNEL.id
                Transport.CUSTOM ->
                    getString(R.string.custom_bridges)
            }
        }

        var connectStrLabel = getString(R.string.set_transport) + ": $connectStr"

        val listItems =
            arrayListOf(
                OrbotMenuAction(R.string.btn_configure, R.drawable.ic_settings_gear, statusString = connectStrLabel) { openConfigureTorConnection() },
                OrbotMenuAction(R.string.btn_change_exit, 0) {
                    ExitNodeBottomSheet().show(
                        requireActivity().supportFragmentManager,
                        "ExitNodeBottomSheet"
                    )
                },
        OrbotMenuAction(R.string.btn_refresh, R.drawable.ic_refresh) { sendNewnymSignal() })
             //   OrbotMenuAction(R.string.btn_tor_off, R.drawable.ic_power) { stopTorAndVpn() })
        if (!Prefs.isPowerUserMode) listItems.add(
            0,
            OrbotMenuAction(R.string.btn_choose_apps, R.drawable.ic_choose_apps) {
                findNavController().navigate(R.id.connectToApps)
            })
        binding.lvConnected.adapter = ConnectMenuActionAdapter(context, listItems)
    }


    private fun doLayoutNoInternet() {

        binding.ivStatus.setImageResource(R.drawable.orbiesleeping)
        binding.ivStatus.setOnClickListener {  }
        stopAnimations()

        binding.tvSubtitle.visibility = View.VISIBLE

        binding.progressBar.visibility = View.INVISIBLE
        binding.tvTitle.text = getString(R.string.no_internet_title)
        binding.tvSubtitle.text = getString(R.string.no_internet_subtitle)

       // binding.btnStart.visibility = View.GONE
        binding.lvConnected.visibility = View.VISIBLE

    }

    fun doLayoutOn(context: Context) {
        binding.ivStatus.setImageResource(R.drawable.orbieon)

        binding.tvSubtitle.visibility = View.VISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvTitle.text = context.getString(R.string.connected_title)
       // binding.btnStart.visibility = View.VISIBLE
        binding.lvConnected.visibility = View.VISIBLE

        refreshMenuList(context)

        with(binding.btnStart) {
            if (Prefs.isPowerUserMode)
                text = getString(R.string.btn_tor_off)
            else
                text = "Stop VPN"

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

        binding.ivStatus.setOnClickListener {
            (activity as OrbotActivity).showLog()
        }
    }

    fun doLayoutOff() {
        binding.ivStatus.setImageResource(R.drawable.orbiesleeping)
        stopAnimations()
        binding.tvSubtitle.visibility = View.VISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.lvConnected.visibility = View.VISIBLE
        binding.tvTitle.text = getString(R.string.secure_your_connection_title)
      //  binding.tvSubtitle.text = ""//getString(R.string.secure_your_connection_subtitle)
        binding.btnStart.text = getString(R.string.btn_start_vpn)

        /**
         * //TODO hide smart connect in the UI for now
        binding.swSmartConnect.visibility = View.VISIBLE
        binding.swSmartConnect.isChecked = Prefs.smartConnect
        binding.swSmartConnect.setOnCheckedChangeListener { _, value ->
            Prefs.smartConnect = value
            doLayoutOff()
        }**/

        /**
        binding.tvConfigure.visibility = View.GONE
        binding.tvConfigure.text = getString(R.string.btn_configure)
        binding.tvConfigure.paintFlags = Paint.UNDERLINE_TEXT_FLAG
        binding.tvConfigure.setOnClickListener { openConfigureTorConnection()
        **/

        with(binding.btnStart) {

            val connectStr = ""
/**
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
            }**/

            isEnabled = true
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.orbot_btn_enabled_purple)
            )
            setOnClickListener { startTorAndVpn() }
        }

        binding.ivStatus.setOnClickListener {

        }
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

        binding.tvSubtitle.setOnClickListener {
            (activity as OrbotActivity).showLog()
        }
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
