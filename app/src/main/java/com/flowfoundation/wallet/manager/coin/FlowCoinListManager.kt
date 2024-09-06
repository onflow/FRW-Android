package com.flowfoundation.wallet.manager.coin

import android.os.Parcelable
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.readTextFromAssets
import com.flowfoundation.wallet.utils.svgToPng
import kotlinx.parcelize.Parcelize
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

object FlowCoinListManager {
    private val TAG = FlowCoinListManager::class.java.simpleName
    private val coinList = CopyOnWriteArrayList<FlowCoin>()

    fun reload() {
        ioScope {
            val text = URL(getTokenListUrl()).readText()
            logd(TAG, "json::${chainNetWorkString()}::$text")
            val list = Gson().fromJson(text, TokenList::class.java)
            if (list.tokens.isNotEmpty()) {
                coinList.clear()
                coinList.addAll(list.tokens.filter { it.address.isNotBlank() })
                if (WalletManager.isEVMAccountSelected()) {
                    addFlowTokenManually()
                }
            }
        }
    }

    private fun addFlowTokenManually() {
        try {
            val text = readTextFromAssets(
                if (isTestnet()) {
                    "config/flow_token_testnet.json"
                } else if (isPreviewnet()) {
                    "config/flow_token_previewnet.json"
                } else {
                    "config/flow_token_mainnet.json"
                }
            )
            Gson().fromJson(text, FlowCoin::class.java)?.let {
                coinList.add(0, it)
            }
        } catch (e: Exception) {
            logd(TAG, "add flow failure")
        }
    }

    fun coinList() = coinList.toList()

    fun getCoin(symbol: String) = coinList.firstOrNull { it.symbol.lowercase() == symbol.lowercase() }

    fun getEnabledCoinList() = coinList.toList().filter { TokenStateManager.isTokenAdded(it.address) }

    private fun getTokenListUrl(): String {
        return "https://raw.githubusercontent.com/Outblock/token-list-jsons/outblock/jsons/${chainNetWorkString()}/${getTokenType()}/${getFileName()}"
    }

    private fun getFileName(): String {
        return if (isPreviewnet() || WalletManager.isEVMAccountSelected()) {
            "default.json"
        } else {
            if (isDev()) {
                "dev.json"
            } else {
                "default.json"
            }
        }
    }

    private fun getTokenType(): String {
        return if (WalletManager.isEVMAccountSelected()) {
            "evm"
        } else {
            "flow"
        }
    }
}

@Parcelize
data class TokenList(
    @SerializedName("tokens")
    val tokens: List<FlowCoin>
): Parcelable

@Parcelize
data class FlowCoin(
    @SerializedName("chainId")
    val chainId: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("contractName")
    val contractName: String?,
    @SerializedName("path")
    val storagePath: FlowCoinStoragePath?,
    @SerializedName("decimals")
    val decimal: Int,
    @SerializedName("logoURI")
    val icon: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("extensions")
    val extensions: FlowCoinExtensions?,
    @SerializedName("flowIdentifier")
    val flowIdentifier: String?,
    @SerializedName("evmAddress")
    val evmAddress: String?
) : Parcelable {

    fun icon(): String {
        return if (icon.endsWith(".svg")) {
            icon.svgToPng()
        } else {
            icon
        }
    }

    fun contractName() = contractName ?: ""

    fun website() = extensions?.website ?: ""

    fun isFlowCoin() = symbol.lowercase() == SYMBOL_FLOW

    fun isCOABridgeCoin() = flowIdentifier.isNullOrBlank().not()

    fun canBridgeToCOA() = evmAddress.isNullOrBlank().not()

    companion object {
        const val SYMBOL_FLOW = "flow"
        const val SYMBOL_FUSD = "fusd"
        const val SYMBOL_STFLOW = "stFlow"
        const val SYMBOL_BLT = "blt"
        const val SYMBOL_USDC = "usdc"
        const val SYMBOL_MY = "my"
        const val SYMBOL_THUL = "thul"
        const val SYMBOL_STARLY = "STARLY"
    }
}

@Parcelize
class FlowCoinExtensions(
    @SerializedName("twitter")
    val twitter: String?,
    @SerializedName("coingeckoId")
    val coingeckoId: String?,
    @SerializedName("documentation")
    val documentation: String?,
    @SerializedName("website")
    val website: String?,
    @SerializedName("displaySource")
    val displaySource: String?,
    @SerializedName("pathSource")
    val pathSource: String?,
) : Parcelable

@Parcelize
class FlowCoinAddress(
    @SerializedName("mainnet")
    val mainnet: String?,
    @SerializedName("testnet")
    val testnet: String?,
    @SerializedName("previewnet")
    val previewnet: String?,
) : Parcelable

@Parcelize
class FlowCoinStoragePath(
    @SerializedName("balance")
    val balance: String,
    @SerializedName("vault")
    val vault: String,
    @SerializedName("receiver")
    val receiver: String,
) : Parcelable

fun FlowCoin.formatCadence(cadence: String): String {
    return cadence.replace("<Token>", contractName())
        .replace("<TokenAddress>", address)
        .replace("<TokenReceiverPath>", storagePath?.receiver ?: "")
        .replace("<TokenBalancePath>", storagePath?.balance ?: "")
        .replace("<TokenStoragePath>", storagePath?.vault ?: "")
}

