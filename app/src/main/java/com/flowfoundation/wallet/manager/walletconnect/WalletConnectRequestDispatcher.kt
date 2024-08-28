package com.flowfoundation.wallet.manager.walletconnect

import androidx.appcompat.app.AppCompatActivity
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.AppLifecycleObserver
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.evm.sendEthereumTransaction
import com.flowfoundation.wallet.manager.evm.signEthereumMessage
import com.flowfoundation.wallet.manager.flowjvm.CADENCE_CALL_EVM_CONTRACT
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.flowjvm.transaction.PayerSignable
import com.flowfoundation.wallet.manager.flowjvm.transaction.SignPayerResponse
import com.flowfoundation.wallet.manager.flowjvm.transaction.Signable
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletconnect.model.Identity
import com.flowfoundation.wallet.manager.walletconnect.model.PollingData
import com.flowfoundation.wallet.manager.walletconnect.model.PollingResponse
import com.flowfoundation.wallet.manager.walletconnect.model.ResponseStatus
import com.flowfoundation.wallet.manager.walletconnect.model.Service
import com.flowfoundation.wallet.manager.walletconnect.model.WCAccountRequest
import com.flowfoundation.wallet.manager.walletconnect.model.WCRequest
import com.flowfoundation.wallet.manager.walletconnect.model.WalletConnectMethod
import com.flowfoundation.wallet.manager.walletconnect.model.walletConnectWalletInfoResponse
import com.flowfoundation.wallet.network.functions.FUNCTION_SIGN_AS_PAYER
import com.flowfoundation.wallet.network.functions.executeHttpFunction
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.wallet.confirm.WalletConfirmationDialog
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EVMSendTransactionDialog
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmTransaction
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.FclSignMessageDialog
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.authz.FclAuthzDialog
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.checkAndShowNetworkWrongDialog
import com.flowfoundation.wallet.widgets.webview.fcl.fclAuthzResponse
import com.flowfoundation.wallet.widgets.webview.fcl.fclSignMessageResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.hexToBytes
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import kotlinx.coroutines.delay
import okio.ByteString.Companion.decodeBase64
import java.io.ByteArrayOutputStream
import java.lang.reflect.Type
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "WalletConnectRequestDispatcher"

suspend fun WCRequest.dispatch() {
    when (method) {
        WalletConnectMethod.AUTHN.value -> respondAuthn()
        WalletConnectMethod.AUTHZ.value -> respondAuthz()
        WalletConnectMethod.PRE_AUTHZ.value -> respondPreAuthz()
        WalletConnectMethod.USER_SIGNATURE.value -> respondUserSign()
        WalletConnectMethod.SIGN_PAYER.value -> respondSignPayer()
        WalletConnectMethod.SIGN_PROPOSER.value -> respondSignProposer()
        WalletConnectMethod.ACCOUNT_INFO.value -> respondAccountInfo()
        WalletConnectMethod.ADD_DEVICE_KEY.value -> respondAddDeviceKey()
        WalletConnectMethod.EVM_SIGN_MESSAGE.value -> evmSignMessage()
        WalletConnectMethod.EVM_SEND_TRANSACTION.value -> evmSendTransaction()
    }
}

private suspend fun WCRequest.evmSendTransaction() {
    val activity = topActivity() ?: return
    val json = gson().fromJson<List<EvmTransaction>>(params, object : TypeToken<List<EvmTransaction>>() {}.type)
    val transaction = json.firstOrNull() ?: return
    uiScope {
        val model = EVMDialogModel(
            title = metaData?.name,
            logo = metaData?.icons?.firstOrNull(),
            url = metaData?.url,
            cadence = CADENCE_CALL_EVM_CONTRACT,
        )
        EVMSendTransactionDialog.show(
            activity.supportFragmentManager,
            model
        )
        EVMSendTransactionDialog.observe { isApprove ->
            if (isApprove) {
                sendEthereumTransaction(transaction) { txHash ->
                    if (txHash.isEmpty()) {
                        reject()
                    } else {
                        approve(txHash)
                    }
                }
            } else reject()
            redirectToSourceApp()
        }
    }
}

private suspend fun WCRequest.evmSignMessage() {
    val activity = topActivity() ?: return
    val json = gson().fromJson<List<String>>(params, object : TypeToken<List<String>>() {}.type)
    val hexMessage = json.firstOrNull() ?: return
    val message = String(hexMessage.hexToBytes(), Charsets.UTF_8)
    uiScope {
        val model = FclDialogModel(
            title = metaData?.name,
            logo = metaData?.icons?.firstOrNull(),
            url = metaData?.url,
            signMessage = hexMessage,
        )
        FclSignMessageDialog.show(
            activity.supportFragmentManager,
            model
        )
        FclSignMessageDialog.observe { isApprove ->
            if (isApprove) approve(signEthereumMessage(message)) else reject()
            FclAuthzDialog.dismiss()
            redirectToSourceApp()
        }
    }
}

private suspend fun WCRequest.respondAddDeviceKey() {
    val activity = topActivity() ?: return
    val request = Gson().fromJson(params, WCAccountRequest::class.java)
    val accountInfo = request.data
    WalletConfirmationDialog.show(activity, requestId, topic, Gson().toJson(accountInfo) ?: "")
}

private fun WCRequest.respondAccountInfo() {
    val account = AccountManager.get() ?: return
    val uid = account.wallet?.id
    if (uid.isNullOrBlank()) {
        return
    }
    val response = Sign.Params.Response(
        sessionTopic = topic,
        jsonRpcResponse = Sign.Model.JsonRpcResponse.JsonRpcResult(
            requestId, walletConnectWalletInfoResponse(
                uid,
                account.userInfo.avatar,
                account.userInfo.username,
                WalletManager.selectedWalletAddress())
        )
    )
    logd(TAG, "respondAccountInfo:\n$response")

    SignClient.respond(response, onSuccess = { success ->
        logd(TAG, "success:${success}")
    }) { error -> loge(error.throwable) }
}

private fun WCRequest.respondAuthn() {
    val address = WalletManager.selectedWalletAddress()
    val json = gson().fromJson<List<Signable>>(params, object : TypeToken<List<Signable>>() {}.type)
    val signable = json.firstOrNull() ?: return
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
    val keyId = cryptoProvider?.let {
        FlowAddress(address).currentKeyId(it.getPublicKey())
    } ?: 0
    val services = walletConnectAuthnServiceResponse(address, keyId, signable.data?.get("nonce"), signable.data?.get("appIdentifier"), isFromFclSdk())
    val response = Sign.Params.Response(
        sessionTopic = topic,
        jsonRpcResponse = Sign.Model.JsonRpcResponse.JsonRpcResult(requestId, services.responseParse(this))
    )
    logd(TAG, "respondAuthn:\n${services}")

    SignClient.respond(response, onSuccess = { success ->
        logd(TAG, "success:${success}")
    }) { error -> loge(error.throwable) }
    redirectToSourceApp()
}

private suspend fun WCRequest.respondAuthz() {
    val activity = topActivity() ?: return
    val json = gson().fromJson<List<Signable>>(params, object : TypeToken<List<Signable>>() {}.type)
    val signable = json.firstOrNull() ?: return
    val message = signable.message ?: return
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return
    uiScope {
        val data = FclDialogModel(
            title = metaData?.name,
            logo = metaData?.icons?.firstOrNull(),
            url = metaData?.url,
            cadence = signable.cadence,
            network = chainId?.toNetwork()
        )
        if (checkAndShowNetworkWrongDialog(activity.supportFragmentManager, data)) {
            reject()
            return@uiScope
        }

        FclAuthzDialog.show(
            activity.supportFragmentManager,
            data
        )

        FclAuthzDialog.observe { isApprove ->
            ioScope {
                val address = WalletManager.selectedWalletAddress()
                val signature = cryptoProvider.signData(message.hexToBytes())
                val keyId = FlowAddress(address).currentKeyId(cryptoProvider.getPublicKey())

                if (isApprove) approve(fclAuthzResponse(address, signature, keyId)) else reject()
                uiScope { FclAuthzDialog.dismiss() }
            }
        }
    }
    redirectToSourceApp()
}


private fun WCRequest.respondPreAuthz() {
    val walletAddress = WalletManager.selectedWalletAddress()
    val payerAddress = if (AppConfig.isFreeGas()) AppConfig.payer().address else walletAddress
    val response = PollingResponse(
        status = ResponseStatus.APPROVED,
        data = PollingData(
            proposer = Service(
                identity = Identity(address = walletAddress, keyId = 0),
                method = "WC/RPC",
                endpoint = WalletConnectMethod.AUTHZ.value,
            ),
            payer = listOf(
                Service(
                    identity = Identity(address = payerAddress, keyId = 0),
                    method = "WC/RPC",
                    endpoint = WalletConnectMethod.SIGN_PAYER.value,
                )
            ),
            authorization = listOf(
                Service(
                    identity = Identity(address = walletAddress, keyId = 0),
                    method = "WC/RPC",
                    endpoint = WalletConnectMethod.SIGN_PROPOSER.value,
                )
            ),
        )
    )
    approve(gson().toJson(response))
}

private suspend fun WCRequest.respondUserSign() {
    val activity = topActivity() ?: return
    val address = WalletManager.selectedWalletAddress()
    val param = gson().fromJson<List<SignableMessage>>(params, object : TypeToken<List<SignableMessage>>() {}.type)?.firstOrNull()
    val message = param?.message ?: return
    uiScope {
        val data = FclDialogModel(
            title = metaData?.name,
            logo = metaData?.icons?.firstOrNull(),
            url = metaData?.url,
            signMessage = message,
            network = chainId?.toNetwork()
        )

        if (checkAndShowNetworkWrongDialog(activity.supportFragmentManager, data)) {
            reject()
            return@uiScope
        }

        FclSignMessageDialog.show(
            activity.supportFragmentManager,
            data
        )

        FclSignMessageDialog.observe { isApprove ->
            if (isApprove) approve(fclSignMessageResponse(message, address)) else reject()
            FclAuthzDialog.dismiss()
            redirectToSourceApp()
        }
    }
}

private suspend fun WCRequest.respondSignPayer() {
    val json = gson().fromJson<List<Signable>>(params, object : TypeToken<List<Signable>>() {}.type)
    val signable = json.firstOrNull() ?: return
    val server = executeHttpFunction(
        FUNCTION_SIGN_AS_PAYER, PayerSignable(
            transaction = signable.voucher!!,
            message = PayerSignable.Message(signable.message!!)
        )
    )

    safeRun {
        val sigs = gson().fromJson(server, SignPayerResponse::class.java).envelopeSigs
        val response = PollingResponse(
            status = ResponseStatus.APPROVED,
            data = PollingData(
                address = sigs.address,
                keyId = sigs.keyId,
                signature = sigs.sig,
            )
        )
        approve(gson().toJson(response))
        FclAuthzDialog.dismiss()
    }
    redirectToSourceApp()
}


private suspend fun WCRequest.respondSignProposer() {
    val activity = topActivity() ?: return

    logd(TAG, "respondSignProposer param:${params}")
    val signable = params.toSignables(gson())
    val address = WalletManager.selectedWalletAddress()
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return

    val data = FclDialogModel(
        title = metaData?.name,
        logo = metaData?.icons?.firstOrNull(),
        url = metaData?.url,
        cadence = signable?.voucher?.cadence,
        network = chainId?.toNetwork()
    )

    if (checkAndShowNetworkWrongDialog(activity.supportFragmentManager, data)) {
        reject()
        return
    }

    FclAuthzDialog.show(
        activity.supportFragmentManager,
        data
    )
    FclAuthzDialog.observe { approve ->
        if (approve) {
            val response = PollingResponse(
                status = ResponseStatus.APPROVED,
                data = PollingData(
                    address = address,
                    keyId = 0,
                    signature = cryptoProvider.signData(signable?.message!!.hexToBytes())
                )
            )
            approve(gson().toJson(response))
        } else {
            reject()
        }
    }
}

private fun gson() = GsonBuilder().registerTypeAdapter(Signable::class.java, SignableDeserializer()).setLenient().create()

private class SignableDeserializer : JsonDeserializer<Signable> {
    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Signable {
        val obj = json.asJsonObject

        if (obj.has("interaction")) {
            val interaction = obj.get("interaction").asJsonObject
            if (interaction.has("payer")) {
                val payer = obj.get("interaction").asJsonObject.get("payer").asString.trim()
                if (!payer.startsWith("[")) {
                    val ja = JsonArray().apply { add(payer) }
                    interaction.add("payer", ja)
                }
            }
        }
        return Gson().fromJson(obj, Signable::class.java)
    }
}


fun gzip(content: String): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    return bos.toByteArray()
}

fun ungzip(content: ByteArray): String =
    GZIPInputStream(content.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }

fun String.toSignables(gson: Gson): Signable? {
    return try {
        val typeToken = object : TypeToken<List<Signable>>() {}.type
        val result: List<Signable> = gson.fromJson(this, typeToken)
        result.firstOrNull()
    } catch (e: Exception) {
        try {
            val stringListType = object : TypeToken<List<String>>() {}.type
            val arguments: List<String> = gson.fromJson(this, stringListType)
            arguments.firstOrNull()?.decodeBase64()?.toByteArray()?.run {
                val jsonString = ungzip(this)
                gson.fromJson(jsonString, Signable::class.java)
            }
        } catch (e: Exception) {
            null
        }
    }
}

private suspend fun topActivity() = suspendCoroutine<AppCompatActivity?> { continuation ->
    if (!AppLifecycleObserver.isForeground()) {
        uiScope {
            val context = BaseActivity.getCurrentActivity() ?: Env.getApp()
            MainActivity.launch(context)
//            context.startActivity(context.packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID))
//            MainActivity.launch(context)
            delay(1000)
            continuation.resume(BaseActivity.getCurrentActivity())
            logw("xxx", "activity1:${BaseActivity.getCurrentActivity()}")
        }
    } else {
        continuation.resume(BaseActivity.getCurrentActivity())
    }
}