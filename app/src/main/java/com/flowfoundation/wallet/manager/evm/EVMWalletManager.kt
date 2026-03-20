package com.flowfoundation.wallet.manager.evm

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.walletdata.EOAWallet
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
import com.flowfoundation.wallet.manager.flowjvm.cadenceTransferToken
import com.flowfoundation.wallet.manager.flowjvm.cadenceWithdrawTokenFromCOAAccount
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.transaction.isFailed
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.TransferAccountType
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.TransactionModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.getCurrentCodeLocation
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.models.TransactionStatus
import org.web3j.crypto.Keys
import java.math.BigDecimal
import com.google.gson.Gson
import kotlin.text.isNullOrBlank
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.manager.walletdata.COAWallet

private val TAG = EVMWalletManager::class.java.simpleName

object EVMWalletManager {

    fun showEVMEnablePage(): Boolean {
        return canEnableEVM() && haveEVMAddress().not()
    }

    private fun canEnableEVM(): Boolean {
        return CadenceScript.CADENCE_CREATE_COA_ACCOUNT.getScript().isNotEmpty()
    }

    fun toChecksumEVMAddress(evmAddress: String): String {
        return Keys.toChecksumAddress(evmAddress)
    }

    fun haveEVMAddress(): Boolean {
        return getEVMAddress().isNullOrBlank().not()
    }

    fun getEVMAddress(): String? {
        val selectedMainAddress = WalletManager.getCurrentFlowWalletAddress() ?: return null
        return getEVMAddressByAddress(selectedMainAddress)
    }

    fun getEVMAddressByAddress(address: String): String? {
        val evmAddress = AccountManager.walletNodes()
            ?.filterIsInstance<FlowWallet>()
            ?.firstOrNull { it.address.equals(address, true) }
            ?.linkedWallets
            ?.filterIsInstance<COAWallet>()
            ?.firstOrNull()?.address

        return if (evmAddress.isNullOrBlank() || evmAddress == "0x") {
            null
        } else {
            val checksumAddress = toChecksumEVMAddress(evmAddress)
            if (!isValidEVMAddress(checksumAddress)) {
                logd(TAG, "Detected corrupted EVM address: $checksumAddress")
                return null
            }
            checksumAddress
        }
    }

    fun isValidEVMAddress(address: String): Boolean {
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
        val result = AccountManager.walletNodes()
            ?.filterIsInstance<FlowWallet>()
            ?.any { flowWallet ->
                flowWallet.linkedWallets.filterIsInstance<COAWallet>().any {
                    it.address != "0x" && it.address.equals(address, ignoreCase = true)
                }
            } ?: false
        return result
    }

    fun isEOAAddress(address: String): Boolean {
        val result = AccountManager.walletNodes()
            ?.filterIsInstance<EOAWallet>()
            ?.any { it.address.equals(address, ignoreCase = true) }
            ?: false
        logd(TAG, "isEOAAddress result: $result")
        return result
    }


    suspend fun moveFlowToken(
        token: FungibleToken,
        amount: BigDecimal,
        fromAddress: String,
        toAddress: String,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        if (isEVMWalletAddress(fromAddress)) {
            if (WalletManager.isChildAccount(toAddress)) {
                // COA -> Linked Account
                val moveAmount = amount.movePointRight(token.tokenDecimal())
                bridgeTokenFromCOAToChild(token.tokenIdentifier(), moveAmount, toAddress, token, callback)
            } else {
                // COA -> Parent Flow
                withdrawFlowFromCOA(amount, toAddress, token, callback)
            }
        } else if (WalletManager.isChildAccount(fromAddress)) {
            if (isEVMWalletAddress(toAddress)) {
                // Linked Account -> COA
                bridgeTokenFromChildToCOA(token.tokenIdentifier(), amount, fromAddress, token, callback)
            } else {
                // Linked Account -> Parent Flow / Linked Account
                transferToken(token, toAddress, amount, callback)
            }
        } else {
            if (isEVMWalletAddress(toAddress)) {
                // Parent Flow -> COA
                fundFlowToCOA(amount, token, callback)
            } else {
                // Parent Flow -> Linked Account
                transferToken(token, toAddress, amount, callback)
            }
        }
    }

    suspend fun moveBridgeToken(
        token: FungibleToken, amount: BigDecimal, fromAddress: String, toAddress: String, callback: (isSuccess: Boolean) -> Unit
    ) {
        if (isEVMWalletAddress(fromAddress)) {
            val moveAmount = amount.movePointRight(token.tokenDecimal())
            if (WalletManager.isChildAccount(toAddress)) {
                // COA -> Linked Account
                bridgeTokenFromCOAToChild(token.tokenIdentifier(), moveAmount, toAddress, token, callback)
            } else {
                // COA -> Parent Flow
                bridgeTokenFromCOAToFlow(token.tokenIdentifier(), moveAmount, token, callback)
            }
        } else {
            if (isEVMWalletAddress(toAddress)) {
                if (WalletManager.isChildAccount(fromAddress)) {
                    // Linked Account -> COA
                    bridgeTokenFromChildToCOA(token.tokenIdentifier(), amount, fromAddress, token, callback)
                } else {
                    // Parent Flow -> COA
                    bridgeTokenFromFlowToCOA(token.tokenIdentifier(), amount, token, callback)
                }
            } else {
                // Linked Account / Parent Flow -> Parent Flow / Linked Account
                transferToken(token, toAddress, amount, callback)
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
            val parentAddress = WalletManager.getCurrentFlowWalletAddress().orEmpty()
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
        executeNFTTransaction(
            action = {
                val txId = if (isMoveToEVM) {
                    cadenceBridgeNFTListToEvm(nftIdentifier, idList)
                } else {
                    cadenceBridgeNFTListFromEvm(nftIdentifier, idList)
                }
                val parentAddress = WalletManager.getCurrentFlowWalletAddress().orEmpty()
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
        executeNFTTransaction(
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

    private suspend fun fundFlowToCOA(amount: BigDecimal, token: FungibleToken, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceFundFlowToCOAAccount(amount) },
            operationName = "fund flow to evm",
            token = token,
            onTransactionIdReceived = { callback(true) },
            callback = { }
        )
    }

    private suspend fun transferToken(token: FungibleToken, toAddress: String, amount: BigDecimal, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceTransferToken(token, toAddress, amount.toDouble()) },
            operationName = "transfer token",
            token = token,
            onTransactionIdReceived = { callback(true) },
            callback = { }
        )
    }

    private suspend fun bridgeTokenFromCOAToFlow(flowIdentifier: String, amount: BigDecimal, token: FungibleToken, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceBridgeFTFromCOA(flowIdentifier, amount) },
            operationName = "bridge token from coa to flow",
            token = token,
            onTransactionIdReceived = { callback(true) },
            callback = { }
        )
    }

    private suspend fun bridgeTokenFromChildToCOA(flowIdentifier: String, amount: BigDecimal,
                                                  childAddress: String, token: FungibleToken, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceBridgeChildFTToCOA(flowIdentifier, childAddress, amount) },
            operationName = "bridge token from child to coa",
            token = token,
            onTransactionIdReceived = { callback(true) },
            callback = { }
        )
    }

    private suspend fun bridgeTokenFromFlowToCOA(flowIdentifier: String, amount: BigDecimal, token: FungibleToken, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceBridgeFTToCOA(flowIdentifier, amount) },
            operationName = "bridge token from flow to coa",
            token = token,
            onTransactionIdReceived = { callback(true) },
            callback = { }
        )
    }

    private suspend fun bridgeTokenFromCOAToChild(flowIdentifier: String, amount: BigDecimal,
                                                  toAddress: String, token: FungibleToken, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceBridgeChildFTFromCOA(flowIdentifier, toAddress, amount) },
            operationName = "bridge token from coa to child",
            token = token,
            onTransactionIdReceived = { callback(true) },
            callback = { }
        )
    }

    private suspend fun withdrawFlowFromCOA(amount: BigDecimal, toAddress: String, token: FungibleToken, callback: (isSuccess: Boolean) -> Unit) {
        executeTransaction(
            action = { cadenceWithdrawTokenFromCOAAccount(amount, toAddress) },
            operationName = "withdraw flow from evm",
            token = token,
            onTransactionIdReceived = { callback(true) },
            callback = { }
        )
    }

    private suspend inline fun executeTransaction(
        crossinline action: suspend () -> String?,
        operationName: String,
        token: FungibleToken,
        crossinline onTransactionIdReceived: () -> Unit = { },
        crossinline callback: (Boolean) -> Unit
    ) {
        try {
            logd("EVMWalletManager", "executeTransaction starting: $operationName")
            val txId = action()
            logd("EVMWalletManager", "executeTransaction got txId: $txId for $operationName")

            if (txId.isNullOrBlank()) {
                logd(TAG, "$operationName failed")
                ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation(operationName))
                callback(false)
                return
            }

            // Call the early callback when we get transaction ID
            onTransactionIdReceived()

            // Add transaction to mini window (bubble stack) immediately
            val transactionType = when {
                operationName.contains("bridge nft") || operationName.contains("move") -> TransactionState.TYPE_MOVE_NFT
                operationName.contains("transfer token") -> TransactionState.TYPE_TRANSFER_COIN
                operationName.contains("fund") || operationName.contains("withdraw") || operationName.contains("bridge") -> TransactionState.TYPE_TRANSFER_COIN
                else -> TransactionState.TYPE_TRANSACTION_DEFAULT
            }

            // Create proper transaction data
            val transactionData = if (transactionType == TransactionState.TYPE_TRANSFER_COIN) {
                // Create a TransactionModel for TYPE_TRANSFER_COIN so the bubble shows the token icon
                val transactionModel = TransactionModel(
                    amount = BigDecimal.ZERO, // We don't have the exact amount here
                    coinId = token.contractId(),
                    target = AddressBookContact(
                        address = "", // We don't have target address in this context
                        username = "",
                        avatar = "",
                        contactName = ""
                    ),
                    fromAddress = ""
                )
                Gson().toJson(transactionModel)
            } else {
                operationName // Fallback to operation name for non-coin transactions
            }

            logd("EVMWalletManager", "Creating TransactionState for $operationName with type $transactionType")
            val transactionState = TransactionState(
                transactionId = txId,
                time = System.currentTimeMillis(),
                state = TransactionStatus.PENDING.ordinal,
                type = transactionType,
                data = transactionData,
            )
            TransactionStateManager.newTransaction(transactionState)
            logd("EVMWalletManager", "Calling pushBubbleStack for txId: $txId")
            pushBubbleStack(transactionState)

            // Monitor transaction completion in the background
            TransactionStateWatcher(txId).watch { result ->
                when {
                    result.isExecuteFinished() -> {
                        logd(TAG, "$operationName success")
                        logd("EVMWalletManager", "Transaction $txId finished successfully")

                        // Update token list and trigger navigation for token operations
                        if (operationName.contains("fund") || operationName.contains("withdraw") ||
                            operationName.contains("bridge") || operationName.contains("transfer token")) {
                            ioScope {
                                FungibleTokenListManager.updateTokenList()
                            }
                        }

                        callback(true)
                    }
                    result.isFailed() -> {
                        logd(TAG, "$operationName failed")
                        logd("EVMWalletManager", "Transaction $txId failed")
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
        // Create and register the transaction state for bubble monitoring
        val transactionState = TransactionState(
            transactionId = txId,
            time = System.currentTimeMillis(),
            state = TransactionStatus.PENDING.ordinal,
            type = TransactionState.TYPE_MOVE_NFT,
            data = nft.uniqueId(),
        )
        TransactionStateManager.newTransaction(transactionState)
        pushBubbleStack(transactionState)

        // Monitor transaction completion and call callback with actual result
        ioScope {
            TransactionStateWatcher(txId).watch { result ->
                when {
                    result.isExecuteFinished() -> {
                        callback(true)
                    }
                    result.isFailed() -> {
                        callback(false)
                    }
                }
            }
        }
    }

    private suspend inline fun executeNFTTransaction(
        crossinline action: suspend () -> String?,
        operationName: String,
        crossinline callback: (Boolean) -> Unit
    ) {
        try {
            logd("EVMWalletManager", "executeNFTTransaction starting: $operationName")
            val txId = action()
            logd("EVMWalletManager", "executeNFTTransaction got txId: $txId for $operationName")

            if (txId.isNullOrBlank()) {
                logd(TAG, "$operationName failed")
                ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation(operationName))
                callback(false)
                return
            }

            // Add transaction to mini window (bubble stack) immediately
            val transactionType = TransactionState.TYPE_MOVE_NFT

            logd("EVMWalletManager", "Creating TransactionState for $operationName with type $transactionType")
            val transactionState = TransactionState(
                transactionId = txId,
                time = System.currentTimeMillis(),
                state = TransactionStatus.PENDING.ordinal,
                type = transactionType,
                data = operationName, // Store operation name for NFT operations
            )
            TransactionStateManager.newTransaction(transactionState)
            logd("EVMWalletManager", "Calling pushBubbleStack for txId: $txId")
            pushBubbleStack(transactionState)

            // Monitor transaction completion in the background
            TransactionStateWatcher(txId).watch { result ->
                when {
                    result.isExecuteFinished() -> {
                        logd(TAG, "$operationName success")
                        logd("EVMWalletManager", "Transaction $txId finished successfully")
                        callback(true)
                    }
                    result.isFailed() -> {
                        logd(TAG, "$operationName failed")
                        logd("EVMWalletManager", "Transaction $txId failed")
                        callback(false)
                    }
                }
            }
        } catch (e: Exception) {
            logd(TAG, "$operationName failed :: ${e.message}")
            callback(false)
        }
    }
}
