package org.torproject.android.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.torproject.android.R
import org.torproject.android.service.util.showToast
import org.torproject.android.ui.OrbotBottomSheetDialogFragment

class LogBottomSheet : OrbotBottomSheetDialogFragment() {

    private lateinit var tvLog: TextView
    private var buffer = StringBuffer()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.log_bottom_sheet, container, false)
        tvLog = v.findViewById(R.id.orbotLog)
        tvLog.text = buffer.toString()

        v.findViewById<FloatingActionButton>(R.id.btnCopyLog).setOnClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("log", tvLog.text)
            clipboard.setPrimaryClip(clip)
            v.context.showToast(R.string.log_copied)
        }
        return v
    }

    fun appendLog(logLine: String) {
        if (this::tvLog.isInitialized) {
            tvLog.append("$logLine\n")
        }
        buffer.append("$logLine\n")
    }

}