package org.torproject.android.ui.connect

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.torproject.android.R
import org.torproject.android.databinding.ConfigConnectionBottomSheetBinding
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.circumvention.AutoConf
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.OrbotBottomSheetDialogFragment

class ConfigConnectionBottomSheet :
    OrbotBottomSheetDialogFragment(), CompoundButton.OnCheckedChangeListener {

    private var callbacks: ConnectionHelperCallbacks? = null

    private lateinit var binding: ConfigConnectionBottomSheetBinding

    private lateinit var radios: List<RadioButton>
    private lateinit var radioSubtitleMap: Map<CompoundButton, View>
    private lateinit var allSubtitles: List<View>

    companion object {
        fun newInstance(callbacks: ConnectionHelperCallbacks): ConfigConnectionBottomSheet {
            return ConfigConnectionBottomSheet().apply {
                this.callbacks = callbacks
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = ConfigConnectionBottomSheetBinding.inflate(inflater, container, false)

        radios = arrayListOf(
            binding.rbDirect,
            binding.rbSnowflake,
            binding.rbSnowflakeAmp,
            binding.rbSnowflakeSqs,
            binding.rbTelegram,
            binding.rbObfs4,
            binding.rbEmail,
            binding.rbMeek,
            binding.rbCustom
        )
        radioSubtitleMap = mapOf<CompoundButton, View>(
            binding.rbDirect to binding.tvDirectSubtitle,
            binding.rbSnowflake to binding.tvSnowflakeSubtitle,
            binding.rbSnowflakeAmp to binding.tvSnowflakeAmpSubtitle,
            binding.rbSnowflakeSqs to binding.tvSnowflakeSqsSubtitle,
            binding.rbTelegram to binding.tvTelegramSubtitle,
            binding.rbObfs4 to binding.tvObfs4Subtitle,
            binding.rbEmail to binding.tvEmailSubtitle,
            binding.rbMeek to binding.tvMeekSubtitle,
            binding.rbCustom to binding.tvCustomSubtitle
        )
        allSubtitles = arrayListOf(
            binding.tvDirectSubtitle,
            binding.tvSnowflakeSubtitle,
            binding.tvSnowflakeAmpSubtitle,
            binding.tvSnowflakeSqsSubtitle,
            binding.tvTelegramSubtitle,
            binding.tvObfs4Subtitle,
            binding.tvEmailSubtitle,
            binding.tvMeekSubtitle,
            binding.tvCustomSubtitle
        )

        binding.btnAskTor.setOnClickListener {
            askTor()
        }

        // setup containers so radio buttons can be checked if labels are clicked on
        binding.directContainer.setOnClickListener { binding.rbDirect.isChecked = true }
        binding.snowflakeContainer.setOnClickListener { binding.rbSnowflake.isChecked = true }
        binding.snowflakeAmpContainer.setOnClickListener { binding.rbSnowflakeAmp.isChecked = true }
        binding.snowflakeSqsContainer.setOnClickListener { binding.rbSnowflakeSqs.isChecked = true }
        binding.telegramContainer.setOnClickListener { binding.rbTelegram.isChecked = true }
        binding.obfs4Container.setOnClickListener { binding.rbObfs4.isChecked = true }
        binding.emailContainer.setOnClickListener { binding.rbEmail.isChecked = true }
        binding.meekContainer.setOnClickListener { binding.rbMeek.isChecked = true }
        binding.customContainer.setOnClickListener { binding.rbCustom.isChecked = true }
        binding.tvCancel.setOnClickListener { dismiss() }

        binding.rbDirect.setOnCheckedChangeListener(this)
        binding.rbSnowflake.setOnCheckedChangeListener(this)
        binding.rbSnowflakeAmp.setOnCheckedChangeListener(this)
        binding.rbSnowflakeSqs.setOnCheckedChangeListener(this)
        binding.rbTelegram.setOnCheckedChangeListener(this)
        binding.rbObfs4.setOnCheckedChangeListener(this)
        binding.rbEmail.setOnCheckedChangeListener(this)
        binding.rbMeek.setOnCheckedChangeListener(this)
        binding.rbCustom.setOnCheckedChangeListener(this)

        binding.tvTelegramSubtitle.text = getString(R.string.bridges_via_telegram_subtitle, "start")

        selectRadioButtonFromPreference()

        binding.btnAction.setOnClickListener {
            if (binding.rbObfs4.isChecked) {
                Prefs.transport = Transport.OBFS4
                Prefs.smartConnect = false
                closeAndConnect()
            } else if (binding.rbDirect.isChecked) {
                Prefs.transport = Transport.NONE
                Prefs.smartConnect = false
                closeAndConnect()
            } else if (binding.rbSnowflake.isChecked) {
                Prefs.transport = Transport.SNOWFLAKE
                Prefs.smartConnect = false
                closeAndConnect()
            } else if (binding.rbSnowflakeAmp.isChecked) {
                Prefs.transport = Transport.SNOWFLAKE_AMP
                Prefs.smartConnect = false
                closeAndConnect()
            } else if (binding.rbSnowflakeSqs.isChecked) {
                Prefs.transport = Transport.SNOWFLAKE_SQS
                Prefs.smartConnect = false
                closeAndConnect()
            } else if (binding.rbTelegram.isChecked) {
                val i = Intent(Intent.ACTION_VIEW, OrbotConstants.GET_BRIDES_TELEGRAM_BOT)
                startActivity(i)
            } else if (binding.rbEmail.isChecked) {
                val i = Intent(Intent.ACTION_SENDTO)
                i.data = "mailto:${OrbotConstants.GET_BRIDES_EMAIL_RECIPIENT}".toUri()
                i.putExtra(Intent.EXTRA_SUBJECT, OrbotConstants.GET_BRIDES_EMAIL_SUBJECT_AND_BODY)
                i.putExtra(Intent.EXTRA_TEXT, OrbotConstants.GET_BRIDES_EMAIL_SUBJECT_AND_BODY)

                val pm = activity?.packageManager ?: return@setOnClickListener

                if (i.resolveActivity(pm) != null) {
                    startActivity(i)
                }
            }
            else if (binding.rbMeek.isChecked) {
                Prefs.transport = Transport.MEEK_AZURE
                Prefs.smartConnect = false
                closeAndConnect()
            }

            if (binding.rbTelegram.isChecked || binding.rbEmail.isChecked || binding.rbCustom.isChecked) {
                CustomBridgeBottomSheet(object : ConnectionHelperCallbacks {
                    override fun tryConnecting() {
                        Prefs.transport = Transport.CUSTOM
                        Prefs.smartConnect = false
                        closeAndConnect()
                    }
                }).show(requireActivity().supportFragmentManager, CustomBridgeBottomSheet.TAG)
            }
        }

        return binding.root
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        if (isChecked) {
            for (radio in radios) {
                if (radio != buttonView) radio.isChecked = false
            }

            radioSubtitleMap[buttonView]?.let {
                for (subtitle in allSubtitles) {
                    subtitle.visibility = if (subtitle == it) View.VISIBLE else View.GONE
                }
            }
        }

        binding.btnAction.text = when (buttonView) {
            binding.rbTelegram, binding.rbEmail, binding.rbCustom -> getString(R.string.next)
            else -> getString(R.string.connect)
        }
    }

    private fun closeAndConnect() {
        closeAllSheets()
        callbacks?.tryConnecting()
    }

    private fun selectRadioButtonFromPreference() {
        when (Prefs.transport) {
            Transport.NONE -> binding.rbDirect.isChecked = true
            Transport.MEEK_AZURE -> binding.rbMeek.isChecked = true
            Transport.OBFS4 -> binding.rbObfs4.isChecked = true
            Transport.SNOWFLAKE -> binding.rbSnowflake.isChecked = true
            Transport.SNOWFLAKE_AMP -> binding.rbSnowflakeAmp.isChecked = true
            Transport.SNOWFLAKE_SQS -> binding.rbSnowflakeSqs.isChecked = true
            Transport.WEBTUNNEL -> TODO() // This should currently not happen, there's no default Webtunnel bridges advertised, yet.
            Transport.CUSTOM -> binding.rbCustom.isChecked = true
        }
    }

    private fun askTor() {
        updateAskTorBt(getString(R.string.asking), R.drawable.ic_faq)

        lifecycleScope.launch(Dispatchers.IO) {
            val context = context ?: return@launch

            try {
                val conf = AutoConf.`do`(context, cannotConnectWithoutPt = true)

                withContext(Dispatchers.Main) {
                    if (conf == null) {
                        updateAskTorBt()

                        Toast.makeText(context, R.string.error_asking_tor_for_bridges, Toast.LENGTH_LONG)
                            .show()

                        return@withContext
                    }

                    updateAskTorBt(conf.first.toString(), R.drawable.ic_green_check)

                    Prefs.transport = conf.first
                    Prefs.smartConnect = false

                    val customBridges = Prefs.bridgesList?.split("\n")?.toMutableSet() ?: mutableSetOf()
                    customBridges.addAll(conf.second)
                    Prefs.bridgesList = customBridges.joinToString("\n")

                    when (conf.first) {
                        Transport.NONE -> {
                            binding.rbDirect.isChecked = true
                        }
                        Transport.MEEK_AZURE -> {
                            binding.rbMeek.isChecked = true
                        }
                        Transport.OBFS4 -> {
                            binding.rbObfs4.isChecked = true
                        }
                        Transport.SNOWFLAKE -> {
                            binding.rbSnowflake.isChecked = true
                        }
                        Transport.SNOWFLAKE_AMP -> {
                            binding.rbSnowflakeAmp.isChecked = true
                        }
                        Transport.SNOWFLAKE_SQS -> {
                            binding.rbSnowflakeSqs.isChecked = true
                        }
                        Transport.WEBTUNNEL -> TODO() // This should currently not happen, there's no default Webtunnel bridges advertised, yet.
                        Transport.CUSTOM -> {
                            binding.rbCustom.isChecked = true
                        }
                    }

                    delay(5 * 1000)
                    updateAskTorBt()
                }
            }
            catch(e: Throwable) {
                withContext(Dispatchers.Main) {
                    updateAskTorBt()

                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun updateAskTorBt(text: CharSequence = getString(R.string.ask_tor), drawableId: Int? = null) {
        val context = context ?: return

        if (drawableId != null) {
            val image = AppCompatResources.getDrawable(context, drawableId)
            binding.btnAskTor.setCompoundDrawablesWithIntrinsicBounds(image, null, null, null)
        }
        else {
            binding.btnAskTor.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        }

        binding.btnAskTor.text = text
    }
}
