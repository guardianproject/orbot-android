package org.torproject.android.ui.more

import IPtProxy.IPtProxy
import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.torproject.android.R
import org.torproject.android.databinding.LayoutAboutBinding
import org.torproject.android.util.DiskUtils
import org.torproject.android.util.createWithCurves
import org.torproject.jni.BuildConfig.VERSION_NAME
import org.torproject.jni.TorService
import java.io.IOException

class AboutDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "AboutDialogFragment"
        const val VERSION: String = VERSION_NAME
        private const val BUNDLE_KEY_TV_ABOUT_TEXT = "about_tv_txt"
    }

    private lateinit var binding: LayoutAboutBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = LayoutAboutBinding.inflate(layoutInflater)
        val versionName = view?.findViewById<TextView>(R.id.versionName)
        versionName?.text = VERSION

        binding.tvTor.text = getString(R.string.tor_url, TorService.VERSION_NAME)
        binding.tvObfs4.text =
            getString(R.string.obfs4_url, IPtProxy.lyrebirdVersion().substringAfter('-'))
        binding.tvSnowflake.text = getString(R.string.snowflake_url, IPtProxy.snowflakeVersion())

        var buildAboutText = true

        savedInstanceState?.getString(BUNDLE_KEY_TV_ABOUT_TEXT)?.let {
            buildAboutText = false
            binding.aboutother.text = it
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

                binding.aboutother.text = spannableAboutText
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return AlertDialog.Builder(requireContext(), R.style.OrbotDialogTheme)
            .setTitle(getString(R.string.menu_about))
            .setView(binding.root)
            .createWithCurves()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(BUNDLE_KEY_TV_ABOUT_TEXT, binding.aboutother.text.toString())
    }
}