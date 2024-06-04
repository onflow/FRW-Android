package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.flowjvm.CADENCE_CREATE_COA_ACCOUNT
import com.flowfoundation.wallet.manager.flowjvm.CADENCE_QUERY_COA_EVM_ADDRESS
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTListFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTListToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceFundFlowToCOAAccount
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryEVMAddress
import com.flowfoundation.wallet.manager.flowjvm.cadenceWithdrawTokenFromCOAAccount
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.wallet.toAddress
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

private val TAG = EVMWalletManager::class.java.simpleName

object EVMWalletManager {

    private val evmAddressMap = mutableMapOf<String, String>()

    fun init() {
        evmAddressMap.clear()
        val data = AccountManager.evmAddressData()
        data?.evmAddressMap?.let {
            evmAddressMap.putAll(it)
        }
    }

    fun showEVMEnablePage(): Boolean {
        return canEnableEVM() && haveEVMAddress().not()
    }

    private fun canEnableEVM(): Boolean {
        return isPreviewnet() && CADENCE_CREATE_COA_ACCOUNT.isNotEmpty()
    }

    private fun canFetchEVMAddress(): Boolean {
        return isPreviewnet() && CADENCE_QUERY_COA_EVM_ADDRESS.isNotEmpty()
    }

    fun updateEVMAddress() {
        if (evmAddressMap.isEmpty() || evmAddressMap[chainNetWorkString()].isNullOrBlank()) {
            fetchEVMAddress()
        }
    }

    // todo get evm address for each network
    fun fetchEVMAddress(callback: ((isSuccess: Boolean) -> Unit)? = null) {
        if (canFetchEVMAddress().not()) {
            callback?.invoke(false)
            return
        }
        logd(TAG, "fetchEVMAddress()")
        ioScope {
            val address = cadenceQueryEVMAddress()
            val prefixAddress = address?.toAddress()
            logd(TAG, "fetchEVMAddress address::$prefixAddress")
            if (prefixAddress != null) {
                evmAddressMap[chainNetWorkString()] = prefixAddress
                AccountManager.updateEVMAddressInfo(evmAddressMap)
                callback?.invoke(true)
            } else {
                callback?.invoke(false)
            }
        }
    }

    fun showEVMAccount(network: String?): Boolean {
        return network != null && evmAddressMap[network].isNullOrBlank().not()
    }

    fun getEVMAccount(): EVMAccount? {
        val address = getEVMAddress()
        address ?: return null
        return EVMAccount(
            address = address,
            name = R.string.default_evm_account_name.res2String(),
            icon = "https://firebasestorage.googleapis.com/v0/b/lilico-334404.appspot" +
                    ".com/o/asset%2Feth.png?alt=media&token=1b926945-5459-4aee-b8ef-188a9b4acade",
        )
    }

    fun haveEVMAddress(): Boolean {
        return getEVMAddress().isNullOrBlank().not()
    }

    fun getEVMAddress(): String? {
        return evmAddressMap[chainNetWorkString()]
    }

    fun isEVMWalletAddress(address: String): Boolean {
        return evmAddressMap.values.firstOrNull { it == address } != null
    }

    suspend fun moveFlowToken(
        amount: Float,
        isFundToEVM: Boolean,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        if (isFundToEVM) {
            fundFlowToEVM(amount, callback)
        } else {
            withdrawFlowFromEVM(amount, callback)
        }
    }

    suspend fun moveToken(
        coin: FlowCoin, amount: Float, isFundToEVM: Boolean, callback:
            (isSuccess: Boolean) -> Unit
    ) {
        if (isFundToEVM) {
            bridgeTokenToEVM(coin, amount, callback)
        } else {
            bridgeTokenFromEVM(coin, amount, callback)
        }
    }

    suspend fun moveNFT(nft: Nft, isMoveToEVM: Boolean, callback: (isSuccess: Boolean) -> Unit) {
        if (isMoveToEVM) {
            bridgeNFTToEVM(nft, callback)
        } else {
            bridgeNFTFromEVM(nft, callback)
        }
    }

    suspend fun moveNFTList(
        contractName: String,
        contractAddress: String,
        idList: List<String>,
        isMoveToEVM: Boolean,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        if (isMoveToEVM) {
            bridgeNFTListToEVM(contractName, contractAddress, idList, callback)
        } else {
            bridgeNFTListFromEVM(contractName, contractAddress, idList, callback)
        }
    }

    private suspend fun bridgeNFTListToEVM(
        contractName: String,
        contractAddress: String,
        idList: List<String>,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val txId = cadenceBridgeNFTListToEvm(contractAddress, contractName, idList)
            if (txId.isNullOrBlank()) {
                logd(TAG, "bridge to evm failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "bridge to evm success")
                    callback.invoke(true)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge to evm failed")
            e.printStackTrace()
        }
    }

    private suspend fun bridgeNFTListFromEVM(
        contractName: String,
        contractAddress: String,
        idList: List<String>,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val txId = cadenceBridgeNFTListFromEvm(contractAddress, contractName, idList)
            if (txId.isNullOrBlank()) {
                logd(TAG, "bridge to evm failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "bridge to evm success")
                    callback.invoke(true)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge to evm failed")
            e.printStackTrace()
        }
    }

    private suspend fun bridgeNFTToEVM(nft: Nft, callback: (isSuccess: Boolean) -> Unit) {
        try {
            val contractAddress = "0x8920ffd3d8768daa"
            val contractName = "ExampleNFT"
            val id = nft.id
            val txId = cadenceBridgeNFTToEvm(contractAddress, contractName, id)
            if (txId.isNullOrBlank()) {
                logd(TAG, "bridge to evm failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "bridge to evm success")
                    callback.invoke(true)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge to evm failed")
            e.printStackTrace()
        }
    }

    private suspend fun bridgeNFTFromEVM(nft: Nft, callback: (isSuccess: Boolean) -> Unit) {
        try {
            val contractAddress = "0x8920ffd3d8768daa"
            val contractName = "ExampleNFT"
            val id = nft.id
            val txId = cadenceBridgeNFTFromEvm(contractAddress, contractName, id)
            if (txId.isNullOrBlank()) {
                logd(TAG, "bridge from evm failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "bridge from evm success")
                    callback.invoke(true)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge from evm failed")
            e.printStackTrace()
        }
    }

    private suspend fun bridgeTokenFromEVM(
        coin: FlowCoin, amount: Float, callback: (isSuccess: Boolean)
        -> Unit
    ) {
        try {
            val address = if (!coin.flowIdentifier.isNullOrEmpty()) {
                val identifier = coin.flowIdentifier.split(".")
                if (identifier.size > 1) {
                    identifier[1].toAddress()
                } else {
                    ""
                }
            } else if (coin.evmAddress != null) {
                coin.address
            } else {
                ""
            }

            val contractName = if (!coin.flowIdentifier.isNullOrEmpty()) {
                val identifier = coin.flowIdentifier.split(".")
                if (identifier.size > 2) {
                    identifier[2]
                } else {
                    ""
                }
            } else coin.contractName ?: ""

            if (address.isEmpty() || contractName.isEmpty()) {
                logd(TAG, "bridge from evm failed")
                callback.invoke(false)
                return
            }
            val decimalAmount = amount.toBigDecimal().movePointRight(18)

            val txId = cadenceBridgeFTFromEvm(address, contractName, decimalAmount)
            if (txId.isNullOrBlank()) {
                logd(TAG, "bridge from evm failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "bridge from evm success")
                    callback.invoke(true)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge from evm failed")
            e.printStackTrace()
        }
    }

    private suspend fun bridgeTokenToEVM(
        coin: FlowCoin, amount: Float, callback: (isSuccess: Boolean)
        -> Unit
    ) {
        try {
            val address = if (coin.evmAddress != null) {
                coin.address
            } else if (coin.flowIdentifier != null) {
                val identifier = coin.flowIdentifier.split(".")
                if (identifier.size > 1) {
                    identifier[1].toAddress()
                } else {
                    ""
                }
            } else {
                ""
            }

            val contractName = coin.contractName
                ?: if (coin.flowIdentifier != null) {
                    val identifier = coin.flowIdentifier.split(".")
                    if (identifier.size > 2) {
                        identifier[2]
                    } else {
                        ""
                    }
                } else {
                    ""
                }

            if (address.isEmpty() || contractName.isEmpty()) {
                logd(TAG, "bridge to evm failed")
                callback.invoke(false)
                return
            }

            val txId = cadenceBridgeFTToEvm(address, contractName, amount)
            if (txId.isNullOrBlank()) {
                logd(TAG, "bridge to evm failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "bridge to evm success")
                    callback.invoke(true)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge to evm failed")
            e.printStackTrace()
        }
    }

    private suspend fun fundFlowToEVM(amount: Float, callback: (isSuccess: Boolean) -> Unit) {
        try {
            val txId = cadenceFundFlowToCOAAccount(amount)
            if (txId.isNullOrBlank()) {
                logd(TAG, "fund flow to evm failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "fund flow to evm success")
                    callback.invoke(true)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "fund flow to evm failed")
            e.printStackTrace()
        }
    }

    private suspend fun withdrawFlowFromEVM(amount: Float, callback: (isSuccess: Boolean) -> Unit) {
        try {
            val toAddress = WalletManager.wallet()?.walletAddress() ?: return callback.invoke(false)
            val txId = cadenceWithdrawTokenFromCOAAccount(amount, toAddress)
            if (txId.isNullOrBlank()) {
                logd(TAG, "withdraw flow from evm failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "withdraw flow from evm success")
                    callback.invoke(true)
                }
            }

        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "withdraw flow from evm failed")
            e.printStackTrace()
        }
    }

}

@Serializable
data class EVMAddressData(
    @SerializedName("evmAddressMap")
    var evmAddressMap: Map<String, String>? = null
)