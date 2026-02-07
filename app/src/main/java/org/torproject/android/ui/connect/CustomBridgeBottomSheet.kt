package org.torproject.android.ui.connect

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.ScannerConfig
import org.torproject.android.R
import org.torproject.android.databinding.CustomBridgeBottomSheetBinding
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.circumvention.MoatApi
import org.torproject.android.service.circumvention.Transport
import org.torproject.android.util.Prefs
import org.torproject.android.ui.OrbotBottomSheetDialogFragment

class CustomBridgeBottomSheet() :
    OrbotBottomSheetDialogFragment() {

    companion object {
        const val TAG = "CustomBridgeBottomSheet"

        // https://regex101.com
        private val validBridgeRegex = Regex(
            """^"""
                    + """((meek_lite|obfs4|webtunnel|snowflake|dnstt)\s+)?""" // Optional bridge type: Currently supported PTs + vanilla bridges
                    + """((\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}|\[[\da-fA-F:]+])""" // Cheap IPv4 and IPv6 address test
                    + """(:\d{1,5})?)\s+""" // Optional port number, space after address
                    + """([\da-fA-F]{40})?""" // Optional bridge fingerprint
                    + """[ \t\f\w\-/+:=.,]*$""" // Optional bridge arguments (different per bridge type, subject to change)
        )

        fun isValidBridge(input: String): Boolean {
            return input.lines()
                .filter { it.isNotEmpty() && it.isNotBlank() }
                .all {
                    it.matches(validBridgeRegex)
                }
        }
    }

    private lateinit var binding: CustomBridgeBottomSheetBinding
    private lateinit var qrScanResultLauncher: ActivityResultLauncher<ScannerConfig>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        qrScanResultLauncher = registerForActivityResult(ScanCustomCode()) { result ->
            if (result is QRResult.QRSuccess) {
                val text = result.content.rawBytes?.let { String(it) }.orEmpty()
                var bridges = try {
                    MoatApi.json.decodeFromString(text)
                } catch (_: Throwable) {
                    emptyList<String>()
                }
                if (bridges.isNotEmpty()) {
                    val current =
                        binding.etBridges.text?.split("\n")?.toMutableList() ?: mutableListOf()

                    bridges = bridges.filter { !current.contains(it) }
                    if (bridges.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            R.string.you_already_have_these_bridges,
                            Toast.LENGTH_LONG
                        ).show()
                        return@registerForActivityResult
                    }

                    current.addAll(bridges)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.added_bridges_from_qr, bridges.size),
                        Toast.LENGTH_LONG
                    ).show()
                    binding.etBridges.setText(current.joinToString("\n"))
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.invalid_bridge_qr_code,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private var dialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = CustomBridgeBottomSheetBinding.inflate(inflater, container, false)

        val uri = OrbotConstants.GET_BRIDES_BRIDGES_URI.buildUpon()
        uri.path("/options")
        binding.tvCustomBridgeSubHeader.text =
            getString(R.string.custom_bridges_description, uri.build().toString())

        binding.btnScan.setOnClickListener {
            qrScanResultLauncher.launch(ScannerConfig.build {

            })
        }

        binding.tvCancel.setOnClickListener { dismiss() }

        binding.btnAction.setOnClickListener {
            Prefs.transport = Transport.CUSTOM
            Prefs.smartConnect = false
            Prefs.bridgesList = binding.etBridges.text?.split("\n") ?: emptyList()
            dismiss()
            val parent = requireActivity().supportFragmentManager.findFragmentByTag(
                ConfigConnectionBottomSheet.TAG
            ) as ConfigConnectionBottomSheet
            parent.closeAndConnect()
        }

        configureMultilineEditTextScrollEvent(binding.etBridges)

        val bridges = Prefs.bridgesList
            .filter { it.matches(validBridgeRegex) }
            .joinToString("\n")
        binding.etBridges.setText(bridges)

        binding.etBridges.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateUi()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        updateUi()
        return binding.root
    }

    override fun onPause() {
        dialog?.dismiss()

        super.onPause()
    }

    private fun updateUi() {
        val inputText = binding.etBridges.text.toString()
        val isValid = inputText.isNotEmpty() && isValidBridge(inputText)

        binding.btnAction.isEnabled = isValid
        binding.btnAction.backgroundTintList = ColorStateList.valueOf(
            if (isValid) {
                requireContext().getColor(R.color.orbot_btn_enabled_purple)
            } else {
                Color.DKGRAY
            }
        )

        if (!isValidBridge(inputText)) {
            binding.etBridges.error = requireContext().getString(R.string.invalid_bridge_format)
        } else {
            binding.etBridges.error = null
        }
    }
}
