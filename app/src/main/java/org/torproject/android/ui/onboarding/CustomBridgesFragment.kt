package org.torproject.android.ui.onboarding

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView

import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment

import com.google.zxing.integration.android.IntentIntegrator

import net.freehaven.tor.control.TorControlCommands

import org.json.JSONArray
import org.torproject.android.R
import org.torproject.android.core.ClipboardUtils.copyToClipboard
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class CustomBridgesFragment : Fragment(), TextWatcher {
    private var mEtPastedBridges: EditText? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_custom_bridges, container, false)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { activity?.onBackPressed() }

        view.findViewById<TextView>(R.id.tvDescription).text = getString(R.string.in_a_browser, URL_TOR_BRIDGES)

        view.findViewById<View>(R.id.btCopyUrl).setOnClickListener {
            copyToClipboard("bridge_url", URL_TOR_BRIDGES, getString(R.string.done), requireContext())
        }

        var bridges: String? = Prefs.getBridgesList().trim { it <= ' ' }
        if (!Prefs.bridgesEnabled() || userHasSetPreconfiguredBridge(bridges)) {
            bridges = null
        }

        mEtPastedBridges = view.findViewById(R.id.etPastedBridges)
        mEtPastedBridges?.setOnTouchListener { v, event ->
            if (v.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_SCROLL) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    return@setOnTouchListener true
                }
            }
            false
        }

        mEtPastedBridges?.setText(bridges)
        mEtPastedBridges?.addTextChangedListener(this)
        val integrator = IntentIntegrator(requireActivity())

        view.findViewById<View>(R.id.btScanQr).setOnClickListener { integrator.initiateScan() }
        view.findViewById<View>(R.id.btShareQr).setOnClickListener {
            Prefs.getBridgesList().takeIf { it.isNotEmpty() }?.let { setBridges ->
                try {
                    integrator.shareText("bridge://${URLEncoder.encode(setBridges, "UTF-8")}")
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            }
        }
        view.findViewById<View>(R.id.btEmail).setOnClickListener {
            val requestText = "get transport"
            val emailUrl = "mailto:${Uri.encode(EMAIL_TOR_BRIDGES)}?subject=${Uri.encode(requestText)}&body=${Uri.encode(requestText)}"
            val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(emailUrl)).apply {
                putExtra(Intent.EXTRA_SUBJECT, requestText)
                putExtra(Intent.EXTRA_TEXT, requestText)
            }
            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email)))
        }

        return view
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            activity?.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(request: Int, response: Int, data: Intent?) {
        super.onActivityResult(request, response, data)

        val scanResult = IntentIntegrator.parseActivityResult(request, response, data)
        scanResult?.contents?.let { results ->
            if (results.isNotEmpty()) {
                try {
                    val urlIdx = results.indexOf("://")
                    val decodedResults = if (urlIdx != -1) {
                        URLDecoder.decode(results, DEFAULT_ENCODING).substring(urlIdx + 3)
                    } else {
                        JSONArray(results).let { bridgeJson ->
                            StringBuilder().apply {
                                for (i in 0 until bridgeJson.length()) {
                                    append(bridgeJson.getString(i)).append("\n")
                                }
                            }.toString()
                        }
                    }
                    setNewBridges(decodedResults)
                } catch (e: Exception) {
                    Log.e(javaClass.simpleName, "unsupported", e)
                }
            }
            activity?.setResult(Activity.RESULT_OK)
        }
    }

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) { /* no-op */ }

    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) { /* no-op */ }

    override fun afterTextChanged(editable: Editable) {
        setNewBridges(editable.toString(), false)
    }

    private fun setNewBridges(bridges: String?, updateEditText: Boolean = true) {
        val trimmedBridges = bridges?.trim()?.takeIf { it.isNotEmpty() }

        if (updateEditText) {
            mEtPastedBridges?.setText(trimmedBridges)
        }

        Prefs.setBridgesList(trimmedBridges)
        Prefs.putBridgesEnabled(trimmedBridges != null)

        Intent(requireContext(), OrbotService::class.java).apply {
            action = TorControlCommands.SIGNAL_RELOAD
            requireContext().startService(this)
        }
    }

    companion object {
        private val DEFAULT_ENCODING = StandardCharsets.UTF_8.name()
        private const val EMAIL_TOR_BRIDGES = "bridges@torproject.org"
        private const val URL_TOR_BRIDGES = "https://bridges.torproject.org/bridges"

        private fun userHasSetPreconfiguredBridge(bridges: String?): Boolean {
            return bridges in listOf("obfs4", "meek", "snowflake", "snowflake-amp")
        }
    }
}
