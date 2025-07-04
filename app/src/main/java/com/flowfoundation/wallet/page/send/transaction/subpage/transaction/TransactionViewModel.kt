package com.flowfoundation.wallet.page.send.transaction.subpage.transaction

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildFTFromCOA
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildFTToCOA
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTFromEVMToFlow
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTFromCOA
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTFromFlowToEVM
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeFTToCOA
import com.flowfoundation.wallet.manager.flowjvm.cadenceFundFlowToCOAAccount
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendEVMTransaction
import com.flowfoundation.wallet.manager.flowjvm.cadenceTransferFlowToEvmAddress
import com.flowfoundation.wallet.manager.flowjvm.cadenceTransferToken
import com.flowfoundation.wallet.manager.flowjvm.cadenceWithdrawTokenFromCOAAccount
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.TransactionModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.getCurrentCodeLocation
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.flowfoundation.wallet.wallet.toAddress
import com.google.gson.Gson
import org.onflow.flow.models.TransactionStatus
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Numeric
import java.math.BigDecimal

class TransactionViewModel : ViewModel(), FungibleTokenUpdateListener {

    val userInfoLiveData = MutableLiveData<UserInfoData>()

    val amountConvertLiveData = MutableLiveData<BigDecimal>()

    val resultLiveData = MutableLiveData<Boolean>()

    private val addressPattern by lazy { Regex("^0x[a-fA-F0-9]{16}\$") }

    lateinit var transaction: TransactionModel

    init {
        FungibleTokenListManager.addTokenUpdateListener(this)
    }

    fun bindTransaction(transaction: TransactionModel) {
        this.transaction = transaction
    }

    fun load() {
        viewModelIOScope(this) {
            AccountManager.userInfo()?.let { userInfo ->
                WalletManager.selectedWalletAddress().let {
                    userInfoLiveData.postValue(userInfo.apply { address = it })
                }
            }
            FungibleTokenListManager.updateTokenInfo(transaction.coinId)
        }
    }

    fun send(token: FungibleToken) {
        viewModelIOScope(this) {
            val toAddress = transaction.target.address.orEmpty().toAddress()
            val fromAddress = transaction.fromAddress
            MixpanelManager.transferFT(fromAddress, toAddress, token.symbol.orEmpty(), transaction.amount.toPlainString(), token.tokenIdentifier())
                    if (token.isFlowToken()) {
                if (EVMWalletManager.isEVMWalletAddress(fromAddress)) {
                    if (isFlowAddress(toAddress)) {
                        if (WalletManager.isChildAccount(toAddress)) {
                            // COA -> Child
                            val amount = transaction.amount.movePointRight(token.tokenDecimal())
                            bridgeTokenFromCOAToChild(token.tokenIdentifier(), amount, toAddress)
                        } else {
                            // COA -> Flow
                            withdrawFromCOAAccount(transaction.amount, toAddress)
                        }
                    } else {
                        // COA -> EOA/COA
                        evmTransaction(toAddress, transaction.amount)
                    }
                } else {
                    if (isFlowAddress(toAddress)) {
                        // Flow -> Flow
                        transferToken(token)
                    } else if (EVMWalletManager.isEVMWalletAddress(toAddress)) {

                        if (WalletManager.isChildAccount(fromAddress)) {
                            // Child -> COA
                            bridgeTokenFromChildToEVM(token.tokenIdentifier(), transaction.amount, fromAddress)
                        } else {
                            // Flow -> COA
                            fundToCOAAccount(transaction.amount)
                        }
                    } else {
                        // Flow -> EOA
                        transferFlowToEVM(toAddress, transaction.amount)
                    }
                }
            } else if (token.canBridgeToCadence() || token.canBridgeToEVM()) {
                if (EVMWalletManager.isEVMWalletAddress(fromAddress)) {
                    if (isFlowAddress(toAddress)) {
                        val amount = transaction.amount.movePointRight(token.tokenDecimal())
                        if (WalletManager.isChildAccount(toAddress)) {
                            // COA -> Child
                            bridgeTokenFromCOAToChild(token.tokenIdentifier(), amount, toAddress)
                        } else {
                            // COA -> Flow
                            if (WalletManager.isSelfFlowAddress(toAddress)) {
                                val txId = cadenceBridgeFTFromCOA(token.tokenIdentifier(), amount)
                                if (txId.isNullOrBlank()) {
                                    resultLiveData.postValue(false)
                                    ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
                                    return@viewModelIOScope
                                }
                                postTransaction(txId)
                            } else {
                                bridgeTokenToFlow(token.tokenIdentifier(), amount, toAddress)
                            }
                        }
                    } else {
                        // COA -> EOA/COA
                        val amount = transaction.amount.movePointRight(token.tokenDecimal()).toBigInteger()
                        val function = Function(
                            "transfer",
                            listOf(Address(toAddress), Uint256(amount)), emptyList()
                        )
                        val data = Numeric.hexStringToByteArray(FunctionEncoder.encode(function) ?: "")
                        val txId = cadenceSendEVMTransaction(token.tokenAddress().removeAddressPrefix(), 0f.toBigDecimal(), data)
                        if (txId.isNullOrBlank()) {
                            resultLiveData.postValue(false)
                            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
                            return@viewModelIOScope
                        }
                        postTransaction(txId)
                    }
                } else {
                    if (isFlowAddress(toAddress)) {
                        // Flow -> Flow
                        transferToken(token)
                    } else if (EVMWalletManager.isEVMWalletAddress(toAddress)) {
                        if (WalletManager.isChildAccount(fromAddress)) {
                            // Child -> Self COA
                            bridgeTokenFromChildToEVM(token.tokenIdentifier(), transaction.amount, fromAddress)
                        } else {
                            // Flow -> COA
                            val txId = cadenceBridgeFTToCOA(
                                token.tokenIdentifier(),
                                transaction.amount
                            )
                            if (txId.isNullOrBlank()) {
                                resultLiveData.postValue(false)
                                ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
                                return@viewModelIOScope
                            }
                            postTransaction(txId)
                        }
                    } else {
                        // Flow -> EOA
                        val txId = cadenceBridgeFTFromFlowToEVM(
                            token.tokenIdentifier(),
                            transaction.amount,
                            toAddress
                        )
                        if (txId.isNullOrBlank()) {
                            resultLiveData.postValue(false)
                            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
                            return@viewModelIOScope
                        }
                        postTransaction(txId)
                    }
                }
            } else {
                if (EVMWalletManager.isEVMWalletAddress(fromAddress)) {
                    val amount = transaction.amount.movePointRight(token.tokenDecimal()).toBigInteger()
                    val function = Function(
                        "transfer",
                        listOf(Address(toAddress), Uint256(amount)), emptyList()
                    )
                    val data = Numeric.hexStringToByteArray(FunctionEncoder.encode(function) ?: "")
                    val txId = cadenceSendEVMTransaction(token.tokenAddress().removeAddressPrefix(), 0f.toBigDecimal(), data)
                    if (txId.isNullOrBlank()) {
                        resultLiveData.postValue(false)
                        ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
                        return@viewModelIOScope
                    }
                    postTransaction(txId)
                } else {
                    transferToken(token)
                }
            }
        }
    }

    private fun isFlowAddress(address: String): Boolean {
        return addressPattern.matches(address)
    }

    private suspend fun transferToken(token: FungibleToken) {
        val txId = cadenceTransferToken(token, transaction.target.address.orEmpty().toAddress(), transaction.amount.toDouble())
        if (txId.isNullOrBlank()) {
            resultLiveData.postValue(false)
            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
            return
        }
        postTransaction(txId)
    }

    private suspend fun evmTransaction(to: String, amount: BigDecimal) {
        val txId = cadenceSendEVMTransaction(to.removeAddressPrefix(), amount, byteArrayOf())
        if (txId.isNullOrBlank()) {
            resultLiveData.postValue(false)
            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
            return
        }
        postTransaction(txId)
    }

    private suspend fun withdrawFromCOAAccount(amount: BigDecimal, toAddress: String) {
        val txId = cadenceWithdrawTokenFromCOAAccount(amount, toAddress)
        if (txId.isNullOrBlank()) {
            resultLiveData.postValue(false)
            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
            return
        }
        postTransaction(txId)
    }

    private suspend fun fundToCOAAccount(amount: BigDecimal) {
        val txId = cadenceFundFlowToCOAAccount(amount)
        if (txId.isNullOrBlank()) {
            resultLiveData.postValue(false)
            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
            return
        }
        postTransaction(txId)
    }

    private suspend fun transferFlowToEVM(to: String, amount: BigDecimal) {
        val txId = cadenceTransferFlowToEvmAddress(to.removeAddressPrefix(), amount)
        if (txId.isNullOrBlank()) {
            resultLiveData.postValue(false)
            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
            return
        }
        postTransaction(txId)
    }

    private suspend fun bridgeTokenToFlow(
        flowIdentifier: String, amount: BigDecimal, recipient: String
    ) {
        val txId = cadenceBridgeFTFromEVMToFlow(flowIdentifier, amount, recipient)
        if (txId.isNullOrBlank()) {
            resultLiveData.postValue(false)
            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
            return
        }
        postTransaction(txId)
    }

    private suspend fun bridgeTokenFromChildToEVM(
        flowIdentifier: String,
        amount: BigDecimal,
        childAddress: String
    ) {
        val txId = cadenceBridgeChildFTToCOA(flowIdentifier, childAddress, amount)
        if (txId.isNullOrBlank()) {
            resultLiveData.postValue(false)
            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
            return
        }
        postTransaction(txId)
    }

    private suspend fun bridgeTokenFromCOAToChild(
        flowIdentifier: String,
        amount: BigDecimal,
        childAddress: String
    ) {
        val txId = cadenceBridgeChildFTFromCOA(flowIdentifier, childAddress, amount)
        if (txId.isNullOrBlank()) {
            resultLiveData.postValue(false)
            ErrorReporter.reportMoveAssetsError(getCurrentCodeLocation())
            return
        }
        postTransaction(txId)
    }

    private fun postTransaction(txId: String) {
        resultLiveData.postValue(true)
        val transactionState = TransactionState(
            transactionId = txId,
            time = System.currentTimeMillis(),
            state = TransactionStatus.PENDING.ordinal,
            type = TransactionState.TYPE_TRANSFER_COIN,
            data = Gson().toJson(transaction),
        )
        TransactionStateManager.newTransaction(transactionState)
        pushBubbleStack(transactionState)
    }

    override fun onTokenUpdated(token: FungibleToken) {
        if (token.isSameToken(transaction.coinId)) {
            amountConvertLiveData.postValue(token.tokenPrice() * transaction.amount)
        }
    }
}