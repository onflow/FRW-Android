package com.flowfoundation.wallet.page.wallet.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.databinding.DialogSwapCoinListBinding
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.MixpanelRampSource
import com.flowfoundation.wallet.network.functions.FUNCTION_MOON_PAY_SIGN
import com.flowfoundation.wallet.network.functions.executeHttpFunction
import com.flowfoundation.wallet.utils.extensions.openInSystemBrowser
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.viewModelIOScope
import java.net.URLEncoder

class SwapDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogSwapCoinListBinding

    private lateinit var viewModel: SwapViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSwapCoinListBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.closeButton.setOnClickListener { dismiss() }
        viewModel = ViewModelProvider(this)[SwapViewModel::class.java].apply { load() }

        with(binding) {
            moonpayButton.setOnClickListener {
                MixpanelManager.onRampClicked(MixpanelRampSource.MOONPAY)
                openUrl(viewModel.moonPayUrl)
            }
            coinbaseButton.setOnClickListener {
                MixpanelManager.onRampClicked(MixpanelRampSource.COINBASE)
                openUrl(coinBaseUrl())
            }
        }
    }

    private fun openUrl(url: String?) {
        url ?: return
        url.openInSystemBrowser(requireActivity())
        dismiss()
    }

    private fun coinBaseUrl(): String {
        // https://pay.coinbase.com/buy/input?appId=d22a56bd-68b7-4321-9b25-aa357fc7f9ce&destinationWallets=[{"address":"0x7d2b880d506db7cc","blockchains":["flow"]}]
        val json = """[{"address":"${viewModel.address}","blockchains":["flow"]}]"""
        return "https://pay.coinbase.com/buy/input?appId=d22a56bd-68b7-4321-9b25-aa357fc7f9ce&destinationWallets=${URLEncoder.encode(json, "UTF-8")}"
    }

    companion object {

        fun show(fragmentManager: FragmentManager) {
            SwapDialog().show(fragmentManager, "")
        }
    }
}

private val TAG = SwapViewModel::class.java.simpleName

internal class SwapViewModel : ViewModel() {

    lateinit var address: String

    var moonPayUrl: String? = null

    private val moonPayApiKey = if (isTestnet()) "pk_test_F0Y1SznEgbvGOWxFYJqStfjLeZ7XT" else "pk_live_6YNhgtZH8nyxkJiQRZsotO69G2loIyv0"
    private val moonPayHost = if (isTestnet()) "https://buy-sandbox.moonpay.com" else "https://buy.moonpay.com"

    fun load() {
        viewModelIOScope(this) {
            address = WalletManager.selectedWalletAddress()
            val response = executeHttpFunction(FUNCTION_MOON_PAY_SIGN, """{"url":"${buildMoonPayUrl()}"}""")
            logd(TAG, "moon pay response:$response")
            moonPayUrl = Gson().fromJson(response, MoonPaySignResponse::class.java).data?.url
            logd(TAG, "moon pay url:${moonPayUrl}")
        }
    }

    private fun buildMoonPayUrl(): String {
        // https://buy-sandbox.moonpay.com?apiKey=pk_test_F0Y1SznEgbvGOWxFYJqStfjLeZ7XT&defaultCurrencyCode=FLOW&colorCode=%23FC814A&walletAddress=0x9f871c373ff892c0
        return "${moonPayHost}?apiKey=$moonPayApiKey&defaultCurrencyCode=FLOW&colorCode=%23FC814A&walletAddress=$address"
    }

}

data class MoonPaySignResponse(
    @SerializedName("data")
    val data: Data?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: Int?
) {
    data class Data(
        @SerializedName("url")
        val url: String?
    )
}