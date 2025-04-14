package com.flowfoundation.wallet.manager.config

import com.flowfoundation.wallet.BuildConfig
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.notification.WalletNotificationManager
import com.flowfoundation.wallet.utils.NETWORK_TESTNET
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.isFreeGasPreferenceEnable
import com.flowfoundation.wallet.utils.isTesting
import com.flowfoundation.wallet.utils.safeRun

suspend fun isGasFree() = AppConfig.isFreeGas() && isFreeGasPreferenceEnable()

object AppConfig {

    private var config: Config? = null
    private var flowAddressRegistry: FlowAddressRegistry? = null

    fun isFreeGas() = config().getFeatures().freeGas

    fun payer() = if (isTestnet()) config().getPayer().testnet else config().getPayer().mainnet

    fun walletConnectEnable() = config().getFeatures().walletConnect

    fun isInAppSwap() = isDev() || isTesting() || (config().getFeatures().swap ?: false)

    fun isInAppBuy() = isDev() || isTesting() || (config().getFeatures().onRamp ?: false)

    fun showDappList() = isDev() || isTesting() || (config().getFeatures().appList ?: false)

    fun useInAppBrowser() = isDev() || isTesting() || (config().getFeatures().browser ?: false)

    fun showNFTTransfer() = isDev() || isTesting() || (config().getFeatures().nftTransfer ?: false)

    fun showTXWarning() = isDev() || isTesting() || (config().getFeatures().txWarning ?: false)

    fun coverBridgeFee() = config().getFeatures().coverBridgeFee ?: false

    fun bridgeFeePayer() = if (isTestnet()) config().getBridgeFeePayer().testnet else config().getBridgeFeePayer().mainnet

    fun addressRegistry(network: Int): Map<String, String> {
        return when (network) {
            NETWORK_TESTNET -> flowAddressRegistry().testnet
            else -> flowAddressRegistry().mainnet
        }
    }

    fun sync() {
        ioScope {
            reloadConfig()
            reloadNotification()
            reloadFlowAddressRegistry()
        }
    }

    fun isVersionUpdateRequired(): Boolean {
        val latestVersionParts = config().versionProd.split(".")
        val localVersion = BuildConfig.VERSION_NAME.replace(Regex("[^0-9.]"), "")
        val localParts = localVersion.split(".")
        val maxLength = maxOf(latestVersionParts.size, localParts.size)

        for (i in 0 until maxLength) {
            val localPart = localParts.getOrNull(i)?.toIntOrNull() ?: 0
            val latestPart = latestVersionParts.getOrNull(i)?.toIntOrNull() ?: 0

            if (localPart < latestPart) {
                return true
            } else if (localPart > latestPart) {
                return false
            }
        }
        return false
    }

    private fun reloadNotification() {
        val text = Firebase.remoteConfig.getString("news")
        WalletNotificationManager.setNotificationList(text)
    }

    private fun reloadConfig(): Config {
        val text = Firebase.remoteConfig.getString("a_config")
        safeRun {
            config = Gson().fromJson(text, Config::class.java)
        }
        return config!!
    }

    private fun reloadFlowAddressRegistry(): FlowAddressRegistry {
        val text = Firebase.remoteConfig.getString("contract_address")
        safeRun {
            flowAddressRegistry = Gson().fromJson(text, FlowAddressRegistry::class.java)
        }
        return flowAddressRegistry!!
    }

    private fun config() = config ?: reloadConfig()

    private fun flowAddressRegistry() = flowAddressRegistry ?: reloadFlowAddressRegistry()
}

private data class Config(
    @SerializedName("prod")
    val prod: Prod,
    @SerializedName("staging")
    val staging: Staging,
    @SerializedName("version")
    val version: String,
    @SerializedName("version_prod")
    val versionProd: String
) {
    fun isStagingVersion(): Boolean {
        val latestVersionClean = BuildConfig.VERSION_NAME.replace(Regex("[^0-9.]"), "")

        val currentParts = version.split(".")
        val latestParts = latestVersionClean.split(".")

        val maxLength = maxOf(currentParts.size, latestParts.size)

        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            val latestPart = latestParts.getOrNull(i)?.toIntOrNull() ?: 0

            if (currentPart < latestPart) {
                return true
            } else if (currentPart > latestPart) {
                return false
            }
        }
        return false
    }

    fun getFeatures(): Features {
        return if (isStagingVersion()) {
            staging.features
        } else {
            prod.features
        }
    }

    fun getPayer(): Payer {
        return if (isStagingVersion()) {
            staging.payer
        } else {
            prod.payer
        }
    }

    fun getBridgeFeePayer(): Payer {
        return if (isStagingVersion()) {
            staging.bridgeFeePayer
        } else {
            prod.bridgeFeePayer
        }
    }
}

private data class Prod(
    @SerializedName("features")
    val features: Features,
    @SerializedName("payer")
    val payer: Payer,
    @SerializedName("bridgeFeePayer")
    val bridgeFeePayer: Payer
)

private data class Staging(
    @SerializedName("features")
    val features: Features,
    @SerializedName("payer")
    val payer: Payer,
    @SerializedName("bridgeFeePayer")
    val bridgeFeePayer: Payer
)

private data class Features(
    @SerializedName("free_gas")
    val freeGas: Boolean,
    @SerializedName("wallet_connect")
    val walletConnect: Boolean,
    @SerializedName("swap")
    val swap: Boolean?,
    @SerializedName("on_ramp")
    val onRamp: Boolean?,
    @SerializedName("app_list")
    val appList: Boolean?,
    @SerializedName("browser")
    val browser: Boolean?,
    @SerializedName("nft_transfer")
    val nftTransfer: Boolean?,
    @SerializedName("tx_warning_prediction")
    val txWarning: Boolean?,
    @SerializedName("cover_bridge_fee")
    val coverBridgeFee: Boolean?,
)

private data class Payer(
    @SerializedName("mainnet")
    val mainnet: PayerNet,
    @SerializedName("testnet")
    val testnet: PayerNet
)

data class PayerNet(
    @SerializedName("address")
    val address: String,
    @SerializedName("keyId")
    val keyId: Int
)

private data class FlowAddressRegistry(
    @SerializedName("mainnet")
    val mainnet: Map<String, String>,
    @SerializedName("testnet")
    val testnet: Map<String, String>
)
