package org.torproject.android.ui.more.tipjar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.torproject.android.R
import org.torproject.android.databinding.TipJarBottomSheetBinding
import org.torproject.android.ui.OrbotBottomSheetDialogFragment

/**
 * Orbot Tip Jar: one-time, consumable in-app products sold through Google Play Billing.
 * Tips are consumed right after purchase so people can tip more than once.
 *
 * The product IDs below must exist as active one-time products in the Play Console.
 */
class TipJarBottomSheet : OrbotBottomSheetDialogFragment(minMode = true), PurchasesUpdatedListener {

    private data class Tip(val productId: String, @StringRes val labelRes: Int)

    private var binding: TipJarBottomSheetBinding? = null
    private var billingClient: BillingClient? = null
    private val productDetails = mutableMapOf<String, ProductDetails>()

    private val tips = listOf(
        Tip(PRODUCT_ONION_RING, R.string.tip_onion_ring),
        Tip(PRODUCT_ONION_SOUP, R.string.tip_onion_soup),
        Tip(PRODUCT_ONION_ORCHARD, R.string.tip_onion_orchard),
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val b = TipJarBottomSheetBinding.inflate(inflater, container, false)
        binding = b
        tips.forEach { tip ->
            buttonFor(tip.productId, b).apply {
                setText(tip.labelRes)
                isEnabled = false
                setOnClickListener { launchPurchase(tip.productId) }
            }
        }
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val client = BillingClient.newBuilder(requireContext())
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        billingClient = client
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    queryProducts()
                    consumeOutstandingPurchases()
                } else {
                    onUi { showUnavailable() }
                }
            }

            override fun onBillingServiceDisconnected() {
                onUi { showUnavailable() }
            }
        })
    }

    override fun onDestroyView() {
        billingClient?.endConnection()
        billingClient = null
        binding = null
        super.onDestroyView()
    }

    private fun buttonFor(productId: String, b: TipJarBottomSheetBinding): Button =
        when (productId) {
            PRODUCT_ONION_RING -> b.btnTipOnionRing
            PRODUCT_ONION_SOUP -> b.btnTipOnionSoup
            else -> b.btnTipOnionOrchard
        }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(tips.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it.productId)
                    .setProductType(ProductType.INAPP)
                    .build()
            })
            .build()
        billingClient?.queryProductDetailsAsync(params) { billingResult, result ->
            onUi {
                val b = binding ?: return@onUi
                if (billingResult.responseCode != BillingResponseCode.OK) {
                    showUnavailable()
                    return@onUi
                }
                result.productDetailsList.forEach { productDetails[it.productId] = it }
                if (productDetails.isEmpty()) {
                    showUnavailable()
                    return@onUi
                }
                b.progressTipJar.isVisible = false
                tips.forEach { tip ->
                    val details = productDetails[tip.productId]
                    val price = details?.oneTimePurchaseOfferDetails?.formattedPrice
                    buttonFor(tip.productId, b).apply {
                        isVisible = details != null
                        isEnabled = details != null
                        text = if (price != null)
                            getString(R.string.tip_price_format, price, getString(tip.labelRes))
                        else getString(tip.labelRes)
                    }
                }
            }
        }
    }

    private fun launchPurchase(productId: String) {
        val details = productDetails[productId] ?: return
        val client = billingClient ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(requireActivity(), flowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it, notify = true) }
            BillingResponseCode.USER_CANCELED -> Unit
            else -> toast(R.string.tip_failed)
        }
    }

    private fun handlePurchase(purchase: Purchase, notify: Boolean) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> consume(purchase, notify)
            Purchase.PurchaseState.PENDING -> if (notify) toast(R.string.tip_pending)
            else -> Unit
        }
    }

    /** Tips are consumable: consuming also acknowledges them, so Play won't auto-refund. */
    private fun consume(purchase: Purchase, notify: Boolean) {
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient?.consumeAsync(params) { result, _ ->
            if (notify && result.responseCode == BillingResponseCode.OK) {
                toast(R.string.tip_thank_you)
            }
        }
    }

    /** Finish any tips that completed while the Tip Jar was closed (e.g. pending payments). */
    private fun consumeOutstandingPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        billingClient?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it, notify = false) }
            }
        }
    }

    private fun showUnavailable() {
        val b = binding ?: return
        b.progressTipJar.isVisible = false
        b.tvTipJarStatus.isVisible = true
        b.tvTipJarStatus.setText(R.string.tip_unavailable)
        tips.forEach { buttonFor(it.productId, b).isEnabled = false }
    }

    /** Billing callbacks may arrive off the main thread. */
    private fun onUi(block: () -> Unit) {
        activity?.runOnUiThread { if (binding != null) block() }
    }

    private fun toast(@StringRes res: Int) {
        val ctx = context?.applicationContext ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(ctx, res, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val TAG = "TipJarBottomSheet"

        const val PRODUCT_ONION_RING = "tip_onion_ring"
        const val PRODUCT_ONION_SOUP = "tip_onion_soup"
        const val PRODUCT_ONION_ORCHARD = "tip_onion_orchard"

        fun show(fragmentManager: FragmentManager) =
            TipJarBottomSheet().show(fragmentManager, TAG)
    }
}
