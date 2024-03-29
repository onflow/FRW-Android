package com.flowfoundation.wallet.page.wallet.sync

import android.graphics.drawable.Drawable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.nftco.flow.sdk.FlowChainId
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.walletconnect.model.WalletConnectMethod
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toQRDrawable
import com.flowfoundation.wallet.utils.viewModelIOScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class WalletSyncViewModel : ViewModel() {

    val qrCodeLiveData = MutableLiveData<Drawable?>()

    fun load() {
        viewModelIOScope(this) {
            val uri = try {
                wcFetchPairUriInternal()
            } catch (e: Exception) {
                qrCodeLiveData.postValue(null)
                return@viewModelIOScope
            }
            loge(WalletSyncViewModel::class.java.simpleName, "paringURI::${uri}")
            val drawable = uri?.toQRDrawable(withScale = true)
            qrCodeLiveData.postValue(drawable)
        }
    }

    private suspend fun wcFetchPairUriInternal() = suspendCoroutine { continuation ->
        val namespaces = mapOf(
            "flow" to Sign.Model.Namespace.Proposal(
                chains = listOf("flow:${
                    if (isTestnet()) FlowChainId.TESTNET 
                    else if (isPreviewnet()) FlowChainId.PREVIEWNET 
                    else FlowChainId.MAINNET}"),
                methods = WalletConnectMethod.values().map { it.value },
                events = listOf("chainChanged", "accountsChanged"),
            )
        )

        val pairing: Core.Model.Pairing = CoreClient.Pairing.create { error ->
            continuation.resume(null)
            throw IllegalStateException("Creating Pairing failed: ${error.throwable.stackTraceToString()}")
        }!!

        val connectParams =
            Sign.Params.Connect(
                namespaces = namespaces.toMutableMap(),
                pairing = pairing
            )

        SignClient.connect(
            connectParams,
            onSuccess = { _ -> continuation.resume(pairing.uri) },
            onError = { error ->
                loge(error.throwable)
                continuation.resumeWithException(error.throwable)
            }
        )
    }
}