package org.torproject.android.ui.more

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import org.torproject.android.databinding.DonateBottomSheetBinding
import org.torproject.android.ui.OrbotBottomSheetDialogFragment
import org.torproject.android.ui.more.tipjar.TipJar

class DonateBottomSheet : OrbotBottomSheetDialogFragment(minMode = true) {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = DonateBottomSheetBinding.inflate(inflater, container, false)
        binding.btnDonate.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, DONATE_URL.toUri()))
            } catch (_: ActivityNotFoundException) {
                // no browser installed; nothing to do
            }
            dismiss()
        }
        // The Tip Jar uses Google Play Billing, so it only exists in the "play" flavor
        binding.btnTipJar.isVisible = TipJar.isAvailable
        binding.btnTipJar.setOnClickListener {
            val fm = parentFragmentManager
            dismiss()
            TipJar.show(fm)
        }
        return binding.root
    }

    companion object {
        const val TAG = "DonateBottomSheet"
        const val DONATE_URL = "https://donate.torproject.org/?source=orbot"

        fun show(fragmentManager: FragmentManager) =
            DonateBottomSheet().show(fragmentManager, TAG)
    }
}
