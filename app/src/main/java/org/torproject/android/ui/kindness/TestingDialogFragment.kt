package org.torproject.android.ui.kindness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.torproject.android.R
import org.torproject.android.databinding.FragmentTestingBinding
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.service.vpn.VpnServicePrepareWrapper
import org.torproject.android.ui.connect.ConnectUiState
import org.torproject.android.ui.connect.ConnectViewModel
import org.torproject.android.util.CoroutineUtils.waitUntilStateFlowEquals
import org.torproject.android.util.NetworkUtils
import org.torproject.android.util.Prefs
import org.torproject.android.util.sendIntentToService
import org.torproject.jni.TorService
import kotlin.getValue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Kindness Mode Quality Test
 *
 * - First, immediately fail the test if there's a non orbot VPN running
 * - Second, if we've passed a quality test in the past 24 hours, skip retesting
 *      otherwise, take the test:
 *      A: if the user is connected to tor with no bridges/proxy, you pass
 *      B: if the user isn't connected to tor, warn the user about connecting to tor and attempt
 *         a direct connection. Pass if we succeed.
 *      C: if the user has a bridge/proxy, turn tor off. perform option B. When the test is
 *         completed, turn the user's original Tor connection back on.
 *
 *   Sets Prefs.snowflakeNeedsQualityCheck to false if test passes, true if otherwise
 */
class TestingDialogFragment : DialogFragment() {

    private lateinit var mBinding: FragmentTestingBinding
    val torConnectedViewModel: ConnectViewModel by activityViewModels()

    private var stoppedNormalTorConnection = false

    private var connectionTestServiceConnection: ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // disable device rotation while this dialog is running
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mBinding = FragmentTestingBinding.inflate(inflater, container, false)

        mBinding.btnCancel1.setOnClickListener {
            dismiss()
        }

        mBinding.btnContinue1.setOnClickListener {
            mBinding.boxInstructions.visibility = View.GONE
            mBinding.boxWarnings.visibility = View.VISIBLE
        }

        mBinding.btnCancel2.setOnClickListener {
            dismiss()
        }

        mBinding.swAcknowledge.setOnCheckedChangeListener { _, value ->
            mBinding.btnContinue2.isEnabled = value

            val context = context ?: return@setOnCheckedChangeListener

            mBinding.btnContinue2.backgroundTintList = ContextCompat.getColorStateList(
                context, if (value) R.color.orbot_btn_enabled_purple else R.color.orbot_btn_disable_grey)
        }

        mBinding.btnContinue2.setOnClickListener {
            if (mBinding.btnContinue2.isEnabled) {
                mBinding.boxWarnings.visibility = View.GONE
                mBinding.boxTesting.visibility = View.VISIBLE

                doQualityTestRequiringConsent()
            }
        }

        mBinding.btnStopTest.setOnClickListener {
            dismiss()
        }

        mBinding.btnContinue3.setOnClickListener {
            setFragmentResult(KEY_RESULT, Bundle().apply { putBoolean(KEY_RESULT, true) })
            dismiss()
        }
        mBinding.btnDeclinedBoxOk.setOnClickListener { dismiss() }

        return mBinding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            dialog?.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // benign tests to immediately see if the user can/can't use kindness mode
            // if we don't get a definite answer, prompt the user for consent to determine for sure
            doQualityTestRequiringNoUserConsent()
        }

    }

    /**
     * This part of the connection test doesn't require user consent
     * It automatically fails if:
     *  - Orbot doesn't have a direct Internet connection
     *  - the user is using a non-Orbot VPN
     *
     * If we didn't automatically fail, we can automatically pass if:
     *  - the user currently has a direct connection to the tor network
     *  - the user has passed the quality test in the past 24 hours
     *
     *  Otherwise, the test is still inconclusive. Obtain the user's consent to complete the text
     *  and give them the option to stop testing...
     */
    private fun doQualityTestRequiringNoUserConsent() {

        // immediately fail if there's another VPN running
        if (NetworkUtils.isNonOrbotVpnActive(requireContext(), TAG)) {
            showTestFailedUi(
                errorExplanation = getString(R.string.testing_explanation_other_vpn),
                bubbleMsg = getString(R.string.testing_explanation_other_vpn_bubble),
                bubbleAction = {
                    VpnServicePrepareWrapper.openVpnSystemSettings(this)
                    dismiss()
                }
            )
            return
        }

        // immediately fail if there's no internet
        val torConnectionState = torConnectedViewModel.uiState.value
        if (torConnectionState == ConnectUiState.NoInternet) {
            showTestFailedUi(bubbleMsg = getString(R.string.testing_explanation_no_net))
            return
        }

        // immediately succeed if we've recently succeeded
        if (!Prefs.snowflakeNeedsQualityCheck) {
            Log.d(TAG, "recently passed quality check, proceeding")
            mBinding.btnContinue3.callOnClick()
            return
        }

        // immediately succeed if you're already connecting directly to Tor
        if (torConnectionState == ConnectUiState.On && Prefs.transport == Transport.NONE && Prefs.outboundProxy.first == null) {
            Log.d(TAG, "there's an active direct connection to tor, no need to test")
            Prefs.snowflakeNeedsQualityCheck = false
            mBinding.btnContinue3.callOnClick()
            return
        }

        // at this point, we need to obtain user consent to actually do the connection test...
        showUserConsentUi()
    }

    private fun showUserConsentUi() {
        mBinding.boxInstructions.visibility = View.VISIBLE
        mBinding.boxWarnings.visibility = View.GONE
        mBinding.boxTesting.visibility = View.GONE
        mBinding.boxApproved.visibility = View.GONE
        mBinding.boxDeclined.visibility = View.GONE
    }

    private fun isOrbotOnOrStarting(): Boolean {
        val torConnectionState = currentTorState()
        return torConnectionState is ConnectUiState.On || torConnectionState is ConnectUiState.Starting
    }


    /** This part of the connection test requires the user's consent, since it involves attempting
     * a direct tor connection that censors can trivially detect, and possibly also temporarily
     * disabling Orbot VPN if there's an active connection with censorship circumvention tech.
     */
    private fun doQualityTestRequiringConsent() {
        lifecycleScope.launch {
            Log.d(TAG, "starting consent connection test ${currentTorState()}")
            val timestampStart = System.currentTimeMillis()
            if (isOrbotOnOrStarting()) {
                Log.d(TAG, "disconnecting user's Tor for connection test...")
                requireActivity().sendIntentToService(TorService.ACTION_STOP)
                stoppedNormalTorConnection = true
                waitUntilStateFlowEquals(
                    torConnectedViewModel.uiState,
                    ConnectUiState.Off,
                    CONNECTION_TEST_TIMEOUT_MS.milliseconds,
                    TAG
                )
                Log.d(TAG, "Tor is now... ${currentTorState()}")
                if (currentTorState() != ConnectUiState.Off) {
                    stoppedNormalTorConnection = false
                    Log.d(TAG, "Failed Connection test, OrbotService still isn't off")
                    showTestFailedUi()
                    return@launch
                }
            }
            Log.d(TAG, "Tor is now... ${currentTorState()}")
            val timeStampTorOff = System.currentTimeMillis()
            val timeRemaining = CONNECTION_TEST_TIMEOUT_MS - (timeStampTorOff - timestampStart)
            Log.d(TAG, "connection test time remaining= $timeRemaining")
            connectionTestServiceConnection =
                TestTorForSnowflakeProxyService.launchTorTestingService(
                    requireActivity(),
                    torStatusReceiver
                )
            delay(timeRemaining.milliseconds)
            if (Prefs.snowflakeNeedsQualityCheck) {
                Log.d(TAG, "Couldn't directly connect in $CONNECTION_TEST_TIMEOUT_MS ms")
                unbindServiceIfBound()
                showTestFailedUi()
            }
        }
    }

    private fun currentTorState() = torConnectedViewModel.uiState.value

    val torStatusReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(TorService.EXTRA_STATUS)
            Log.d(TAG, "Got tor status from testing service: $status")
            if (status == TorService.STATUS_ON) {
                lifecycleScope.launch {
                    Log.d(TAG, "TEST PASSED")
                    Prefs.snowflakeNeedsQualityCheck = false
                    unbindServiceIfBound()
                    if (stoppedNormalTorConnection) {
                        delay(250.milliseconds)
                        Log.d(TAG, "relaunching OrbotService...")
                        requireActivity().sendIntentToService(TorService.ACTION_START)
                    }
                    showTestPassedUi()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindServiceIfBound()
        // restore device rotation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    private fun unbindServiceIfBound() {
        if (connectionTestServiceConnection != null) {
            Log.d(TAG, "unregistering receiver, killing service")
            val connection = connectionTestServiceConnection!!
            requireActivity().unregisterReceiver(torStatusReceiver)
            requireActivity().unbindService(connection)
            connectionTestServiceConnection = null
        }
    }

    fun showTestPassedUi() {
        Prefs.snowflakeNeedsQualityCheck = false
        setFragmentResult(KEY_RESULT, Bundle().apply { putBoolean(KEY_RESULT, true) })
        mBinding.boxTesting.visibility = View.GONE
        mBinding.boxApproved.visibility = View.VISIBLE
    }

    fun showTestFailedUi(
        errorExplanation: String? = null,
        bubbleMsg: String? = null,
        bubbleAction: View.OnClickListener = {}
    ) {
        Prefs.snowflakeNeedsQualityCheck = true
        mBinding.boxTesting.visibility = View.GONE
        mBinding.boxDeclined.visibility = View.VISIBLE
        errorExplanation?.let {
            mBinding.tvExplanationDeclined.text = errorExplanation
        }

        if (bubbleMsg == null) {
            mBinding.tvErrorBubbleMessage.visibility = View.INVISIBLE
        }
        else {
            mBinding.tvErrorBubbleMessage.visibility = View.VISIBLE
            mBinding.tvErrorBubbleMessage.text = bubbleMsg
            mBinding.tvErrorBubbleMessage.setOnClickListener(bubbleAction)
        }
    }

    companion object {
        const val KEY_RESULT = "kindness_test_result"
        const val TAG = "TestingFragment"
        const val CONNECTION_TEST_TIMEOUT_MS = 90000L
        fun show(fragmentManager: FragmentManager) =
            TestingDialogFragment().show(fragmentManager, TAG)
    }
}
