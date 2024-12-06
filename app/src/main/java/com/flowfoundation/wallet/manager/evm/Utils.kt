package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.app.networkChainId
import com.flowfoundation.wallet.manager.app.networkRPCUrl
import com.flowfoundation.wallet.manager.flowjvm.EVM_GAS_LIMIT
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendEVMTransaction
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.transaction.isFailed
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.webview.evm.EvmInterface
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmTransaction
import com.nftco.flow.sdk.DomainTag
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.cadence.toJsonElement
import com.nftco.flow.sdk.decodeToAny
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString
import org.web3j.rlp.RlpType
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import wallet.core.jni.Hash


fun loadInitJS(): String {
    return """
        (function() {
            var config = {                
                ethereum: {
                    chainId: ${networkChainId()},
                    rpcUrl: "${networkRPCUrl()}"
                },
                isDebug: true
            };
            trustwallet.ethereum = new trustwallet.Provider(config);
            trustwallet.postMessage = (json) => {
                window._tw_.postMessage(JSON.stringify(json));
            }
            window.ethereum = trustwallet.ethereum;
            
            const EIP6963Icon =
                'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjUwIiBoZWlnaHQ9IjI1MCIgdmlld0JveD0iMCAwIDI1MCAyNTAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxnIGNsaXAtcGF0aD0idXJsKCNjbGlwMF8xMzc2MV8zNTIxKSI+CjxyZWN0IHdpZHRoPSIyNTAiIGhlaWdodD0iMjUwIiByeD0iNDYuODc1IiBmaWxsPSJ3aGl0ZSIvPgo8ZyBjbGlwLXBhdGg9InVybCgjY2xpcDFfMTM3NjFfMzUyMSkiPgo8cmVjdCB3aWR0aD0iMjUwIiBoZWlnaHQ9IjI1MCIgZmlsbD0idXJsKCNwYWludDBfbGluZWFyXzEzNzYxXzM1MjEpIi8+CjxwYXRoIGQ9Ik0xMjUgMjE3LjUyOUMxNzYuMTAyIDIxNy41MjkgMjE3LjUyOSAxNzYuMTAyIDIxNy41MjkgMTI1QzIxNy41MjkgNzMuODk3NSAxNzYuMTAyIDMyLjQ3MDcgMTI1IDMyLjQ3MDdDNzMuODk3NSAzMi40NzA3IDMyLjQ3MDcgNzMuODk3NSAzMi40NzA3IDEyNUMzMi40NzA3IDE3Ni4xMDIgNzMuODk3NSAyMTcuNTI5IDEyNSAyMTcuNTI5WiIgZmlsbD0id2hpdGUiLz4KPHBhdGggZD0iTTE2NS4zODIgMTEwLjQyMkgxMzkuNTg1VjEzNi43OEgxNjUuMzgyVjExMC40MjJaIiBmaWxsPSJibGFjayIvPgo8cGF0aCBkPSJNMTEzLjIyNyAxMzYuNzhIMTM5LjU4NVYxMTAuNDIySDExMy4yMjdWMTM2Ljc4WiIgZmlsbD0iIzQxQ0M1RCIvPgo8L2c+CjwvZz4KPGRlZnM+CjxsaW5lYXJHcmFkaWVudCBpZD0icGFpbnQwX2xpbmVhcl8xMzc2MV8zNTIxIiB4MT0iMCIgeTE9IjAiIHgyPSIyNTAiIHkyPSIyNTAiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIj4KPHN0b3Agc3RvcC1jb2xvcj0iIzFDRUI4QSIvPgo8c3RvcCBvZmZzZXQ9IjEiIHN0b3AtY29sb3I9IiM0MUNDNUQiLz4KPC9saW5lYXJHcmFkaWVudD4KPGNsaXBQYXRoIGlkPSJjbGlwMF8xMzc2MV8zNTIxIj4KPHJlY3Qgd2lkdGg9IjI1MCIgaGVpZ2h0PSIyNTAiIHJ4PSI0Ni44NzUiIGZpbGw9IndoaXRlIi8+CjwvY2xpcFBhdGg+CjxjbGlwUGF0aCBpZD0iY2xpcDFfMTM3NjFfMzUyMSI+CjxyZWN0IHdpZHRoPSIyNTAiIGhlaWdodD0iMjUwIiBmaWxsPSJ3aGl0ZSIvPgo8L2NsaXBQYXRoPgo8L2RlZnM+Cjwvc3ZnPgo=';

            const info = {
              uuid: crypto.randomUUID(),
              name: 'Flow Wallet',
              icon: EIP6963Icon,
              rdns: 'com.flowfoundation.wallet',
            };

            const announceEvent = new CustomEvent('eip6963:announceProvider', {
              detail: Object.freeze({ info, provider: window.ethereum }),
            });

            window.dispatchEvent(announceEvent);

            window.addEventListener('eip6963:requestProvider', () => {
               window.dispatchEvent(announceEvent);
            });
        })();
        """.trimIndent()
}

fun loadProviderJS(): String {
    return Env.getApp().resources.openRawResource(R.raw.flow_web3_min).bufferedReader()
        .use { it.readText() }
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
            evmTransactionSigned("", false)
            callback.invoke("")
            return@ioScope
        }
        logd(EvmInterface.TAG, "send transaction transactionId:$txId")
        TransactionStateWatcher(txId).watch { result ->
            if (result.isExecuteFinished()) {
                evmTransactionSigned(txId, true)
                val event = result.events.find {
                    it.type.contains(
                        "evm.TransactionExecuted",
                        ignoreCase = true
                    )
                }
                if (event == null) {
                    callback.invoke("")
                } else {
                    val element = event.payload.decodeToAny().toJsonElement()
                    try {
                        val eventHash = jsonArrayToByteArray(element.jsonObject["hash"] as
                                JsonArray).bytesToHex().toAddress()
                        logd(EvmInterface.TAG, "eth transaction hash:$eventHash")
                        callback.invoke(eventHash)
                        refreshBalance(value.toFloat())
                    } catch (e: Exception) {
                        refreshBalance(value.toFloat())
                    }
                }
            } else if (result.isFailed()) {
                evmTransactionSigned(txId, false)
            }
        }
    }
}

private fun evmTransactionSigned(txId: String, isSuccess: Boolean) {
    MixpanelManager.evmTransactionSigned(
        txId = txId,
        flowAddress = WalletManager.wallet()?.walletAddress().orEmpty(),
        evmAddress = EVMWalletManager.getEVMAddress().orEmpty(),
        isSuccess = isSuccess
    )
}

private fun jsonArrayToByteArray(jsonArray: JsonArray): ByteArray {
    val byteArray = ByteArray(jsonArray.size)
    for (i in jsonArray.indices) {
        val byteValue = (jsonArray[i] as JsonPrimitive).int and 0xFF
        byteArray[i] = byteValue.toByte()
    }
    return byteArray
}

fun refreshBalance(value: Float) {
    if (WalletManager.isEVMAccountSelected() && value > 0) {
        BalanceManager.refresh()
    }
}

fun signTypedData(data: ByteArray): String {
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return ""
    val address = WalletManager.wallet()?.walletAddress() ?: return ""
    val flowAddress = FlowAddress(address)
    val keyIndex = flowAddress.currentKeyId(cryptoProvider.getPublicKey())

    val signableData = DomainTag.USER_DOMAIN_TAG + data
    val sign = cryptoProvider.getSigner().sign(signableData)
    val rlpList = RlpList(asRlpValues(keyIndex, flowAddress.bytes, sign))
    val encoded = RlpEncoder.encode(rlpList)

    logd(EvmInterface.TAG, "signableData:::${signableData.bytesToHex()}")
    logd(EvmInterface.TAG, "sign:::${sign.bytesToHex()}")
    logd(EvmInterface.TAG, "encoded:::${encoded.bytesToHex()}")
    logd(EvmInterface.TAG, "signResult:::${Numeric.toHexString(encoded)}")

    return Numeric.toHexString(encoded)
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
