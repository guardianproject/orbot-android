package org.torproject.android

import IPtProxy.IPtProxy
import IPtProxy.OnTransportStopped
import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import org.torproject.android.circumvention.Bridges
import org.torproject.android.circumvention.CircumventionApiManager
import org.torproject.android.circumvention.SettingsRequest
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs
import java.io.File
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.util.*

class ConfigConnectionBottomSheet() :
    OrbotBottomSheetDialogFragment() {

    private var callbacks: ConnectionHelperCallbacks? = null

    private lateinit var rbDirect: RadioButton
    private lateinit var rbSnowflake: RadioButton
    private lateinit var rbSnowflakeAmp: RadioButton
    private lateinit var rbRequestBridge: RadioButton
    private lateinit var rbCustom: RadioButton

    private lateinit var btnAction: Button
    private lateinit var btnAskTor: Button

    companion object {
        public fun newInstance(callbacks: ConnectionHelperCallbacks): ConfigConnectionBottomSheet {
            return ConfigConnectionBottomSheet().apply {
                this.callbacks = callbacks
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.config_connection_bottom_sheet, container, false)

        rbDirect = v.findViewById(R.id.rbDirect)
        rbSnowflake = v.findViewById(R.id.rbSnowflake)
        rbSnowflakeAmp = v.findViewById(R.id.rbSnowflakeAmp)
        rbRequestBridge = v.findViewById(R.id.rbRequest)
        rbCustom = v.findViewById(R.id.rbCustom)

        val tvDirectSubtitle = v.findViewById<View>(R.id.tvDirectSubtitle)
        val tvSnowflakeSubtitle = v.findViewById<View>(R.id.tvSnowflakeSubtitle)
        val tvSnowflakeAmpSubtitle = v.findViewById<View>(R.id.tvSnowflakeAmpSubtitle)
        val tvRequestSubtitle = v.findViewById<View>(R.id.tvRequestSubtitle)
        val tvCustomSubtitle = v.findViewById<View>(R.id.tvCustomSubtitle)

        val radios = arrayListOf(rbDirect, rbSnowflake, rbSnowflakeAmp, rbRequestBridge, rbCustom)
        val radioSubtitleMap = mapOf<CompoundButton, View>(
            rbDirect to tvDirectSubtitle,
            rbSnowflake to tvSnowflakeSubtitle,
            rbSnowflakeAmp to tvSnowflakeAmpSubtitle,
            rbRequestBridge to tvRequestSubtitle,
            rbCustom to tvCustomSubtitle
        )
        val allSubtitles = arrayListOf(
            tvDirectSubtitle, tvSnowflakeSubtitle, tvSnowflakeAmpSubtitle, tvRequestSubtitle, tvCustomSubtitle
        )
        btnAction = v.findViewById(R.id.btnAction)
        btnAskTor = v.findViewById(R.id.btnAskTor)

        btnAskTor.setOnClickListener {
            askTor()
        }

        // setup containers so radio buttons can be checked if labels are clicked on
        //   v.findViewById<View>(R.id.smartContainer).setOnClickListener {rbSmart.isChecked = true}
        v.findViewById<View>(R.id.directContainer).setOnClickListener { rbDirect.isChecked = true }
        v.findViewById<View>(R.id.snowflakeContainer)
            .setOnClickListener { rbSnowflake.isChecked = true }
        v.findViewById<View>(R.id.snowflakeAmpContainer)
            .setOnClickListener { rbSnowflakeAmp.isChecked = true }
        v.findViewById<View>(R.id.requestContainer)
            .setOnClickListener { rbRequestBridge.isChecked = true }
        v.findViewById<View>(R.id.customContainer).setOnClickListener { rbCustom.isChecked = true }
        v.findViewById<View>(R.id.tvCancel).setOnClickListener { dismiss() }

        rbDirect.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
                radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
            }
        }
        rbSnowflake.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
                radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
            }
        }
        rbSnowflakeAmp.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
                radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
            }
        }
        rbRequestBridge.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
                radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
                btnAction.text = getString(R.string.next)
            } else {
                btnAction.text = getString(R.string.connect)
            }
        }
        rbCustom.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
                radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
                btnAction.text = getString(R.string.next)
            } else {
                btnAction.text = getString(R.string.connect)
            }
        }

        selectRadioButtonFromPreference()

        btnAction.setOnClickListener {
            if (rbRequestBridge.isChecked) {
                MoatBottomSheet(object : ConnectionHelperCallbacks {
                    override fun tryConnecting() {
                        Prefs.putConnectionPathway(Prefs.PATHWAY_CUSTOM)
                        rbCustom.isChecked = true
                        closeAndConnect()
                    }
                }).show(requireActivity().supportFragmentManager, MoatBottomSheet.TAG)
            } else if (rbDirect.isChecked) {
                Prefs.putConnectionPathway(Prefs.PATHWAY_DIRECT)
                closeAndConnect()
            } else if (rbSnowflake.isChecked) {
                Prefs.putConnectionPathway(Prefs.PATHWAY_SNOWFLAKE)
                closeAndConnect()
            } else if (rbSnowflakeAmp.isChecked) {
                Prefs.putConnectionPathway(Prefs.PATHWAY_SNOWFLAKE_AMP)
                closeAndConnect()
            } else if (rbCustom.isChecked) {
                CustomBridgeBottomSheet(object : ConnectionHelperCallbacks {
                    override fun tryConnecting() {
                        Prefs.putConnectionPathway(Prefs.PATHWAY_CUSTOM)
                        closeAndConnect()
                    }
                }).show(requireActivity().supportFragmentManager, CustomBridgeBottomSheet.TAG)
            }
        }

        return v
    }

    private fun closeAndConnect() {
        closeAllSheets()
        callbacks?.tryConnecting()
    }

    // it's 2022 and android makes you do ungodly things for mere radio button functionality
    private fun nestedRadioButtonKludgeFunction(rb: RadioButton, all: List<RadioButton>) =
        all.forEach { if (it != rb) it.isChecked = false }

    private fun onlyShowActiveSubtitle(showMe: View, all: List<View>) = all.forEach {
        if (it == showMe) it.visibility = View.VISIBLE
        else it.visibility = View.GONE
    }

    private fun selectRadioButtonFromPreference() {
        val pref = Prefs.getConnectionPathway()
        if (pref.equals(Prefs.PATHWAY_CUSTOM)) rbCustom.isChecked = true
        if (pref.equals(Prefs.PATHWAY_SNOWFLAKE)) rbSnowflake.isChecked = true
        if (pref.equals(Prefs.PATHWAY_SNOWFLAKE_AMP)) rbSnowflakeAmp.isChecked = true
        if (pref.equals(Prefs.PATHWAY_DIRECT)) rbDirect.isChecked = true
    }

    private var circumventionApiBridges: List<Bridges?>? = null
    private var circumventionApiIndex = 0

    private fun askTor() {

        val dLeft = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_faq)
        btnAskTor.text = getString(R.string.asking)
        btnAskTor.setCompoundDrawablesWithIntrinsicBounds(dLeft, null, null, null)

        val fileCacheDir = File(requireActivity().cacheDir, "pt")
        if (!fileCacheDir.exists()) {
            fileCacheDir.mkdir()
        }

        val proxy = OrbotService.getIptProxyController(context)
        proxy.start(IPtProxy.MeekLite,null);

        val pUsername =
            "url=" + OrbotService.getCdnFront("moat-url") + ";front=" + OrbotService.getCdnFront("moat-front")
        val pPassword = "\u0000"

        //    Log.d(getClass().getSimpleName(), String.format("mHost=%s, mPort=%d, mUsername=%s, mPassword=%s", mHost, mPort, mUsername, mPassword))
        val authenticator: Authenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                Log.d(javaClass.simpleName, "getPasswordAuthentication!")
                return PasswordAuthentication(pUsername, pPassword.toCharArray())
            }
        }

        Authenticator.setDefault(authenticator)

        val countryCodeValue: String = getDeviceCountryCode(requireContext())

        CircumventionApiManager(proxy.port(IPtProxy.MeekLite)).getSettings(SettingsRequest(countryCodeValue), {
            it?.let {
                circumventionApiBridges = it.settings
                if (circumventionApiBridges == null) {
                    //Log.d("abc", "settings is null, we can assume a direct connect is fine ")
                    rbDirect.isChecked = true

                } else {

                    // Log.d("abc", "settings is $circumventionApiBridges")
                    circumventionApiBridges?.forEach { b ->
                        //   Log.d("abc", "BRIDGE $b")
                    }

                    //got bridges, let's set them
                    setPreferenceForSmartConnect()
                }

                proxy.stop(IPtProxy.MeekLite)
            }
        }, {
            // TODO what happens to the app in this case?!
            Log.e("ConfigConnectionBottomSheet", "Couldn't hit circumvention API... $it")
            Toast.makeText(requireContext(), "Ask Tor was not available", Toast.LENGTH_LONG).show()
        })
    }

    private fun getDeviceCountryCode(context: Context): String {
        var countryCode: String?

        // Try to get country code from TelephonyManager service
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Query first getSimCountryIso()
        countryCode = tm.simCountryIso
        if (countryCode != null && countryCode.length == 2) return countryCode.lowercase(Locale.getDefault())

        countryCode = tm.networkCountryIso
        if (countryCode != null && countryCode.length == 2) return countryCode.lowercase(Locale.getDefault())

        countryCode = context.resources.configuration.locales[0].country

        return if (countryCode != null && countryCode.length == 2) countryCode.lowercase(Locale.getDefault()) else "us"

    }

    private fun setPreferenceForSmartConnect() {

        val dLeft = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_green_check)
        btnAskTor.setCompoundDrawablesWithIntrinsicBounds(dLeft, null, null, null)

        circumventionApiBridges?.let {
            if (it.size == circumventionApiIndex) {
                circumventionApiBridges = null
                circumventionApiIndex = 0
                rbDirect.isChecked = true
                btnAskTor.text = getString(R.string.connection_direct)

                return
            }
            val b = it[circumventionApiIndex]!!.bridges
            when (b.type) {
                CircumventionApiManager.BRIDGE_TYPE_SNOWFLAKE -> {
                    Prefs.putConnectionPathway(Prefs.PATHWAY_SNOWFLAKE)
                    rbSnowflake.isChecked = true
                    btnAskTor.text = getString(R.string.connection_snowflake)
                }

                CircumventionApiManager.BRIDGE_TYPE_OBFS4 -> {
                    rbCustom.isChecked = true
                    btnAskTor.text = getString(R.string.connection_custom)

                    var bridgeStrings = ""
                    b.bridge_strings!!.forEach { bridgeString ->
                        bridgeStrings += "$bridgeString\n"
                    }
                    Prefs.setBridgesList(bridgeStrings)
                    Prefs.putConnectionPathway(Prefs.PATHWAY_CUSTOM)
                }

                else -> {
                    rbDirect.isChecked = true
                }
            }
            circumventionApiIndex += 1
        }
    }
}
