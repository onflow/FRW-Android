package com.flowfoundation.wallet.manager.coin

import android.os.Parcelable
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import kotlinx.parcelize.Parcelize
import java.util.concurrent.CopyOnWriteArrayList

object FlowCoinListManager {
    private val TAG = FlowCoinListManager::class.java.simpleName
    private const val KEY = "flow_coins"
    private val coinList = CopyOnWriteArrayList<FlowCoin>()

    fun reload() {
        ioScope {
            val jsonStr = Firebase.remoteConfig.getString(KEY)
            logd(TAG, "json:$jsonStr")
            val list = Gson().fromJson<List<FlowCoin>>(jsonStr, object : TypeToken<List<FlowCoin>>() {}.type)
            if (list.isNotEmpty()) {
                coinList.clear()
                coinList.addAll(list.filter { it.address().isNotBlank() })
            }
        }
    }

    fun coinList() = coinList.toList()

    fun getCoin(symbol: String) = coinList.firstOrNull { it.symbol.lowercase() == symbol.lowercase() }

    fun getEnabledCoinList() = coinList.toList().filter { TokenStateManager.isTokenAdded(it.address()) }

}

@Parcelize
data class FlowCoin(
    @SerializedName("name")
    val name: String,
    @SerializedName("address")
    val address: FlowCoinAddress,
    @SerializedName("contract_name")
    val contractName: String,
    @SerializedName("storage_path")
    val storagePath: FlowCoinStoragePath,
    @SerializedName("decimal")
    val decimal: Int?,
    @SerializedName("icon")
    val icon: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("website")
    val website: String?,
) : Parcelable {
    fun address() = when {
        isTestnet() -> address.testnet.orEmpty()
        isPreviewnet() -> address.previewnet.orEmpty()
        else -> address.mainnet.orEmpty()
    }

    fun isFlowCoin() = symbol.lowercase() == SYMBOL_FLOW

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
    return cadence.replace("<Token>", contractName)
        .replace("<TokenAddress>", address())
        .replace("<TokenReceiverPath>", storagePath.receiver)
        .replace("<TokenBalancePath>", storagePath.balance)
        .replace("<TokenStoragePath>", storagePath.vault)
}

