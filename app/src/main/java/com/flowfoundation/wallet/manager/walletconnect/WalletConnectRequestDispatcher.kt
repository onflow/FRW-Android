package com.flowfoundation.wallet.manager.walletconnect

import androidx.appcompat.app.AppCompatActivity
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.AppLifecycleObserver
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.evm.sendEthereumTransaction
import com.flowfoundation.wallet.manager.evm.signEthereumMessage
import com.flowfoundation.wallet.manager.evm.signTypedData
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.flowjvm.transaction.PayerSignable
import com.flowfoundation.wallet.manager.flowjvm.transaction.SignPayerResponse
import com.flowfoundation.wallet.manager.flowjvm.transaction.Signable
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.manager.walletconnect.model.Identity
import com.flowfoundation.wallet.manager.walletconnect.model.PollingData
import com.flowfoundation.wallet.manager.walletconnect.model.PollingResponse
import com.flowfoundation.wallet.manager.walletconnect.model.ResponseStatus
import com.flowfoundation.wallet.manager.walletconnect.model.Service
import com.flowfoundation.wallet.manager.walletconnect.model.SignableParams
import com.flowfoundation.wallet.manager.walletconnect.model.WCAccountRequest
import com.flowfoundation.wallet.manager.walletconnect.model.WCProxyAccountRequest
import com.flowfoundation.wallet.manager.walletconnect.model.WCRequest
import com.flowfoundation.wallet.manager.walletconnect.model.WalletConnectMethod
import com.flowfoundation.wallet.manager.walletconnect.model.WatchAsset
import com.flowfoundation.wallet.manager.walletconnect.model.walletConnectWalletInfoResponse
import com.flowfoundation.wallet.network.functions.FUNCTION_SIGN_AS_PAYER
import com.flowfoundation.wallet.network.functions.executeHttpFunction
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.token.custom.widget.AddCustomTokenDialog
import com.flowfoundation.wallet.page.wallet.confirm.WalletConfirmationDialog
import com.flowfoundation.wallet.page.wallet.proxy.WalletProxyConfirmationDialog
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EVMSendTransactionDialog
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmTransaction
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EVMSignMessageDialog
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EVMSignTypedDataDialog
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMTransactionDialogModel
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
import org.onflow.flow.models.hexToBytes
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import kotlinx.coroutines.delay
import okio.ByteString.Companion.decodeBase64
import org.onflow.flow.infrastructure.Cadence
import org.onflow.flow.models.FlowAddress
import org.web3j.crypto.StructuredDataEncoder
import java.lang.reflect.Type
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.onflow.flow.models.Transaction
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger

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
        WalletConnectMethod.PROXY_ACCOUNT.value -> respondProxyAccount()
        WalletConnectMethod.PROXY_SIGN.value -> respondProxySign()
        WalletConnectMethod.EVM_SIGN_MESSAGE.value -> evmSignMessage()
        WalletConnectMethod.EVM_SEND_TRANSACTION.value -> evmSendTransaction()
        WalletConnectMethod.EVM_SIGN_TYPED_DATA.value, WalletConnectMethod.EVM_SIGN_TYPED_DATA_V3.value,
        WalletConnectMethod.EVM_SIGN_TYPED_DATA_V4.value -> evmSignTypedData()
        WalletConnectMethod.WALLET_WATCH_ASSETS.value -> watchAssets()
    }
}

suspend fun WCRequest.evmSignTypedData() {
    val activity = topActivity() ?: return
    val jsonArray = Gson().fromJson(params, JsonArray::class.java)
    val messageObject = if (jsonArray.get(0).isJsonObject) {
        jsonArray.get(0)
    } else {
        jsonArray.get(1)
    } ?: return
    val message = Gson().toJson(messageObject)
    val dataEncoder = StructuredDataEncoder(message)
    val hashData = dataEncoder.hashStructuredData()
    logd(TAG, "hashData::$hashData")
    uiScope {
        val model = FclDialogModel(
            title = metaData?.name,
            logo = metaData?.icons?.firstOrNull(),
            url = metaData?.url,
            signMessage = message,
        )
        EVMSignTypedDataDialog.show(
            activity.supportFragmentManager,
            model
        )
        EVMSignTypedDataDialog.observe { isApprove ->
            ioScope {
                if (isApprove) approve(signTypedData(hashData)) else reject()
            }
        }
    }
}

private suspend fun WCRequest.evmSendTransaction() {
    val activity = topActivity() ?: return
    val json = Gson().fromJson<List<EvmTransaction>>(params, object : TypeToken<List<EvmTransaction>>() {}.type)
    val transaction = json.firstOrNull() ?: return
    uiScope {
        val model = EVMTransactionDialogModel(
            title = metaData?.name,
            logo = metaData?.icons?.firstOrNull(),
            url = metaData?.url,
            toAddress = transaction.to,
            value = transaction.value,
            data = transaction.data
        )
        EVMSendTransactionDialog.show(
            activity.supportFragmentManager,
            model
        )
        EVMSendTransactionDialog.observe { isApprove ->
            ioScope {
                if (isApprove) {
                    sendEthereumTransaction(transaction) { txHash ->
                        if (txHash.isEmpty()) {
                            reject()
                        } else {
                            approve(txHash)
                        }
                    }
                } else reject()
            }
        }
    }
}

private suspend fun WCRequest.watchAssets() {
    val activity = topActivity() ?: return
    val watchAsset = Gson().fromJson(params, WatchAsset::class.java)
    if (watchAsset.type.equals("ERC20", ignoreCase = true).not() || watchAsset.options?.address == null) {
        toast(msgRes = R.string.invalid_evm_address)
        return
    }
    uiScope {
        AddCustomTokenDialog.show(
            activity.supportFragmentManager,
            watchAsset.options.address,
            watchAsset.options.image
        )
        AddCustomTokenDialog.observe { isApprove ->
            if (isApprove) {
                approve(true.toString())
            } else reject()
        }
    }
}

private suspend fun WCRequest.evmSignMessage() {
    val activity = topActivity() ?: return
    val json = Gson().fromJson<List<String>>(params, object : TypeToken<List<String>>() {}.type)
    val hexMessage = json.firstOrNull() ?: return
    val message = String(hexMessage.hexToBytes(), Charsets.UTF_8)
    uiScope {
        val model = FclDialogModel(
            title = metaData?.name,
            logo = metaData?.icons?.firstOrNull(),
            url = metaData?.url,
            signMessage = hexMessage,
        )
        EVMSignMessageDialog.show(
            activity.supportFragmentManager,
            model
        )
        EVMSignMessageDialog.observe { isApprove ->
            ioScope {
                if (isApprove) approve(signEthereumMessage(message)) else reject()
            }
        }
    }
}

private suspend fun WCRequest.respondAddDeviceKey() {
    val activity = topActivity() ?: return
    val request = Gson().fromJson(params, WCAccountRequest::class.java)
    val accountInfo = request.data
    WalletConfirmationDialog.show(activity, requestId, topic, Gson().toJson(accountInfo) ?: "")
}

private suspend fun WCRequest.respondProxyAccount() {
    val activity = topActivity() ?: return
    val request = Gson().fromJson(params, WCProxyAccountRequest::class.java)
    val accountInfo = request.data
    WalletProxyConfirmationDialog.show(activity, requestId, topic, accountInfo?.jwt ?: "", Gson().toJson(accountInfo?.deviceInfo) ?: "")
}

private suspend fun WCRequest.respondProxySign() {
    val request = Gson().fromJson(params, WCRequest::class.java)
    request.dispatch()
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

private suspend fun WCRequest.respondAuthn() {
    logd(TAG, "Starting respondAuthn with params: $params")
    val address = WalletManager.wallet()?.walletAddress() ?: run {
        loge(TAG, "No wallet address found")
        return
    }
    
    val json = try {
        Gson().fromJson<List<SignableParams>>(params, object : TypeToken<List<SignableParams>>() {}.type)
    } catch (e: Exception) {
        loge(TAG, "Failed to parse params: ${e.message}")
        loge(e)
        return
    }
    val signable = json.firstOrNull() ?: run {
        loge(TAG, "No signable params found")
        return
    }
    logd(TAG, "Signable params: $signable")
    logd(TAG, "Request address: ${signable.address}")
    
    // Use the wallet's current address for the active network, not the request address
    // The request address might be for a different network
    val walletAddress = address
    logd(TAG, "Using wallet address for signing: $walletAddress")
    
    // Clean address for Flow-KMM (remove "0x" prefix)
    val cleanWalletAddress = walletAddress.removePrefix("0x")
    logd(TAG, "Cleaned wallet address for Flow-KMM: $cleanWalletAddress")
    
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: run {
        loge(TAG, "No crypto provider found")
        return
    }
    val keyId = cryptoProvider.let {
        FlowAddress(cleanWalletAddress).currentKeyId(it.getPublicKey())
    }
    logd(TAG, "Using keyId: $keyId")
    
    val services = walletConnectAuthnServiceResponse(cleanWalletAddress, keyId, signable.nonce, signable.appIdentifier)
    logd(TAG, "Generated authn response: $services")
    
    val response = Sign.Params.Response(
        sessionTopic = topic,
        jsonRpcResponse = Sign.Model.JsonRpcResponse.JsonRpcResult(requestId, services)
    )
    logd(TAG, "Sending response: $response")

    SignClient.respond(response, onSuccess = { success ->
        logd(TAG, "Response sent successfully: $success")
        ioScope {
            delay(1000)
            logd(TAG, "Authn response sent, proceeding with session settlement")
        }
    }) { error -> 
        loge(TAG, "Failed to send response: ${error.throwable.message}")
        loge(error.throwable)
    }
}

private suspend fun WCRequest.respondAuthz() {
    val activity = topActivity() ?: return
    val json = gson().fromJson<List<Signable>>(params, object : TypeToken<List<Signable>>() {}.type)
    val signable = json.firstOrNull() ?: return
    val message = signable.message ?: return
    val address = WalletManager.wallet()?.walletAddress() ?: return
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return
    
    // Clean address for Flow-KMM (remove "0x" prefix)
    val cleanAddress = address.removePrefix("0x")
    
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
                val signature = cryptoProvider.signData(message.hexToBytes())
                val keyId = FlowAddress(cleanAddress).currentKeyId(cryptoProvider.getPublicKey())

                if (isApprove) approve(fclAuthzResponse(cleanAddress, signature, keyId)) else reject()
                uiScope { FclAuthzDialog.dismiss() }
            }
        }
    }
}

private suspend fun WCRequest.respondPreAuthz() {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return
    val payerAddress = if (AppConfig.isFreeGas()) AppConfig.payer().address else walletAddress
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return
    
    // Clean addresses for Flow-KMM (remove "0x" prefix)
    val cleanWalletAddress = walletAddress.removePrefix("0x")
    val cleanPayerAddress = payerAddress.removePrefix("0x")
    
    val keyId = FlowAddress(cleanWalletAddress).currentKeyId(cryptoProvider.getPublicKey())

    val response = PollingResponse(
        status = ResponseStatus.APPROVED,
        data = PollingData(
            fType = "PreAuthzResponse",
            fVsn = "1.0.0",
            proposer = Service(
                fType = "Service",
                fVsn = "1.0.0",
                type = "authz",
                uid =  "https://frw-link.lilico.app/wc",
                identity = Identity(address = cleanWalletAddress, keyId = keyId),
                method = "WC/RPC",
                endpoint = WalletConnectMethod.AUTHZ.value,
            ),
            payer = listOf(
                Service(
                    fType = "Service",
                    fVsn = "1.0.0",
                    type = "authz",
                    uid =  "https://frw-link.lilico.app/wc",
                    identity = Identity(address = cleanPayerAddress, keyId = AppConfig.payer().keyId),
                    method = "WC/RPC",
                    endpoint = WalletConnectMethod.SIGN_PAYER.value,
                )
            ),
            authorization = listOf(
                Service(
                    fType = "Service",
                    fVsn = "1.0.0",
                    type = "authz",
                    uid =  "https://frw-link.lilico.app/wc",
                    identity = Identity(address = cleanWalletAddress, keyId = keyId),
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
    val address = WalletManager.wallet()?.walletAddress() ?: return
    val param = gson().fromJson<List<SignableMessage>>(params, object : TypeToken<List<SignableMessage>>() {}.type)?.firstOrNull()
    val message = param?.message ?: return
    
    // Clean address for Flow-KMM (remove "0x" prefix)
    val cleanAddress = address.removePrefix("0x")
    
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
            ioScope {
                if (isApprove) approve(fclSignMessageResponse(message, cleanAddress)) else reject()
                FclAuthzDialog.dismiss()
            }
        }
    }
}

private suspend fun WCRequest.respondSignPayer() {
    val json = gson().fromJson<List<Signable>>(params, object : TypeToken<List<Signable>>() {}.type)
    val signable = json.firstOrNull() ?: return
    val voucher = signable.voucher ?: return
    
    // Validate required fields
    val cadence = voucher.cadence ?: return
    val refBlock = voucher.refBlock ?: return
    val computeLimit = voucher.computeLimit ?: return
    val payer = voucher.payer ?: return
    val proposalKey = voucher.proposalKey
    val proposerAddress = proposalKey.address ?: return
    val proposerKeyId = proposalKey.keyId ?: return
    val proposerSequenceNum = proposalKey.sequenceNum ?: return
    
    // Clean addresses for Flow-KMM (remove "0x" prefix)
    val cleanPayer = payer.removePrefix("0x")
    val cleanProposerAddress = proposerAddress.removePrefix("0x")
    val cleanAuthorizers = voucher.authorizers?.map { it.removePrefix("0x") } ?: emptyList()
    
    val transaction = Transaction(
        script = cadence,
        arguments = voucher.arguments?.map { Cadence.string(it.toString()) } ?: emptyList(),
        referenceBlockId = refBlock,
        gasLimit = computeLimit.toBigInteger(),
        payer = cleanPayer,
        proposalKey = org.onflow.flow.models.ProposalKey(
            address = cleanProposerAddress,
            keyIndex = proposerKeyId,
            sequenceNumber = proposerSequenceNum.toBigInteger()
        ),
        authorizers = cleanAuthorizers
    )

    val message = signable.message ?: return
    val server = executeHttpFunction(
        FUNCTION_SIGN_AS_PAYER, PayerSignable(
            transaction = transaction,
            message = PayerSignable.Message(message)
        )
    )

    safeRun {
        val sigs = gson().fromJson(server, SignPayerResponse::class.java).envelopeSigs
        val response = PollingResponse(
            status = ResponseStatus.APPROVED,
            data = PollingData(
                fType = "CompositeSignature",
                fVsn = "1.0.0",
                address = sigs.address,
                keyId = sigs.keyId,
                signature = sigs.sig,
            )
        )
        approve(gson().toJson(response))
        FclAuthzDialog.dismiss()
    }
}

private suspend fun WCRequest.respondSignProposer() {
    val activity = topActivity() ?: return

    logd(TAG, "respondSignProposer param:${params}")
    val signable = params.toSignables(gson())
    val address = WalletManager.wallet()?.walletAddress() ?: return
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return
    
    // Clean address for Flow-KMM (remove "0x" prefix)
    val cleanAddress = address.removePrefix("0x")
    
    logd(TAG, "WalletConnect Sign Proposer - Provider type: ${cryptoProvider.javaClass.simpleName}")
    logd(TAG, "WalletConnect Sign Proposer - Signing algorithm: ${cryptoProvider.getSignatureAlgorithm()}")
    logd(TAG, "WalletConnect Sign Proposer - Hashing algorithm: ${cryptoProvider.getHashAlgorithm()}")
    logd(TAG, "WalletConnect Sign Proposer - Public key: ${cryptoProvider.getPublicKey()}")
    logd(TAG, "WalletConnect Sign Proposer - Message to sign: ${signable?.message}")
    logd(TAG, "WalletConnect Sign Proposer - Clean address: $cleanAddress")
    
    val keyId = FlowAddress(cleanAddress).currentKeyId(cryptoProvider.getPublicKey())

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
        ioScope {
            if (approve) {
                logd(TAG, "WalletConnect Sign Proposer - User approved, signing with signData()")
                val signature = cryptoProvider.signData(signable?.message!!.hexToBytes())
                logd(TAG, "WalletConnect Sign Proposer - Generated signature: $signature")
                val response = PollingResponse(
                    status = ResponseStatus.APPROVED,
                    data = PollingData(
                        fType = "CompositeSignature",
                        fVsn = "1.0.0",
                        address = cleanAddress,
                        keyId = keyId,
                        signature = signature
                    )
                )
                approve(gson().toJson(response))
            } else {
                reject()
            }
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