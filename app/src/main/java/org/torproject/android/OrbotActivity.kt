package org.torproject.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.*

import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController

import com.google.android.material.bottomnavigation.BottomNavigationView
import com.scottyab.rootbeer.RootBeer

import org.torproject.android.core.LocaleHelper
import org.torproject.android.core.sendIntentToService
import org.torproject.android.core.ui.BaseActivity
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.LogBottomSheet

class OrbotActivity : BaseActivity() {

    private lateinit var logBottomSheet: LogBottomSheet
    lateinit var fragConnect: ConnectFragment

    var portSocks: Int = -1
    var portHttp: Int = -1

    var previousReceivedTorStatus: String? = null

    private var lastSelectedItemId: Int = R.id.connectFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lastSelectedItemId = savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: lastSelectedItemId
        previousReceivedTorStatus = savedInstanceState?.getString(KEY_TOR_STATUS)

        if (!Prefs.enableRotation()) {
            /* TODO TODO TODO TODO TODO
            Currently there are a lot of problems wiht landscape mode and bugs resulting from
            rotation. To this end, Orbot will be locked into either portrait or landscape
            if the device is a tablet (whichever the app is set when an activity is created)
            until these things are fixed. On smaller devices it's just portrait...
            */
            val isTablet =
                resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >=
                        Configuration.SCREENLAYOUT_SIZE_LARGE
            requestedOrientation = if (isTablet) {
                val currentOrientation = resources.configuration.orientation
                val lockedInOrientation =
                    if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE)
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                lockedInOrientation
            } else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

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

        findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = lastSelectedItemId
    }

    private fun createOrbot() {
        setContentView(R.layout.activity_orbot)

        logBottomSheet = LogBottomSheet()

        val navController: NavController = findNavController(R.id.nav_fragment)
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)

        bottomNavigationView.selectedItemId = lastSelectedItemId

        val navOptionsLeftToRight = NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_right)
            .setPopExitAnim(R.anim.slide_out_left)
            .build()

        val navOptionsRightToLeft = NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_left)
            .setExitAnim(R.anim.slide_out_right)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()

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
                R.id.connectFragment -> navController.navigate(R.id.connectFragment, null, navOptions)
                R.id.kindnessFragment -> navController.navigate(R.id.kindnessFragment, null, navOptions)
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
            registerReceiver(
                orbotServiceBroadcastReceiver,
                IntentFilter(OrbotConstants.LOCAL_ACTION_SMART_CONNECT_EVENT)
            )
        }

        requestNotificationPermission()

        Prefs.initWeeklyWorker()

        if (Prefs.detectRoot()) {
            val rootBeer = RootBeer(this)
            if (rootBeer.isRooted) {
                //we found indication of root
                val toast = Toast.makeText(
                    applicationContext, getString(R.string.root_warning), Toast.LENGTH_LONG
                )
                toast.show()
            } else {
                //we didn't find indication of root
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun requestNotificationPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) -> {
                // You can use the API that requires the permission.
            }

            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    // Register the permissions callback, which handles the user's response to the
// system permissions dialog. Save the return value, an instance of
// ActivityResultLauncher. You can use either a val, as shown in this snippet,
// or a lateinit var in your onAttach() or onCreate() method.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted. Continue the action or workflow in your
            // app.
        } else {
            // Explain to the user that the feature is unavailable because the
            // feature requires a permission that the user has denied. At the
            // same time, respect the user's decision. Don't link to system
            // settings in an effort to convince the user to change their
            // decision.
        }
    }

    override fun onResume() {
        super.onResume()
        sendIntentToService(OrbotConstants.CMD_ACTIVE)
        LocaleHelper.onAttach(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(orbotServiceBroadcastReceiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_VPN && resultCode == RESULT_OK) {
            fragConnect.startTorAndVpn()
        } else if (requestCode == REQUEST_CODE_SETTINGS && resultCode == RESULT_OK) {
            Prefs.setDefaultLocale(data?.getStringExtra("locale"))
            sendIntentToService(OrbotConstants.ACTION_LOCAL_LOCALE_SET)
            (application as OrbotApp).setLocale()
            finish()
            startActivity(Intent(this, OrbotActivity::class.java))
        } else if (requestCode == REQUEST_VPN_APP_SELECT && resultCode == RESULT_OK) {
            sendIntentToService(OrbotConstants.ACTION_RESTART_VPN) // is this enough todo?
            fragConnect.refreshMenuList(this)
        }
    }

//    override fun attachBaseContext(newBase: Context) =
  //      super.attachBaseContext(LocaleHelper.onAttach(newBase))

    var allCircumventionAttemptsFailed = false

    private val orbotServiceBroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(OrbotConstants.EXTRA_STATUS)
            when (intent?.action) {
                OrbotConstants.LOCAL_ACTION_STATUS -> {
                    if (status.equals(previousReceivedTorStatus)) return
                    when (status) {
                        OrbotConstants.STATUS_OFF -> {
                            if (previousReceivedTorStatus.equals(OrbotConstants.STATUS_STARTING)) {
                                if (allCircumventionAttemptsFailed) {
                                    allCircumventionAttemptsFailed = false
                                    previousReceivedTorStatus = status
                                    return
                                }
                                if (!Prefs.getConnectionPathway().equals(Prefs.PATHWAY_SMART) && fragConnect.isAdded && fragConnect.context != null) {
                                    fragConnect.doLayoutOff()
                                }
                            } else if (fragConnect.isAdded && fragConnect.context != null) {
                                fragConnect.doLayoutOff()
                            }
                        }

                        OrbotConstants.STATUS_STARTING -> if (fragConnect.isAdded && fragConnect.context != null) fragConnect.doLayoutStarting(this@OrbotActivity)
                        OrbotConstants.STATUS_ON -> if (fragConnect.isAdded && fragConnect.context != null) fragConnect.doLayoutOn(this@OrbotActivity)
                        OrbotConstants.STATUS_STOPPING -> {}
                    }

                    previousReceivedTorStatus = status
                }

                OrbotConstants.LOCAL_ACTION_LOG -> {
                    intent.getStringExtra(OrbotConstants.LOCAL_EXTRA_BOOTSTRAP_PERCENT)?.let {
                        // todo progress bar shouldn't be accessed directly here, *tell* the connect fragment to update
                        fragConnect.progressBar.progress = Integer.parseInt(it)
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

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab_id"
        private const val KEY_TOR_STATUS = "key_tor_status"
        const val REQUEST_CODE_VPN = 1234
        const val REQUEST_CODE_SETTINGS = 2345
        const val REQUEST_VPN_APP_SELECT = 2432
    }

    fun showLog() {
        if (!logBottomSheet.isAdded) {
            logBottomSheet.show(supportFragmentManager, OrbotActivity::class.java.simpleName)
        }
    }
}
