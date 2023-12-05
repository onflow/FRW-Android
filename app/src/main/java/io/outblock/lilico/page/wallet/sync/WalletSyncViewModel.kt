package io.outblock.lilico.page.wallet.sync

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nftco.flow.sdk.FlowChainId
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import io.outblock.lilico.manager.app.isTestnet
import io.outblock.lilico.manager.walletconnect.model.WalletConnectMethod
import io.outblock.lilico.utils.ScreenUtils
import io.outblock.lilico.utils.extensions.dp2px
import io.outblock.lilico.utils.loge
import io.outblock.lilico.utils.toQRBitmap
import io.outblock.lilico.utils.viewModelIOScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class WalletSyncViewModel : ViewModel() {

    private val size by lazy { ScreenUtils.getScreenWidth() - (30 * 2).dp2px().toInt() }

    val qrCodeLiveData = MutableLiveData<Bitmap>()

    fun load() {
        viewModelIOScope(this) {
            val uri = wcFetchPairUriInternal() ?: return@viewModelIOScope
            loge(WalletSyncViewModel::class.java.simpleName, "paringURI::${uri}")
            val bitmap = uri.toQRBitmap(width = size, height = size) ?: return@viewModelIOScope
            qrCodeLiveData.postValue(bitmap)
        }
    }

    private suspend fun wcFetchPairUriInternal() = suspendCoroutine<String?> { continuation ->
        val namespaces = mapOf(
            "flow" to Sign.Model.Namespace.Proposal(
                chains = listOf("flow:${if (isTestnet()) FlowChainId.TESTNET else FlowChainId.MAINNET}"),
                methods = WalletConnectMethod.values().map { it.value },
                events = listOf("chainChanged", "accountsChanged"),
            )
        )

        val pairing: Core.Model.Pairing = CoreClient.Pairing.create { error ->
            throw IllegalStateException("Creating Pairing failed: ${error.throwable.stackTraceToString()}")
        }!!

        val connectParams =
            Sign.Params.Connect(
                namespaces = namespaces.toMutableMap(),
                pairing = pairing
            )

        SignClient.connect(
            connectParams,
            onSuccess = { continuation.resume(pairing.uri) },
            onError = { error ->
                loge(error.throwable)
                continuation.resumeWithException(error.throwable)
            }
        )
    }
}