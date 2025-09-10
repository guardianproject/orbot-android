package org.torproject.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.addCallback

import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController

import com.google.android.material.bottomnavigation.BottomNavigationView
import com.scottyab.rootbeer.RootBeer

import org.torproject.android.service.util.sendIntentToService
import org.torproject.android.ui.core.BaseActivity
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.util.Prefs
import org.torproject.android.service.util.canStartForegroundServices
import org.torproject.android.service.util.showToast
import org.torproject.android.ui.more.LogBottomSheet
import org.torproject.android.ui.connect.ConnectViewModel
import org.torproject.android.ui.connect.RequestPostNotificationPermission
import org.torproject.android.ui.core.DeviceAuthenticationPrompt
import java.util.Locale

class OrbotActivity : BaseActivity() {

    private lateinit var logBottomSheet: LogBottomSheet

    var portSocks: Int = -1
    var portHttp: Int = -1

    var previousReceivedTorStatus: String? = null

    private var lastSelectedItemId: Int = R.id.connectFragment

    // used to hide UI while password isn't obtained
    private var rootLayout: View? = null

    private val connectViewModel: ConnectViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        lastSelectedItemId = savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: lastSelectedItemId
        previousReceivedTorStatus = savedInstanceState?.getString(KEY_TOR_STATUS)

        // programmatically set title to "Orbot" since camo mode will overwrite it here from manifest
        title = getString(R.string.app_name)

        try {
            createOrbot()

        } catch (re: RuntimeException) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)

            //clear malicious intent
            intent = null
            finish()
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_TAB, lastSelectedItemId)
        outState.putString(KEY_TOR_STATUS, previousReceivedTorStatus)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        lastSelectedItemId = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.connectFragment)
        previousReceivedTorStatus = savedInstanceState.getString(KEY_TOR_STATUS)

        val navController = findNavController(R.id.nav_fragment)
        val currentDest = navController.currentDestination?.id

        if (currentDest != lastSelectedItemId) {
            navController.navigate(lastSelectedItemId)
        }

        findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId =
            lastSelectedItemId
    }

    private fun createOrbot() {
        setContentView(R.layout.activity_orbot)
        rootLayout = findViewById(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_fragment)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        logBottomSheet = LogBottomSheet()

        val navController: NavController = findNavController(R.id.nav_fragment)
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)

        bottomNavigationView.selectedItemId = lastSelectedItemId

        val navOptionsLeftToRight = NavOptions.Builder().setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left).setPopEnterAnim(R.anim.slide_in_right)
            .setPopExitAnim(R.anim.slide_out_left).build()

        val navOptionsRightToLeft = NavOptions.Builder().setEnterAnim(R.anim.slide_in_left)
            .setExitAnim(R.anim.slide_out_right).setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right).build()

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == lastSelectedItemId) {
                return@setOnItemSelectedListener true
            }

            val navOptions = if (item.itemId > lastSelectedItemId) {
                navOptionsLeftToRight
            } else {
                navOptionsRightToLeft
            }

            when (item.itemId) {
                R.id.connectFragment -> navController.navigate(
                    R.id.connectFragment, null, navOptions
                )

                R.id.kindnessFragment -> navController.navigate(
                    R.id.kindnessFragment, null, navOptions
                )

                R.id.moreFragment -> navController.navigate(R.id.moreFragment, null, navOptions)
            }

            lastSelectedItemId = item.itemId
            true
        }

        with(LocalBroadcastManager.getInstance(this)) {
            registerReceiver(
                orbotServiceBroadcastReceiver, IntentFilter(OrbotConstants.LOCAL_ACTION_STATUS)
            )
            registerReceiver(
                orbotServiceBroadcastReceiver, IntentFilter(OrbotConstants.LOCAL_ACTION_LOG)
            )
            registerReceiver(
                orbotServiceBroadcastReceiver, IntentFilter(OrbotConstants.LOCAL_ACTION_PORTS)
            )
        }

        requestNotificationPermission()

        Prefs.initWeeklyWorker()

        if (!rootDetectionShown && Prefs.detectRoot() && RootBeer(this).isRooted) {
            //we found indication of root
            applicationContext.showToast(getString(R.string.root_warning))

            rootDetectionShown = true
        }

        onBackPressedDispatcher.addCallback(this) {
            if (lastSelectedItemId != R.id.connectFragment) {
                bottomNavigationView.selectedItemId = R.id.connectFragment
            } else finish()
        }
    }

    private fun requestNotificationPermission() {
        // automatically granted on Android 12 and lower
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            return
        val checkPostNotificationPerm =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        when (checkPostNotificationPerm) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.d(
                    "OrbotActivity",
                    "Granted Permission ${Manifest.permission.POST_NOTIFICATIONS}"
                )
            }

            else -> {
                Log.d(
                    "OrbotActivity",
                    "Try Prompting For ${Manifest.permission.POST_NOTIFICATIONS}"
                )
                requestPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog.
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("OrbotActivity", "User just granted ${Manifest.permission.POST_NOTIFICATIONS}")
        } else {
            Log.d("OrbotActivity", "Notification denied")
            RequestPostNotificationPermission().show(supportFragmentManager, "RequestNotificationDialog")
        }
    }

    override fun onStart() {
        super.onStart()
        promptDeviceAuthenticationIfRequired()
    }

    override fun onResume() {
        super.onResume()
        sendIntentToService(OrbotConstants.CMD_ACTIVE)
        // turn off snowflake proxy if on API 34 and you no longer have 
        // the right permissions to be a foreground service....
        if (Prefs.beSnowflakeProxy() && !canStartForegroundServices()) {
            Prefs.setBeSnowflakeProxy(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(orbotServiceBroadcastReceiver)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_VPN && resultCode == RESULT_OK) {
            connectViewModel.triggerStartTorAndVpn()
        } else if (requestCode == REQUEST_CODE_SETTINGS && resultCode == RESULT_OK) {
            Prefs.defaultLocale = data?.getStringExtra("locale") ?: Locale.getDefault().language
            sendIntentToService(OrbotConstants.ACTION_LOCAL_LOCALE_SET)
            (application as OrbotApp).setLocale()
            finish()
            startActivity(Intent(this, OrbotActivity::class.java))
        } else if (requestCode == REQUEST_VPN_APP_SELECT && resultCode == RESULT_OK) {
            sendIntentToService(OrbotConstants.ACTION_RESTART_VPN) // is this enough todo?
            connectViewModel.triggerRefreshMenuList()
        }
    }

    private val orbotServiceBroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(OrbotConstants.EXTRA_STATUS)
            when (intent?.action) {
                OrbotConstants.LOCAL_ACTION_STATUS -> {
                    if (status != previousReceivedTorStatus) {
                        connectViewModel.updateState(this@OrbotActivity, status)
                        previousReceivedTorStatus = status
                    }
                }

                OrbotConstants.LOCAL_ACTION_LOG -> {
                    intent.getStringExtra(OrbotConstants.LOCAL_EXTRA_BOOTSTRAP_PERCENT)?.let {
                        connectViewModel.updateBootstrapPercent(it.toIntOrNull() ?: 0)
                    }
                    intent.getStringExtra(OrbotConstants.LOCAL_EXTRA_LOG)?.let {
                        logBottomSheet.appendLog(it)
                    }
                }

                OrbotConstants.LOCAL_ACTION_PORTS -> {
                    val socks = intent.getIntExtra(OrbotConstants.EXTRA_SOCKS_PROXY_PORT, -1)
                    val http = intent.getIntExtra(OrbotConstants.EXTRA_HTTP_PROXY_PORT, -1)
                    if (http > 0 && socks > 0) {
                        portSocks = socks
                        portHttp = http
                    }
                }

                else -> {}
            }
        }
    }

    private fun promptDeviceAuthenticationIfRequired() {
        if (!Prefs.requireDeviceAuthentication)
            return

        if (!OrbotApp.shouldRequestAuthentication)
            return

        // if app was closed, we should re-request password upon
        // re-open, even if we've gotten it already
        OrbotApp.shouldRequestAuthentication = false

        if (OrbotApp.isAuthenticationPromptOpenLegacyFlag)
            return

        OrbotApp.isAuthenticationPromptOpenLegacyFlag = true

        rootLayout?.visibility = View.INVISIBLE
        DeviceAuthenticationPrompt.openPrompt(this, object :
            BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errorMsg: CharSequence) {
                OrbotApp.isAuthenticationPromptOpenLegacyFlag = false
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    OrbotApp.resetLockFlags()
                    finish() // user presses back, just close
                } else if (errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE) {
                    // we set this flag when Orbot *can't* authenticate, ie no password or unsupported device
                    showToast(errorMsg) // String set in RequirePasswordPrompt.kt
                    rootLayout?.visibility = View.VISIBLE
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                OrbotApp.shouldRequestAuthentication = false
                OrbotApp.isAuthenticationPromptOpenLegacyFlag = false
                rootLayout?.visibility = View.VISIBLE
            }

            override fun onAuthenticationFailed() {
                OrbotApp.resetLockFlags()
                finish()
            }
        })
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab_id"
        private const val KEY_TOR_STATUS = "key_tor_status"
        const val REQUEST_CODE_VPN = 1234
        const val REQUEST_CODE_SETTINGS = 2345
        const val REQUEST_VPN_APP_SELECT = 2432

        // Make sure this is only shown once per app-start, not on every device rotation.
        private var rootDetectionShown = false
    }

    fun showLog() {
        if (!logBottomSheet.isAdded) {
            logBottomSheet.show(supportFragmentManager, OrbotActivity::class.java.simpleName)
        }
    }
}
