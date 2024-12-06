package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.flowjvm.Cadence
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTListFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTListToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTListFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTListToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceFundFlowToCOAAccount
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryEVMAddress
import com.flowfoundation.wallet.manager.flowjvm.cadenceWithdrawTokenFromCOAAccount
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.transaction.isFailed
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.TransferAccountType
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.wallet.toAddress
import com.google.gson.annotations.SerializedName
import com.nftco.flow.sdk.FlowTransactionStatus
import kotlinx.serialization.Serializable
import java.math.BigDecimal

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
        return Cadence.CADENCE_CREATE_COA_ACCOUNT.getScript().isNotEmpty()
    }

    private fun canFetchEVMAddress(): Boolean {
        return Cadence.CADENCE_QUERY_COA_EVM_ADDRESS.getScript().isNotEmpty()
    }

    fun updateEVMAddress() {
        if (evmAddressMap.isEmpty() || getEVMAddress().isNullOrBlank()) {
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
            logd(TAG, "fetchEVMAddress address::$address")
            if (address.isNullOrEmpty()) {
                callback?.invoke(false)
            } else {
                val networkAddress = getNetworkAddress()
                if (networkAddress != null) {
                    evmAddressMap[networkAddress] = address.toAddress()
                    AccountManager.updateEVMAddressInfo(evmAddressMap.toMutableMap())
                    callback?.invoke(true)
                } else {
                    callback?.invoke(false)
                }
            }
        }
    }

    private fun getNetworkAddress(network: String? = chainNetWorkString()): String? {
       return WalletManager.wallet()?.chainNetworkWallet(network)?.address()
    }

    fun showEVMAccount(network: String?): Boolean {
        return evmAddressMap[getNetworkAddress(network)].isNullOrBlank().not()
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

    fun getEVMAddress(network: String? = chainNetWorkString()): String? {
        val address = evmAddressMap[getNetworkAddress(network)]
        return if (address.equals("0x")) {
            return null
        } else {
            address
        }
    }

    fun isEVMWalletAddress(address: String): Boolean {
        return evmAddressMap.values.firstOrNull { it == address } != null
    }

    suspend fun moveFlowToken(
        amount: BigDecimal,
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
        coin: FlowCoin, amount: BigDecimal, isFundToEVM: Boolean, callback:
            (isSuccess: Boolean) -> Unit
    ) {
        try {
            val txId = if (isFundToEVM) {
                cadenceBridgeFTToEvm(coin.getFTIdentifier(), amount)
            } else {
                val decimalAmount = amount.movePointRight(coin.decimal)
                cadenceBridgeFTFromEvm(coin.getFTIdentifier(), decimalAmount)
            }
            if (txId.isNullOrBlank()) {
                logd(TAG, "bridge ft failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "bridge ft success")
                    callback.invoke(true)
                } else if (result.isFailed()) {
                    logd(TAG, "bridge ft failed")
                    callback.invoke(false)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge ft failed")
            e.printStackTrace()
        }
    }

    suspend fun moveNFT(nft: Nft, isMoveToEVM: Boolean, callback: (isSuccess: Boolean) -> Unit) {
        try {
            val id = nft.id
            val txId = if (isMoveToEVM) {
                cadenceBridgeNFTToEvm(nft.getNFTIdentifier(), id)
            } else {
                cadenceBridgeNFTFromEvm(nft.getNFTIdentifier(), id)
            }
            val parentAddress = WalletManager.wallet()?.walletAddress().orEmpty()
            MixpanelManager.transferNFT(
                if (isMoveToEVM) parentAddress else getEVMAddress().orEmpty(),
                if(isMoveToEVM) getEVMAddress().orEmpty() else parentAddress,
                nft.getNFTIdentifier(),
                txId.orEmpty(),
                if (isMoveToEVM) TransferAccountType.FLOW else TransferAccountType.COA,
                if (isMoveToEVM) TransferAccountType.COA else TransferAccountType.FLOW,
                true
            )
            postTransaction(nft, txId, callback)
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge nft ${if (isMoveToEVM) "to" else "from"} evm failed")
            e.printStackTrace()
        }
    }

    suspend fun moveChildNFT(nft: Nft, childAddress: String, isMoveToEVM: Boolean, callback: (isSuccess: Boolean) -> Unit) {
        try {
            val id = nft.id
            val txId = if (isMoveToEVM) {
                cadenceBridgeChildNFTToEvm(nft.getNFTIdentifier(), id, childAddress)
            } else {
                cadenceBridgeChildNFTFromEvm(nft.getNFTIdentifier(), id, childAddress)
            }
            MixpanelManager.transferNFT(
                if (isMoveToEVM) childAddress else getEVMAddress().orEmpty(),
                if (isMoveToEVM) getEVMAddress().orEmpty() else childAddress,
                nft.getNFTIdentifier(),
                txId.orEmpty(),
                if (isMoveToEVM) TransferAccountType.CHILD else TransferAccountType.COA,
                if (isMoveToEVM) TransferAccountType.COA else TransferAccountType.CHILD,
                true
            )
            postTransaction(nft, txId, callback)
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge child nft ${if (isMoveToEVM) "to" else "from"} evm failed")
            e.printStackTrace()
        }
    }

    suspend fun moveNFTList(
        nftIdentifier: String,
        idList: List<String>,
        isMoveToEVM: Boolean,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val txId = if (isMoveToEVM) {
                cadenceBridgeNFTListToEvm(nftIdentifier, idList)
            } else {
                cadenceBridgeNFTListFromEvm(nftIdentifier, idList)
            }
            val parentAddress = WalletManager.wallet()?.walletAddress().orEmpty()
            MixpanelManager.transferNFT(
                if (isMoveToEVM) parentAddress else getEVMAddress().orEmpty(),
                if (isMoveToEVM) getEVMAddress().orEmpty() else parentAddress,
                nftIdentifier,
                txId.orEmpty(),
                if (isMoveToEVM) TransferAccountType.FLOW else TransferAccountType.COA,
                if (isMoveToEVM) TransferAccountType.COA else TransferAccountType.FLOW,
                true
            )
            if (txId.isNullOrBlank()) {
                logd(TAG, "bridge nft list failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "bridge nft list success")
                    callback.invoke(true)
                } else if (result.isFailed()) {
                    logd(TAG, "bridge nft list failed")
                    callback.invoke(false)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge nft list failed")
            e.printStackTrace()
        }
    }

    suspend fun moveChildNFTList(
        nftIdentifier: String,
        idList: List<String>,
        childAddress: String,
        isMoveToEVM: Boolean,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val txId = if (isMoveToEVM) {
                cadenceBridgeChildNFTListToEvm(nftIdentifier, idList, childAddress)
            } else {
                cadenceBridgeChildNFTListFromEvm(nftIdentifier, idList, childAddress)
            }
            MixpanelManager.transferNFT(
                if (isMoveToEVM) childAddress else getEVMAddress().orEmpty(),
                if (isMoveToEVM) getEVMAddress().orEmpty() else childAddress,
                nftIdentifier,
                txId.orEmpty(),
                if (isMoveToEVM) TransferAccountType.CHILD else TransferAccountType.COA,
                if (isMoveToEVM) TransferAccountType.COA else TransferAccountType.CHILD,
                true
            )
            if (txId.isNullOrBlank()) {
                logd(TAG, "bridge child nft list failed")
                callback.invoke(false)
                return
            }
            TransactionStateWatcher(txId).watch { result ->
                if (result.isExecuteFinished()) {
                    logd(TAG, "bridge child nft list success")
                    callback.invoke(true)
                } else if (result.isFailed()) {
                    logd(TAG, "bridge child nft list failed")
                    callback.invoke(false)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "bridge child nft list failed")
            e.printStackTrace()
        }
    }

    private suspend fun fundFlowToEVM(amount: BigDecimal, callback: (isSuccess: Boolean) -> Unit) {
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
                } else if (result.isFailed()) {
                    logd(TAG, "fund flow to evm failed")
                    callback.invoke(false)
                }
            }
        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "fund flow to evm failed")
            e.printStackTrace()
        }
    }

    private suspend fun withdrawFlowFromEVM(amount: BigDecimal, callback: (isSuccess: Boolean) -> Unit) {
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
                } else if (result.isFailed()) {
                    logd(TAG, "withdraw flow from evm failed")
                    callback.invoke(false)
                }
            }

        } catch (e: Exception) {
            callback.invoke(false)
            logd(TAG, "withdraw flow from evm failed")
            e.printStackTrace()
        }
    }

    private fun postTransaction(nft: Nft, txId: String?, callback: (isSuccess: Boolean) -> Unit) {
        callback.invoke(txId != null)
        if (txId.isNullOrBlank()) {
            return
        }
        val transactionState = TransactionState(
            transactionId = txId,
            time = System.currentTimeMillis(),
            state = FlowTransactionStatus.PENDING.num,
            type = TransactionState.TYPE_MOVE_NFT,
            data = nft.uniqueId(),
        )
        TransactionStateManager.newTransaction(transactionState)
        pushBubbleStack(transactionState)
    }
}

@Serializable
data class EVMAddressData(
    @SerializedName("evmAddressMap")
    var evmAddressMap: Map<String, String>? = null
)