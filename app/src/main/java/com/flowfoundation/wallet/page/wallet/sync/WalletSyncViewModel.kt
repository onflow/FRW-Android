package com.flowfoundation.wallet.page.wallet.sync

import android.graphics.drawable.Drawable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nftco.flow.sdk.FlowChainId
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.walletconnect.model.WalletConnectMethod
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toQRDrawable
import com.flowfoundation.wallet.utils.viewModelIOScope
import java.util.concurrent.atomic.AtomicReference
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
        val atomicReference = AtomicReference(continuation)
        val namespaces = mapOf(
            "flow" to Sign.Model.Namespace.Proposal(
                chains = listOf("flow:${
                    if (isTestnet()) FlowChainId.TESTNET 
                    else FlowChainId.MAINNET}"),
                methods = WalletConnectMethod.entries.map { it.value },
                events = listOf("chainChanged", "accountsChanged"),
            )
        )
        fun resumeContinuation(result: Result<String>) {
            val cont = atomicReference.get()
            if(cont != null && atomicReference.compareAndSet(cont, null)){
                cont.resumeWith(result)
            }
        }

        val pairing: Core.Model.Pairing = CoreClient.Pairing.create { error ->
            loge(WalletSyncViewModel::class.java.simpleName, "Creating Pairing failed: ${error.throwable.stackTraceToString()}")
            resumeContinuation(Result.failure(error.throwable))
            return@create
        } ?: run {
            resumeContinuation(Result.failure(IllegalStateException("Failed to create pairing")))
            return@suspendCoroutine
        }

        try {
            val connectParams = Sign.Params.Connect(
                namespaces = namespaces.toMutableMap(),
                pairing = pairing
            )

            SignClient.connect(
                connectParams,
                onSuccess = { _ -> resumeContinuation(Result.success(pairing.uri)) },
                onError = { error ->
                    loge(error.throwable)
                    resumeContinuation(Result.failure(error.throwable))
                }
            )
        } catch (e: Exception) {
            resumeContinuation(Result.failure(e))
        }
    }
}