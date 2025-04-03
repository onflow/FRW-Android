package com.flowfoundation.wallet.manager.coin

import android.os.Parcelable
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.readTextFromAssets
import com.flowfoundation.wallet.utils.svgToPng
import com.flowfoundation.wallet.wallet.removeAddressPrefix
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
                coinList.addAll(
                    list.tokens.filter { it.address.isNotBlank() }.map { token ->
                        val coinType = if (WalletManager.isEVMAccountSelected()) {
                            FlowCoinType.EVM
                        } else {
                            FlowCoinType.CADENCE
                        }
                        token.copy(type = coinType)
                    }
                )
                if (WalletManager.isEVMAccountSelected()) {
                    addFlowTokenManually()
                    addCustomToken()
                }
            }
        }
    }

    fun addCustomToken() {
        val list = CustomTokenManager.getCurrentCustomTokenList()
        val existingAddresses = coinList.map { it.address.lowercase() }.toSet()
        coinList.addAll(list.map {
            it.toFlowCoin()
        }.filter { it.address !in existingAddresses }.toList())
    }

    fun deleteCustomToken(contractAddress: String) {
        coinList.removeIf { it.address.equals(contractAddress, true)}
    }

    private fun addFlowTokenManually() {
        try {
            val text = readTextFromAssets(
                if (isTestnet()) {
                    "config/flow_token_testnet.json"
                } else {
                    "config/flow_token_mainnet.json"
                }
            )
            Gson().fromJson(text, FlowCoin::class.java)?.let { coin ->
                if(coinList.none { it.address == coin.address }) {
                    coinList.add(0, coin.copy(type = FlowCoinType.EVM))
                }
            }
        } catch (e: Exception) {
            loge(TAG, "manually add flow token failure :: $e")
        }
    }

    fun coinList() = coinList.toList()

    fun getCoinById(contractId: String) = coinList.firstOrNull { it.contractId() == contractId }

    fun getFlowCoin() = coinList.firstOrNull { it.isFlowCoin() }

    fun getFlowCoinContractId() = getFlowCoin()?.contractId().orEmpty()

    fun isFlowCoin(contractId: String) = coinList.any { it.isFlowCoin() && it.contractId().equals(contractId, true) }

    fun getEVMCoin(address: String) = coinList.firstOrNull { it.isSameCoin(address, "")  }

    fun getEnabledCoinList() = coinList.toList().filter { TokenStateManager.isTokenAdded(it) }

    private fun getTokenListUrl(): String {
        return "https://raw.githubusercontent.com/Outblock/token-list-jsons/outblock/jsons/${chainNetWorkString()}/${getTokenType()}/${getFileName()}"
    }

    private fun getFileName(): String {
        return if (WalletManager.isEVMAccountSelected()) {
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
    val chainId: Int?,
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
    val evmAddress: String?,
    @SerializedName("type")
    val type: FlowCoinType? = if (evmAddress.isNullOrBlank()) FlowCoinType.EVM else FlowCoinType.CADENCE,
) : Parcelable {

    fun contractId(): String {
        return "A.${address.removeAddressPrefix()}.${contractName()}"
    }

    fun icon(): String {
        return if (icon.endsWith(".svg")) {
            icon.svgToPng()
        } else {
            icon
        }
    }

    fun isSameCoin(address: String, contractName: String): Boolean {
        return this.address.equals(address, true) && contractName().equals(contractName, true)
    }

    fun isSameCoin(contractId: String): Boolean {
        return this.contractId().equals(contractId, true)
    }

    fun contractName() = contractName ?: ""

    fun website() = extensions?.website ?: ""

    fun isFlowCoin() = symbol.lowercase() == SYMBOL_FLOW

    fun isCOABridgeCoin() = flowIdentifier.isNullOrBlank().not()

    fun canBridgeToCOA() = evmAddress.isNullOrBlank().not()

    fun getFTIdentifier(): String {
        return flowIdentifier ?: "A.${address.removeAddressPrefix()}.${contractName}.Vault"
    }

    companion object {
        const val SYMBOL_FLOW = "flow"
        const val SYMBOL_USDC = "usdc"
    }
}

@Parcelize
enum class FlowCoinType : Parcelable {
    CADENCE,
    EVM
}

@Parcelize
class FlowCoinExtensions(
    @SerializedName("website")
    val website: String?,
) : Parcelable

@Parcelize
class FlowCoinAddress(
    @SerializedName("mainnet")
    val mainnet: String?,
    @SerializedName("testnet")
    val testnet: String?
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

fun FlowCoin.formatCadence(cadenceScript: CadenceScript): String {
    return cadenceScript.getScript().replace("<Token>", contractName())
        .replace("<TokenAddress>", address)
        .replace("<TokenReceiverPath>", storagePath?.receiver ?: "")
        .replace("<TokenBalancePath>", storagePath?.balance ?: "")
        .replace("<TokenStoragePath>", storagePath?.vault ?: "")
}

