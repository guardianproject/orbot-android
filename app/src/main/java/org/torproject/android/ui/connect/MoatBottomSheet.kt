package org.torproject.android.ui.connect

import IPtProxy.Controller
import IPtProxy.IPtProxy.MeekLite
import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.android.volley.DefaultRetryPolicy
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject
import org.torproject.android.R
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.components.OrbotBottomSheetDialogFragment
import org.torproject.android.circumvention.ProxiedHurlStack
import java.io.File


class MoatBottomSheet(private val callbacks: ConnectionHelperCallbacks) :
    OrbotBottomSheetDialogFragment(), View.OnClickListener {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.moat_bottom_sheet, container, false)
        mProgressBar = v.findViewById(R.id.progressBar)
        ivCaptcha = v.findViewById(R.id.ivCaptcha)
        etSolution = v.findViewById(R.id.solutionEt)
        etSolution.setOnEditorActionListener { textView, _, keyEvent ->
            // handle pressing of enter key TODO this isn't working properly
            if (keyEvent != null && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (keyEvent.action == KeyEvent.ACTION_UP) {
                    val imm =
                        requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
                    imm?.hideSoftInputFromWindow(textView.windowToken, 0)
                    onClick(textView)
                }
                true
            }
            false
        }
        mBtnAction = v.findViewById(R.id.btnAction)
        v.findViewById<View>(R.id.tvCancel).setOnClickListener { dismiss() }
        mBtnAction.setOnClickListener(this)
        setupMoat()
        return v
    }

    private var mChallenge: String? = null
    private var mCaptcha: ByteArray? = null
    private var mRequestInProgress = false
    private lateinit var etSolution: EditText
    private lateinit var mProgressBar: ProgressBar
    private lateinit var ivCaptcha: ImageView
    private lateinit var mQueue: RequestQueue
    private lateinit var mBtnAction: Button

    private lateinit var mController : Controller

    private fun setupMoat() {
        val fileCacheDir = File(requireActivity().cacheDir, "pt")
        if (!fileCacheDir.exists()) {
            fileCacheDir.mkdir()
        }

        // val isDebuggable = (0 != (context?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE)))
        mController = OrbotService.getIptProxyController(context)
        mController.start(MeekLite,"")

        val phs = ProxiedHurlStack(
            "127.0.0.1",
            mController.port(MeekLite).toInt(),
            "url=" + OrbotService.getCdnFront("moat-url") + ";front=" + OrbotService.getCdnFront("moat-front"),
            "\u0000"
        )

        mQueue = Volley.newRequestQueue(requireActivity(), phs)
        if (mCaptcha == null) {
            Handler(Looper.getMainLooper()).postDelayed({ fetchCaptcha() }, 50)
        }

    }

    private fun fetchCaptcha() {
        val request = buildRequest(
            ENDPOINT_FETCH, "\"type\": \"client-transports\", \"supported\": [\"obfs4\"]"
        ) { response: JSONObject ->
            mRequestInProgress = false
            mProgressBar.visibility = View.GONE
            try {
                val data = response.getJSONArray("data").getJSONObject(0)
                mChallenge = data.getString("challenge")
                mCaptcha = Base64.decode(data.getString("image"), Base64.DEFAULT)
                ivCaptcha.setImageBitmap(
                    BitmapFactory.decodeByteArray(
                        mCaptcha, 0, mCaptcha!!.size
                    )
                )
                ivCaptcha.visibility = View.VISIBLE
                etSolution.text = null
                etSolution.isEnabled = true
                mBtnAction.isEnabled = true
            } catch (e: JSONException) {
                Log.d(TAG, "Error decoding answer: $response")
                displayError(e)
            }
        }

        if (request != null) {
            mRequestInProgress = true
            mProgressBar.visibility = View.VISIBLE
            ivCaptcha.visibility = View.GONE
            mBtnAction.isEnabled = false
            mChallenge = null
            mCaptcha = null
            mQueue.add(request)
        }
    }

    private fun requestBridges(solution: String) {
        val request = buildRequest(
            ENDPOINT_CHECK,
            "\"id\": \"2\", \"type\": \"moat-solution\", \"transport\": \"obfs4\", \"challenge\": \"$mChallenge\", \"solution\": \"$solution\", \"qrcode\": \"false\""
        ) { response: JSONObject ->
            mRequestInProgress = false
            mProgressBar.visibility = View.GONE
            try {
                val bridges = response.getJSONArray("data").getJSONObject(0).getJSONArray("bridges")
                Log.d(TAG, "Bridges: $bridges")
                val sb = StringBuilder()
                for (i in 0 until bridges.length()) {
                    sb.append(bridges.getString(i)).append("\n")
                }
                onBridgeRequestSuccess(sb.toString())
            } catch (e: JSONException) {
                Log.d(TAG, "Error decoding answer: $response")
                displayError(e)
                onBridgeRequestFailed()
            }
        }
        if (request != null) {
            mRequestInProgress = true
            mProgressBar.visibility = View.VISIBLE
            etSolution.isEnabled = false
            mBtnAction.isEnabled = false
            mQueue.add(request)
        }
    }

    private fun buildRequest(
        endpoint: String, payload: String, listener: Response.Listener<JSONObject>
    ): JsonObjectRequest? {
        val requestBody: JSONObject = try {
            JSONObject("{\"data\": [{\"version\": \"0.1.0\", $payload}]}")
        } catch (e: JSONException) {
            return null
        }
        Log.d(TAG, "Request: $requestBody")
        val request: JsonObjectRequest = object : JsonObjectRequest(Method.POST,
            "$MOAT_BASE/$endpoint",
            requestBody,
            listener,
            Response.ErrorListener { error: VolleyError ->
                mRequestInProgress = false
                mProgressBar.visibility = View.GONE
                Log.d(TAG, "Error response.")
                error.printStackTrace()
                displayError(error)
            }) {
            override fun getBodyContentType(): String {
                return "application/vnd.api+json"
            }
        }
        request.retryPolicy = DefaultRetryPolicy(
            30000, 3, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        return request
    }

    private fun displayError(exception: Exception) {
        Log.d("MoatBottomSheet", "DISPLAY ERROR: $exception")
    }

    private fun onBridgeRequestSuccess(bridges: String) {
        Prefs.setTorConnectionPathway(Prefs.CONNECTION_PATHWAY_OBFS4)
        Prefs.setBridgesList(bridges)
        closeAllSheets()
        callbacks.tryConnecting()
    }

    private fun onBridgeRequestFailed() {
        etSolution.text.clear()
        etSolution.isEnabled = true
        mBtnAction.isEnabled = true
        Toast.makeText(requireContext(), R.string.incorrect_solution, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val TAG = "MoatBottomSheet"
        private const val MOAT_BASE = "https://bridges.torproject.org/moat"
        private const val ENDPOINT_FETCH = "fetch"
        private const val ENDPOINT_CHECK = "check"
    }

    override fun onClick(v: View?) {
        requestBridges(etSolution.text.trim().toString())
    }
}
