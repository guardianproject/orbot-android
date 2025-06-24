package org.torproject.android.ui.connect

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
import org.torproject.android.R
import org.torproject.android.circumvention.Bridges
import org.torproject.android.circumvention.CircumventionApiManager
import org.torproject.android.circumvention.SettingsRequest
import org.torproject.android.service.circumvention.Moat
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.OrbotBottomSheetDialogFragment
import java.util.*

class ConfigConnectionBottomSheet :
    OrbotBottomSheetDialogFragment() {

    private var callbacks: ConnectionHelperCallbacks? = null

    private lateinit var rbDirect: RadioButton
    private lateinit var rbSnowflake: RadioButton
    private lateinit var rbSnowflakeAmp: RadioButton
    private lateinit var rbSnowflakeSqs: RadioButton
    private lateinit var rbRequestBridge: RadioButton
    private lateinit var rbCustom: RadioButton

    private lateinit var btnAction: Button
    private lateinit var btnAskTor: Button

    companion object {
        fun newInstance(callbacks: ConnectionHelperCallbacks): ConfigConnectionBottomSheet {
            return ConfigConnectionBottomSheet().apply {
                this.callbacks = callbacks
            }
        }

        const val TAG = "ConfigConnectionBttmSheet"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.config_connection_bottom_sheet, container, false)

        rbDirect = v.findViewById(R.id.rbDirect)
        rbSnowflake = v.findViewById(R.id.rbSnowflake)
        rbSnowflakeAmp = v.findViewById(R.id.rbSnowflakeAmp)
        rbSnowflakeSqs = v.findViewById(R.id.rbSnowflakeSqs)
        rbRequestBridge = v.findViewById(R.id.rbRequest)
        rbCustom = v.findViewById(R.id.rbCustom)

        val tvDirectSubtitle = v.findViewById<View>(R.id.tvDirectSubtitle)
        val tvSnowflakeSubtitle = v.findViewById<View>(R.id.tvSnowflakeSubtitle)
        val tvSnowflakeAmpSubtitle = v.findViewById<View>(R.id.tvSnowflakeAmpSubtitle)
        val tvSnowflakeSqsSubtitle = v.findViewById<View>(R.id.tvSnowflakeSqsSubtitle)
        val tvRequestSubtitle = v.findViewById<View>(R.id.tvRequestSubtitle)
        val tvCustomSubtitle = v.findViewById<View>(R.id.tvCustomSubtitle)

        val radios = arrayListOf(
            rbDirect,
            rbSnowflake,
            rbSnowflakeAmp,
            rbSnowflakeSqs,
            rbRequestBridge,
            rbCustom
        )
        val radioSubtitleMap = mapOf<CompoundButton, View>(
            rbDirect to tvDirectSubtitle,
            rbSnowflake to tvSnowflakeSubtitle,
            rbSnowflakeAmp to tvSnowflakeAmpSubtitle,
            rbSnowflakeSqs to tvSnowflakeSqsSubtitle,
            rbRequestBridge to tvRequestSubtitle,
            rbCustom to tvCustomSubtitle
        )
        val allSubtitles = arrayListOf(
            tvDirectSubtitle,
            tvSnowflakeSubtitle,
            tvSnowflakeAmpSubtitle,
            tvSnowflakeSqsSubtitle,
            tvRequestSubtitle,
            tvCustomSubtitle
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
        v.findViewById<View>(R.id.snowflakeSqsContainer)
            .setOnClickListener { rbSnowflakeSqs.isChecked = true }
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
        rbSnowflakeSqs.setOnCheckedChangeListener { buttonView, isChecked ->
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
                        Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_OBFS4)
                        rbCustom.isChecked = true
                        closeAndConnect()
                    }
                }).show(requireActivity().supportFragmentManager, MoatBottomSheet.TAG)
            } else if (rbDirect.isChecked) {
                Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_DIRECT)
                closeAndConnect()
            } else if (rbSnowflake.isChecked) {
                Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_SNOWFLAKE)
                closeAndConnect()
            } else if (rbSnowflakeAmp.isChecked) {
                Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_SNOWFLAKE_AMP)
                closeAndConnect()
            } else if (rbSnowflakeSqs.isChecked) {
                Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_SNOWFLAKE_SQS)
                closeAndConnect()
            } else if (rbCustom.isChecked) {
                CustomBridgeBottomSheet(object : ConnectionHelperCallbacks {
                    override fun tryConnecting() {
                        Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_OBFS4)
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
        val pref = Prefs.getTorConnectionPathway()
        if (pref.equals(Prefs.CONNECTION_PATHWAY_OBFS4)) rbCustom.isChecked = true
        if (pref.equals(Prefs.CONNECTION_PATHWAY_SNOWFLAKE)) rbSnowflake.isChecked = true
        if (pref.equals(Prefs.CONNECTION_PATHWAY_SNOWFLAKE_AMP)) rbSnowflakeAmp.isChecked = true
        if (pref.equals(Prefs.CONNECTION_PATHWAY_SNOWFLAKE_SQS)) rbSnowflakeSqs.isChecked = true
        if (pref.equals(Prefs.CONNECTION_PATHWAY_DIRECT)) rbDirect.isChecked = true
    }

    private fun askTor() {

        val dLeft = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_faq)
        btnAskTor.text = getString(R.string.asking)
        btnAskTor.setCompoundDrawablesWithIntrinsicBounds(dLeft, null, null, null)

        val meekPort = Moat.startMeekForMoatApi(requireContext())

        val countryCodeValue: String = getDeviceCountryCode(requireContext())
        CircumventionApiManager(meekPort).getSettings(
            SettingsRequest(
                countryCodeValue
            ), {
                it?.let {
                    var circumventionApiBridges = it.settings
                    if (circumventionApiBridges == null) {
                        rbDirect.isChecked = true

                    } else { // got bridges
                        setPreferenceForSmartConnect(circumventionApiBridges)
                    }
                    Moat.stopMeekForMoatApi(requireContext())
                }
            }, {
                Log.wtf(TAG, "Couldn't hit circumvention API... $it")
                if (isVisible) {
                    Toast.makeText(
                        requireContext(),
                        R.string.error_asking_tor_for_bridges,
                        Toast.LENGTH_LONG
                    ).show()
                    Moat.stopMeekForMoatApi(requireContext())
                }
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

    private fun setPreferenceForSmartConnect(circumventionApiBridges: List<Bridges?>?) {
        if (isVisible) {
            val dLeft = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_green_check)
            btnAskTor.setCompoundDrawablesWithIntrinsicBounds(dLeft, null, null, null)
        }
        circumventionApiBridges?.let {
            if (it.isEmpty()) {
                if (isVisible) {
                    rbDirect.isChecked = true
                    btnAskTor.text = getString(R.string.connection_direct)
                }
                Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_DIRECT)
                return
            }
            val b = it[0]!!.bridges
            when (b.type) {
                CircumventionApiManager.BRIDGE_TYPE_SNOWFLAKE -> {
                    Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_SNOWFLAKE)
                    if (isVisible) {
                        rbSnowflake.isChecked = true
                        btnAskTor.text = getString(R.string.connection_snowflake)
                    }
                }

                CircumventionApiManager.BRIDGE_TYPE_OBFS4 -> {
                    if (isVisible) {
                        rbCustom.isChecked = true
                        btnAskTor.text = getString(R.string.connection_custom)
                    }
                    var bridgeStrings = ""
                    b.bridge_strings?.forEach { bridgeString ->
                        bridgeStrings += "$bridgeString\n"
                    }
                    Prefs.setBridgesList(bridgeStrings)
                    Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_OBFS4)
                }

                else -> {
                    if (isVisible) {
                        rbDirect.isChecked = true
                    }
                }
            }
        }
    }
}
