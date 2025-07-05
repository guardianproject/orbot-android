package org.torproject.android.ui.connect

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.torproject.android.R
import org.torproject.android.databinding.CustomBridgeBottomSheetBinding
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.OrbotBottomSheetDialogFragment

class CustomBridgeBottomSheet(private val callbacks: ConnectionHelperCallbacks) :
    OrbotBottomSheetDialogFragment() {

    companion object {
        const val TAG = "CustomBridgeBottomSheet"
        private val bridgeStatement = Regex("(obfs4|meek|webtunnel)")
        private val obfs4Regex = Regex("""^obfs4\s+(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}|\[[0-9a-fA-F:]+]):\d+\s+[A-F0-9]{40}(\s+cert=[a-zA-Z0-9+/=]+)?(\s+iat-mode=\d+)?$""")
        private val webtunnelRegex = Regex("""^webtunnel\s+(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}|\[[0-9a-fA-F:]+]):\d+\s+[A-F0-9]{40}(\s+url=https?://\S+)?(\s+ver=\d+\.\d+\.\d+)?$""")

        fun isValidBridge(input: String): Boolean {
            return input.lines()
                .filter { it.isNotEmpty() && it.isNotBlank() }
                .all { it.matches(obfs4Regex) || it.matches(webtunnelRegex) }
        }
    }

    private lateinit var binding: CustomBridgeBottomSheetBinding


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = CustomBridgeBottomSheetBinding.inflate(inflater, container, false)

        val uri = OrbotConstants.GET_BRIDES_BRIDGES_URI.buildUpon()
        uri.path("/options")
        binding.tvCustomBridgeSubHeader.text = getString(R.string.custom_bridges_description, uri.build().toString())

        binding.tvCancel.setOnClickListener { dismiss() }

        binding.btnAction.setOnClickListener {
            Prefs.bridgesList = binding.etBridges.text.toString()
            closeAllSheets()
            callbacks.tryConnecting()
        }

        configureMultilineEditTextScrollEvent(binding.etBridges)

        var bridges = Prefs.bridgesList ?: ""
        if (!bridges.contains(bridgeStatement)) bridges = ""
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

    private fun updateUi() {
        val inputText = binding.etBridges.text.toString()
        binding.btnAction.isEnabled = inputText.isNotEmpty() && isValidBridge(inputText)

        if (!isValidBridge(inputText)) {
            binding.etBridges.error = requireContext().getString(R.string.invalid_bridge_format)
        } else {
            binding.etBridges.error = null
        }
    }
}
