package org.torproject.android.ui.more

import IPtProxy.IPtProxy
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import org.torproject.android.R
import org.torproject.android.util.DiskUtils
import org.torproject.jni.BuildConfig.VERSION_NAME
import org.torproject.jni.TorService
import java.io.IOException

class AboutDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "AboutDialogFragment"
        const val VERSION: String = VERSION_NAME
        private const val BUNDLE_KEY_TV_ABOUT_TEXT = "about_tv_txt"
    }

    private lateinit var tvAbout: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view: View? = activity?.layoutInflater?.inflate(R.layout.layout_about, null)

        val versionName = view?.findViewById<TextView>(R.id.versionName)
        versionName?.text = VERSION

        tvAbout = view?.findViewById(R.id.aboutother)!!

        val tvTor = view.findViewById<TextView>(R.id.tvTor)
        tvTor.text = getString(R.string.tor_url, TorService.VERSION_NAME)

        val tvObfs4 = view.findViewById<TextView>(R.id.tvObfs4)
        tvObfs4.text = getString(R.string.obfs4_url, IPtProxy.lyrebirdVersion().substringAfter('-'))

        val tvSnowflake = view.findViewById<TextView>(R.id.tvSnowflake)
        tvSnowflake.text = getString(R.string.snowflake_url, IPtProxy.snowflakeVersion())

        var buildAboutText = true

        savedInstanceState?.getString(BUNDLE_KEY_TV_ABOUT_TEXT)?.let {
            buildAboutText = false
            tvAbout.text = it
        }

        if (buildAboutText) {
            try {
                val equalsBlockRegex = Regex("={3,}")
                var aboutText = DiskUtils.readFileFromAssets("LICENSE", requireContext())
                aboutText = aboutText.replace(equalsBlockRegex, "")

                val spannableAboutText = SpannableStringBuilder(aboutText)
                spannableAboutText.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    aboutText.indexOf("\n"),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                tvAbout.text = spannableAboutText
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return AlertDialog.Builder(context, R.style.OrbotDialogTheme)
            .setTitle(getString(R.string.menu_about))
            .setView(view)
            .create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(BUNDLE_KEY_TV_ABOUT_TEXT, tvAbout.text.toString())
    }
}