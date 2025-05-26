package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildFTFromCOA
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildFTToCOA
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTListFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTListToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTFromCOA
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTToCOA
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTListFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTListToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceFundFlowToCOAAccount
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryEVMAddress
import com.flowfoundation.wallet.manager.flowjvm.cadenceTransferToken
import com.flowfoundation.wallet.manager.flowjvm.cadenceWithdrawTokenFromCOAAccount
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.transaction.isFailed
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.TransferAccountType
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.error.CadenceError
import com.flowfoundation.wallet.utils.error.EVMError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.getCurrentCodeLocation
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.wallet.toAddress
import com.google.gson.annotations.SerializedName
import org.onflow.flow.models.TransactionStatus
import kotlinx.serialization.Serializable
import org.web3j.crypto.Keys
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
        return CadenceScript.CADENCE_CREATE_COA_ACCOUNT.getScript().isNotEmpty()
    }

    private fun canFetchEVMAddress(): Boolean {
        return CadenceScript.CADENCE_QUERY_COA_EVM_ADDRESS.getScript().isNotEmpty()
    }

    fun updateEVMAddress() {
        if (evmAddressMap.isEmpty() || getEVMAddress().isNullOrBlank()) {
            fetchEVMAddress()
        }
    }

    private fun toChecksumEVMAddress(evmAddress: String): String {
        return Keys.toChecksumAddress(evmAddress)
    }

    // todo get evm address for each network
    fun fetchEVMAddress(callback: ((isSuccess: Boolean) -> Unit)? = null) {
        if (canFetchEVMAddress().not()) {
            ErrorReporter.reportWithMixpanel(CadenceError.EMPTY, getCurrentCodeLocation())
            callback?.invoke(false)
            return
        }
        logd(TAG, "fetchEVMAddress()")
        ioScope {
            val address = cadenceQueryEVMAddress()
            logd(TAG, "fetchEVMAddress cadence response: '$address'")
            logd(TAG, "fetchEVMAddress address length: ${address?.length ?: 0}")
            if (address.isNullOrEmpty()) {
                ErrorReporter.reportWithMixpanel(EVMError.QUERY_EVM_ADDRESS_FAILED, getCurrentCodeLocation())
                callback?.invoke(false)
            } else {
                val networkAddress = getNetworkAddress()
                logd(TAG, "fetchEVMAddress networkAddress: '$networkAddress'")
                if (networkAddress != null) {
                    val formattedAddress = address.toAddress()
                    logd(TAG, "fetchEVMAddress formatted address: '$formattedAddress'")
                    
                    // Validate the address before storing it
                    if (!isValidEVMAddress(formattedAddress)) {
                        logd(TAG, "fetchEVMAddress received invalid address: '$formattedAddress'")
                        ErrorReporter.reportWithMixpanel(EVMError.QUERY_EVM_ADDRESS_FAILED, getCurrentCodeLocation())
                        callback?.invoke(false)
                        return@ioScope
                    }
                    
                    evmAddressMap[networkAddress] = formattedAddress
                    AccountManager.updateEVMAddressInfo(evmAddressMap.toMutableMap())
                    logd(TAG, "fetchEVMAddress successfully stored address: '$formattedAddress'")
                    callback?.invoke(true)
                } else {
                    ErrorReporter.reportWithMixpanel(EVMError.GET_ADDRESS_FAILED, getCurrentCodeLocation())
                    callback?.invoke(false)
                }
            }
        }
    }

    private fun getNetworkAddress(network: String? = chainNetWorkString()): String? {
        return WalletManager.wallet()?.let {
            AccountManager.get()?.wallet?.chainNetworkWallet(network)?.address()
        }
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
        return if (address.isNullOrBlank() || address == "0x") {
            ErrorReporter.reportWithMixpanel(EVMError.GET_ADDRESS_FAILED, getCurrentCodeLocation())
            return null
        } else {
            val checksumAddress = toChecksumEVMAddress(address)
            // Validate the address format - if it's corrupted, try to refresh it
            if (!isValidEVMAddress(checksumAddress)) {
                logd(TAG, "Detected corrupted EVM address: $checksumAddress, attempting to refresh")
                fetchEVMAddress { success ->
                    if (success) {
                        logd(TAG, "Successfully refreshed EVM address")
                    } else {
                        logd(TAG, "Failed to refresh EVM address")
                    }
                }
                return null
            }
            checksumAddress
        }
    }

    private fun isValidEVMAddress(address: String): Boolean {
        // Check if address matches valid EVM address pattern and doesn't have suspicious patterns
        if (!address.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
            return false
        }
        
        // Check for addresses with too many leading zeros (likely corrupted)
        val hexPart = address.removePrefix("0x")
        val leadingZeros = hexPart.takeWhile { it == '0' }.length
        
        // If more than 30 characters are zeros (out of 40), it's likely corrupted
        if (leadingZeros > 30) {
            logd(TAG, "Address appears corrupted due to excessive leading zeros: $leadingZeros")
            return false
        }
        
        return true
    }

    fun isEVMWalletAddress(address: String): Boolean {
        return evmAddressMap.values.firstOrNull { address != "0x" && it.equals(address, ignoreCase = true)} != null
    }

    suspend fun moveFlowToken(
        coin: FlowCoin,
        amount: BigDecimal,
        fromAddress: String,
        toAddress: String,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        if (isEVMWalletAddress(fromAddress)) {
            if (WalletManager.isChildAccount(toAddress)) {
                // COA -> Linked Account
                val moveAmount = amount.movePointRight(coin.decimal)
                bridgeTokenFromCOAToChild(coin.getFTIdentifier(), moveAmount, toAddress, callback)
            } else {
                // COA -> Parent Flow
                withdrawFlowFromCOA(amount, toAddress, callback)
            }
        } else if (WalletManager.isChildAccount(fromAddress)) {
            if (isEVMWalletAddress(toAddress)) {
                // Linked Account -> COA
                bridgeTokenFromChildToCOA(coin.getFTIdentifier(), amount, fromAddress, callback)
            } else {
                // Linked Account -> Parent Flow / Linked Account
                transferToken(coin, toAddress, amount, callback)
            }
        } else {
            if (isEVMWalletAddress(toAddress)) {
                // Parent Flow -> COA
                fundFlowToCOA(amount, callback)
            } else {
                // Parent Flow -> Linked Account
                transferToken(coin, toAddress, amount, callback)
            }
        }
    }

    suspend fun moveBridgeToken(
        coin: FlowCoin, amount: BigDecimal, fromAddress: String, toAddress: String, callback: (isSuccess: Boolean) -> Unit
    ) {
        if (isEVMWalletAddress(fromAddress)) {
            val moveAmount = amount.movePointRight(coin.decimal)
            if (WalletManager.isChildAccount(toAddress)) {
                // COA -> Linked Account
                bridgeTokenFromCOAToChild(coin.getFTIdentifier(), moveAmount, toAddress, callback)
            } else {
                // COA -> Parent Flow
                bridgeTokenFromCOAToFlow(coin.getFTIdentifier(), moveAmount, callback)
            }
        } else {
            if (isEVMWalletAddress(toAddress)) {
                if (WalletManager.isChildAccount(fromAddress)) {
                    // Linked Account -> COA
                    bridgeTokenFromChildToCOA(coin.getFTIdentifier(), amount, fromAddress, callback)
                } else {
                    // Parent Flow -> COA
                    bridgeTokenFromFlowToCOA(coin.getFTIdentifier(), amount, callback)
                }
            } else {
                // Linked Account / Parent Flow -> Parent Flow / Linked Account
                transferToken(coin, toAddress, amount, callback)
            }
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
                if (isMoveToEVM) getEVMAddress().orEmpty() else parentAddress,
                nft.getNFTIdentifier(),
                txId.orEmpty(),
                if (isMoveToEVM) TransferAccountType.FLOW else TransferAccountType.COA,
                if (isMoveToEVM) TransferAccountType.COA else TransferAccountType.FLOW,
                true
            )
            if (txId.isNullOrBlank()) {
                callback.invoke(false)
                ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
                return
            }
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
            if (txId.isNullOrBlank()) {
                callback.invoke(false)
                ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
                return
            }
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
        executeTransaction(
            action = {
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
                txId
            },
            operationName = "bridge nft list",
            callback = callback
        )
    }

    suspend fun moveChildNFTList(
        nftIdentifier: String,
        idList: List<String>,
        childAddress: String,
        isMoveToEVM: Boolean,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        executeTransaction(
            action = {
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
                txId
            },
            operationName = "bridge child nft list",
            callback = callback
        )
    }

    private suspend fun fundFlowToCOA(amount: BigDecimal, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceFundFlowToCOAAccount(amount) },
            operationName = "fund flow to evm",
            callback = callback
        )
    }

    suspend fun transferToken(coin: FlowCoin, toAddress: String, amount: BigDecimal, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceTransferToken(coin, toAddress, amount.toDouble()) },
            operationName = "transfer token",
            callback = callback
        )
    }

    private suspend fun bridgeTokenFromCOAToFlow(flowIdentifier: String, amount: BigDecimal, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceBridgeFTFromCOA(flowIdentifier, amount) },
            operationName = "bridge token from coa to flow",
            callback = callback
        )
    }

    private suspend fun bridgeTokenFromChildToCOA(flowIdentifier: String, amount: BigDecimal,
                                                  childAddress: String, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceBridgeChildFTToCOA(flowIdentifier, childAddress, amount) },
            operationName = "bridge token from child to coa",
            callback = callback
        )
    }

    private suspend fun bridgeTokenFromFlowToCOA(flowIdentifier: String, amount: BigDecimal, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceBridgeFTToCOA(flowIdentifier, amount) },
            operationName = "bridge token from flow to coa",
            callback = callback
        )
    }

    private suspend fun bridgeTokenFromCOAToChild(flowIdentifier: String, amount: BigDecimal,
                                                  toAddress: String, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceBridgeChildFTFromCOA(flowIdentifier, toAddress, amount) },
            operationName = "bridge token from coa to child",
            callback = callback
        )
    }

    private suspend fun withdrawFlowFromCOA(amount: BigDecimal, toAddress: String, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceWithdrawTokenFromCOAAccount(amount, toAddress) },
            operationName = "withdraw flow from evm",
            callback = callback
        )
    }

    private suspend inline fun executeTransaction(
        crossinline action: suspend () -> String?,
        operationName: String,
        crossinline callback: (Boolean) -> Unit
    ) {
        try {
            val txId = action()
            if (txId.isNullOrBlank()) {
                logd(TAG, "$operationName failed")
                ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation(operationName))
                callback(false)
                return
            }

            TransactionStateWatcher(txId).watch { result ->
                when {
                    result.isExecuteFinished() -> {
                        logd(TAG, "$operationName success")
                        callback(true)
                    }
                    result.isFailed() -> {
                        logd(TAG, "$operationName failed")
                        callback(false)
                    }
                }
            }
        } catch (e: Exception) {
            logd(TAG, "$operationName failed :: ${e.message}")
            callback(false)
        }
    }

    private fun postTransaction(nft: Nft, txId: String, callback: (isSuccess: Boolean) -> Unit) {
        callback.invoke(true)
        val transactionState = TransactionState(
            transactionId = txId,
            time = System.currentTimeMillis(),
            state = TransactionStatus.PENDING.ordinal,
            type = TransactionState.TYPE_MOVE_NFT,
            data = nft.uniqueId(),
        )
        TransactionStateManager.newTransaction(transactionState)
        pushBubbleStack(transactionState)
    }

    fun clearCorruptedEVMAddress(network: String? = chainNetWorkString()) {
        logd(TAG, "clearCorruptedEVMAddress called for network: $network")
        val networkAddress = getNetworkAddress(network)
        if (networkAddress != null) {
            val currentAddress = evmAddressMap[networkAddress]
            logd(TAG, "Clearing corrupted EVM address: '$currentAddress' for network address: '$networkAddress'")
            evmAddressMap.remove(networkAddress)
            AccountManager.updateEVMAddressInfo(evmAddressMap.toMutableMap())
            logd(TAG, "Corrupted EVM address cleared, attempting to fetch fresh address")
            fetchEVMAddress { success ->
                logd(TAG, "Fresh EVM address fetch result: $success")
            }
        }
    }

    fun validateAndRefreshEVMAddress(network: String? = chainNetWorkString()): Boolean {
        val currentAddress = getEVMAddress(network)
        if (currentAddress == null || !isValidEVMAddress(currentAddress)) {
            logd(TAG, "EVM address validation failed, clearing and refreshing")
            clearCorruptedEVMAddress(network)
            return false
        }
        return true
    }
}

@Serializable
data class EVMAddressData(
    @SerializedName("evmAddressMap")
    var evmAddressMap: Map<String, String>? = null
)