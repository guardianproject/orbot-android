package org.torproject.android.ui.kindness

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.window.layout.WindowMetricsCalculator
import org.torproject.android.R
import org.torproject.android.databinding.FragmentTestingBinding

class TestingDialogFragment : DialogFragment() {

    private lateinit var mBinding: FragmentTestingBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mBinding = FragmentTestingBinding.inflate(inflater, container, false)

        mBinding.tvTitleApproved.text = getString(R.string.testing_title_approved, "✅")
        mBinding.tvTitleDeclined.text = getString(R.string.testing_title_declined, "\uD83D\uDEAB")

        mBinding.boxTesting.visibility = View.VISIBLE
        mBinding.boxApproved.visibility = View.GONE
        mBinding.boxDeclined.visibility = View.GONE

        mBinding.btContinue.setOnClickListener {
            val bundle = Bundle()
            bundle.putBoolean(KEY_RESULT, true)

            setFragmentResult(KEY_RESULT, bundle)
            dismiss()
        }

        mBinding.btOk.setOnClickListener {
            setFragmentResult(KEY_RESULT, Bundle())
            dismiss()
        }

        return mBinding.root
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.let { window ->
            val metrics = WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(requireActivity())
            val width = metrics.bounds.width()
            val height = metrics.bounds.height()

            val dialogHeight = if (width > height) (height * 0.9f).toInt() else (height * 0.33f).toInt()

            val dialogWidth = if (width > height) (width * 0.33f).toInt() else (width * 0.9f).toInt()

            window.setLayout(dialogWidth, dialogHeight)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }

        // TODO: Add actual test:
        // - If connected to Tor without a bridge right now, we're fine: dismiss immediately like `btContinue`.
        // - If last test success timestamp is younger than 1 day, dismiss immediately like `btContinue`.
        // - Else, set bridge to `NONE`, start Tor, wait until success or timeout.
        // - Stop Tor again, after test and restore original settings.
        // - Store timestamp of success in `Prefs`.
    }

    companion object {
        const val KEY_RESULT = "kindness_test_result"

        fun show(fragmentManager: FragmentManager) {
            TestingDialogFragment().show(fragmentManager, "TestingFragment")
        }
    }
}
