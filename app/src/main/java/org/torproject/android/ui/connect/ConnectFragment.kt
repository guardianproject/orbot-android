package org.torproject.android.ui.connect

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import net.freehaven.tor.control.TorControlCommands
import org.torproject.android.OrbotActivity
import org.torproject.android.R
import org.torproject.android.core.putNotSystem
import org.torproject.android.core.sendIntentToService
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.AppManagerActivity
import org.torproject.android.ui.OrbotMenuAction

class ConnectFragment : Fragment(), ConnectionHelperCallbacks,
    ExitNodeBottomSheet.ExitNodeSelectedCallback {

    // main screen UI
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvConfigure: TextView
    private lateinit var btnStartVpn: Button
    private lateinit var ivOnion: ImageView
    private lateinit var ivOnionShadow: ImageView
    lateinit var progressBar: ProgressBar
    private lateinit var lvConnectedActions: ListView

    private val viewModel: ConnectViewModel by activityViewModels()

    private val lastStatus: String
        get() = (activity as? OrbotActivity)?.previousReceivedTorStatus ?: ""

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
                                progressBar.progress = it
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
        val view = inflater.inflate(R.layout.fragment_connect, container, false)
        view?.let {

            tvTitle = it.findViewById(R.id.tvTitle)
            tvSubtitle = it.findViewById(R.id.tvSubtitle)
            tvConfigure = it.findViewById(R.id.tvConfigure)
            btnStartVpn = it.findViewById(R.id.btnStart)
            ivOnion = it.findViewById(R.id.ivStatus)
            ivOnionShadow = it.findViewById(R.id.ivShadow)
            progressBar = it.findViewById(R.id.progressBar)
            lvConnectedActions = it.findViewById(R.id.lvConnected)

            if (Prefs.isPowerUserMode()) {
                btnStartVpn.text = getString(R.string.connect)
            }

            viewModel.updateState(requireContext(), lastStatus)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateState(requireContext(), lastStatus)
    }

    private fun stopTorAndVpn() {
        requireContext().sendIntentToService(OrbotConstants.ACTION_STOP)
        requireContext().sendIntentToService(OrbotConstants.ACTION_STOP_VPN)
        doLayoutOff()
    }

    private fun stopAnimations() {
        ivOnion.clearAnimation()
        ivOnionShadow.clearAnimation()
    }

    private fun sendNewnymSignal() {
        requireContext().sendIntentToService(TorControlCommands.SIGNAL_NEWNYM)
        ivOnion.animate().alpha(0f).duration = 500
        Handler().postDelayed({ ivOnion.animate().alpha(1f).duration = 500 }, 600)
    }

    private fun openExitNodeDialog() {
        ExitNodeBottomSheet(this).show(
            requireActivity().supportFragmentManager, "ExitNodeBottomSheet"
        )
    }

    fun startTorAndVpn() {
        val vpnIntent = VpnService.prepare(requireActivity())?.putNotSystem()
        if (vpnIntent != null && (!Prefs.isPowerUserMode())) {
            startActivityForResult(vpnIntent, OrbotActivity.Companion.REQUEST_CODE_VPN)
        } else { // either the vpn permission hasn't been granted or we are in power user mode
            Prefs.putUseVpn(!Prefs.isPowerUserMode())
            if (Prefs.isPowerUserMode()) {
                // android 14 awkwardly needs this permission to be explicitly granted to use the
                // FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED permission without grabbing a VPN Intent
                val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    RequestScheduleExactAlarmDialogFragment().show(requireActivity().supportFragmentManager, "RequestAlarmPermDialog")
                } else {
                    ivOnion.setImageResource(R.drawable.torstarting)
                    with(btnStartVpn) {
                        text = context.getString(android.R.string.cancel)
                    }
                    requireContext().sendIntentToService(OrbotConstants.ACTION_START)
                }
            } else { // normal VPN mode, power user is disabled
                ivOnion.setImageResource(R.drawable.torstarting)
                with(btnStartVpn) {
                    text = context.getString(android.R.string.cancel)
                }
                requireContext().sendIntentToService(OrbotConstants.ACTION_START)
                requireContext().sendIntentToService(OrbotConstants.ACTION_START_VPN)
            }
        }
    }

    fun refreshMenuList(context: Context) {
        val listItems =
            arrayListOf(
                OrbotMenuAction(R.string.btn_change_exit, 0) { openExitNodeDialog() },
                OrbotMenuAction(R.string.btn_refresh, R.drawable.ic_refresh) { sendNewnymSignal() },
                OrbotMenuAction(R.string.btn_tor_off, R.drawable.ic_power) { stopTorAndVpn() })
        if (!Prefs.isPowerUserMode()) listItems.add(
            0,
            OrbotMenuAction(R.string.btn_choose_apps, R.drawable.ic_choose_apps) {
                startActivityForResult(
                    Intent(requireActivity(), AppManagerActivity::class.java),
                    OrbotActivity.Companion.REQUEST_VPN_APP_SELECT
                )
            })
        lvConnectedActions.adapter = ConnectMenuActionAdapter(context, listItems)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OrbotActivity.Companion.REQUEST_CODE_VPN && resultCode == AppCompatActivity.RESULT_OK) {
            startTorAndVpn()
        } else if (requestCode == OrbotActivity.Companion.REQUEST_CODE_SETTINGS && resultCode == AppCompatActivity.RESULT_OK) {
            // todo respond to language change extra data here...
        } else if (requestCode == OrbotActivity.Companion.REQUEST_VPN_APP_SELECT && resultCode == AppCompatActivity.RESULT_OK) {
            requireContext().sendIntentToService(OrbotConstants.ACTION_RESTART_VPN) // is this enough todo?
            refreshMenuList(requireContext())
        }
    }

    private fun doLayoutForCircumventionApi() {
        // TODO prompt user to request bridge over MOAT
        progressBar.progress = 0
        tvTitle.text = getString(R.string.having_trouble)
        tvSubtitle.text = getString(R.string.having_trouble_subtitle)
        tvSubtitle.visibility = View.VISIBLE
        btnStartVpn.text = getString(R.string.solve_captcha)
        btnStartVpn.setOnClickListener {
            MoatBottomSheet(this).show(
                requireActivity().supportFragmentManager, "CircumventionFailed"
            )
        }
        tvConfigure.text = getString(android.R.string.cancel)
        tvConfigure.setOnClickListener {
            doLayoutOff()
        }
    }


    private fun doLayoutNoInternet() {

        ivOnion.setImageResource(R.drawable.nointernet)

        stopAnimations()

        tvSubtitle.visibility = View.VISIBLE

        progressBar.visibility = View.INVISIBLE
        tvTitle.text = getString(R.string.no_internet_title)
        tvSubtitle.text = getString(R.string.no_internet_subtitle)

        btnStartVpn.visibility = View.GONE
        lvConnectedActions.visibility = View.GONE
        tvConfigure.visibility = View.GONE
    }

    fun doLayoutOn(context: Context) {
        ivOnion.setImageResource(R.drawable.toron)

        tvSubtitle.visibility = View.GONE
        progressBar.visibility = View.INVISIBLE
        tvTitle.text = context.getString(R.string.connected_title)
        btnStartVpn.visibility = View.GONE
        lvConnectedActions.visibility = View.VISIBLE
        tvConfigure.visibility = View.GONE

        refreshMenuList(context)

        ivOnion.setOnClickListener {}
    }

    fun doLayoutOff() {
        ivOnion.setImageResource(R.drawable.toroff)
        stopAnimations()
        tvSubtitle.visibility = View.VISIBLE
        progressBar.visibility = View.INVISIBLE
        lvConnectedActions.visibility = View.GONE
        tvTitle.text = getString(R.string.secure_your_connection_title)
        tvSubtitle.text = getString(R.string.secure_your_connection_subtitle)
        tvConfigure.visibility = View.VISIBLE
        tvConfigure.text = getString(R.string.btn_configure)
        tvConfigure.paintFlags = Paint.UNDERLINE_TEXT_FLAG
        tvConfigure.setOnClickListener { openConfigureTorConnection() }

        with(btnStartVpn) {
            visibility = View.VISIBLE

            var connectStr = ""
            when (Prefs.getTorConnectionPathway()) {
                Prefs.CONNECTION_PATHWAY_DIRECT -> connectStr =
                    context.getString(R.string.action_use) + ' ' + getString(R.string.direct_connect)

                Prefs.CONNECTION_PATHWAY_SNOWFLAKE -> connectStr =
                    context.getString(R.string.action_use) + ' ' + getString(R.string.snowflake)

                Prefs.CONNECTION_PATHWAY_SNOWFLAKE_AMP -> connectStr =
                    context.getString(R.string.action_use) + ' ' + getString(R.string.snowflake_amp)

                Prefs.CONNECTION_PATHWAY_SNOWFLAKE_SQS -> connectStr =
                    context.getString(R.string.action_use) + ' ' + getString(R.string.snowflake_sqs)

                Prefs.CONNECTION_PATHWAY_OBFS4 -> connectStr =
                    context.getString(R.string.action_use) + ' ' + getString(R.string.custom_bridge)
            }

            text = when {
                Prefs.isPowerUserMode() -> getString(R.string.connect)
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

        ivOnion.setOnClickListener {
            startTorAndVpn()
        }
    }

    fun doLayoutStarting(context: Context) {
        tvSubtitle.visibility = View.GONE
        with(progressBar) {
            progress = 0
            visibility = View.VISIBLE
        }
        ivOnion.setImageResource(R.drawable.torstarting)
        val animHover = AnimationUtils.loadAnimation(context, R.anim.hover)
        animHover.repeatCount = 7
        animHover.repeatMode = Animation.REVERSE
        ivOnion.animation = animHover
        animHover.start()
        val animShadow = AnimationUtils.loadAnimation(context, R.anim.shadow)
        animShadow.repeatCount = 7
        animShadow.repeatMode = Animation.REVERSE
        ivOnionShadow.animation = animShadow
        animShadow.start()

        tvTitle.text = context.getString(R.string.trying_to_connect_title)
        with(btnStartVpn) {
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
    }


    private fun openConfigureTorConnection() =
        ConfigConnectionBottomSheet.Companion.newInstance(this)
            .show(
                requireActivity().supportFragmentManager, OrbotActivity::class.java.simpleName
            )


    override fun tryConnecting() {
        startTorAndVpn() // TODO for now just start tor and VPN, we need to decouple this down the line
    }

    override fun onExitNodeSelected(countryCode: String, displayCountryName: String) {

        //tor format expects "{" for country code
        Prefs.setExitNodes("{$countryCode}")

        requireContext().sendIntentToService(
            Intent(
                requireActivity(),
                OrbotService::class.java
            ).setAction(OrbotConstants.CMD_SET_EXIT).putExtra("exit", countryCode)
        )

        refreshMenuList(requireContext())
    }
}