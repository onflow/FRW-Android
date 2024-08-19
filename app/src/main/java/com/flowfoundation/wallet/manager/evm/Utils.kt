package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.flowjvm.EVM_GAS_LIMIT
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendEVMTransaction
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.flowfoundation.wallet.widgets.webview.evm.EvmInterface
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmTransaction
import com.nftco.flow.sdk.DomainTag
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.cadence.toJsonElement
import com.nftco.flow.sdk.decodeToAny
import kotlinx.serialization.json.jsonObject
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString
import org.web3j.rlp.RlpType
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import wallet.core.jni.Hash

const val PREVIEWNET_CHAIN_ID = 646
const val PREVIEWNET_RPC_URL = "https://previewnet.evm.nodes.onflow.org"
const val TESTNET_CHAIN_ID = 545
const val TESTNET_RPC_URL = "https://testnet.evm.nodes.onflow.org"
const val MAINNET_CHAIN_ID = 747
const val MAINNET_RPC_URL = "https://mainnet.evm.nodes.onflow.org"

fun getChainID(): Int {
    return when {
        isPreviewnet() -> PREVIEWNET_CHAIN_ID
        isTestnet() -> TESTNET_CHAIN_ID
        else -> MAINNET_CHAIN_ID
    }
}

fun getRPCUrl(): String {
    return when {
        isPreviewnet() -> PREVIEWNET_RPC_URL
        isTestnet() -> TESTNET_RPC_URL
        else -> MAINNET_RPC_URL
    }
}

fun loadInitJS(): String {
    return """
        (function() {
            var config = {                
                ethereum: {
                    chainId: ${getChainID()},
                    rpcUrl: "${getRPCUrl()}"
                },
                isDebug: true
            };
            trustwallet.ethereum = new trustwallet.Provider(config);
            trustwallet.postMessage = (json) => {
                window._tw_.postMessage(JSON.stringify(json));
            }
            window.ethereum = trustwallet.ethereum;
        })();
        """.trimIndent()
}

fun loadProviderJS(): String {
    return Env.getApp().resources.openRawResource(R.raw.flow_web3_min).bufferedReader().use { it.readText() }
}

fun sendEthereumTransaction(transaction: EvmTransaction, callback: (txHash: String) -> Unit) {
    ioScope {
        val amountValue = Numeric.decodeQuantity(transaction.value ?: "0")
        val toAddress = transaction.to?.removeAddressPrefix() ?: ""
        val gasValue = transaction.gas?.run {
            Numeric.decodeQuantity(this).toInt()
        } ?: EVM_GAS_LIMIT
        val value = Convert.fromWei(amountValue.toString(), Convert.Unit.ETHER)
        logd(EvmInterface.TAG, "amountValue:::$amountValue")
        logd(EvmInterface.TAG, "toAddress:::${toAddress}")
        logd(EvmInterface.TAG, "gasValue:::${gasValue}")
        logd(EvmInterface.TAG, "value:::$value")
        val data = Numeric.hexStringToByteArray(transaction.data ?: "")
        val txId = cadenceSendEVMTransaction(toAddress, value, data, gasValue)

        if (txId.isNullOrBlank()) {
            logd(EvmInterface.TAG, "send transaction failed")
            callback.invoke("")
            return@ioScope
        }
        logd(EvmInterface.TAG, "send transaction transactionId:$txId")
        TransactionStateWatcher(txId).watch { result ->
            if (result.isExecuteFinished()) {
                val event = result.events.find { it.type.contains("evm.TransactionExecuted", ignoreCase = true)}
                if (event == null) {
                    callback.invoke("")
                } else {
                    val element = event.payload.decodeToAny().toJsonElement()
                    try {
                        val eventHash = element.jsonObject["hash"].toString()
                        logd(EvmInterface.TAG, "eth transaction hash:$eventHash")
                        callback.invoke(eventHash)
                        refreshBalance(value.toFloat())
                    } catch (e: Exception) {
                        refreshBalance(value.toFloat())
                    }
                }
            }
        }
    }
}

fun refreshBalance(value: Float) {
    if (WalletManager.isEVMAccountSelected() && value > 0) {
        BalanceManager.refresh()
    }
}

fun signEthereumMessage(message: String): String {
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return ""
    val address = WalletManager.wallet()?.walletAddress() ?: return ""
    val flowAddress = FlowAddress(address)
    val keyIndex = flowAddress.currentKeyId(cryptoProvider.getPublicKey())

    val hashedData = hashPersonalMessage(message.toByteArray())
    val signableData = DomainTag.USER_DOMAIN_TAG + hashedData
    val sign = cryptoProvider.getSigner().sign(signableData)
    val rlpList = RlpList(asRlpValues(keyIndex, flowAddress.bytes, sign))
    val encoded = RlpEncoder.encode(rlpList)

    logd(EvmInterface.TAG, "hashedData:::${hashedData.bytesToHex()}")
    logd(EvmInterface.TAG, "signableData:::${signableData.bytesToHex()}")
    logd(EvmInterface.TAG, "sign:::${sign.bytesToHex()}")
    logd(EvmInterface.TAG, "encoded:::${encoded.bytesToHex()}")
    logd(EvmInterface.TAG, "signResult:::${Numeric.toHexString(encoded)}")

    return Numeric.toHexString(encoded)
}

private fun asRlpValues(keyIndex: Int, address: ByteArray, signature: ByteArray): List<RlpType> {
    return listOf(
        RlpList(RlpString.create(keyIndex.toBigInteger())),
        RlpString.create(address),
        RlpString.create("evm"),
        RlpList(RlpString.create(signature))
    )
}

private fun hashPersonalMessage(message: ByteArray): ByteArray {
    val messagePrefix = "\u0019Ethereum Signed Message:\n"
    val prefix = (messagePrefix + message.size).toByteArray()
    val result = ByteArray(prefix.size + message.size)
    System.arraycopy(prefix, 0, result, 0, prefix.size)
    System.arraycopy(message, 0, result, prefix.size, message.size)
    return Hash.keccak256(result)
}
