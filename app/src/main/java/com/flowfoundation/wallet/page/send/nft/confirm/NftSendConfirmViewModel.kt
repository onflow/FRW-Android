package com.flowfoundation.wallet.page.send.nft.confirm

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeChildNFTToEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTFromEVMToFlow
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTFromEvm
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTFromFlowToEVM
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendEVMTransaction
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTFromChildToChild
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTFromChildToFlow
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTFromParentToChild
import com.flowfoundation.wallet.manager.flowjvm.cadenceTransferNft
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.TransferAccountType
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.send.nft.NftSendModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.addressPattern
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Numeric

class NftSendConfirmViewModel : ViewModel() {

    val userInfoLiveData = MutableLiveData<UserInfoData>()

    val resultLiveData = MutableLiveData<Boolean>()

    lateinit var sendModel: NftSendModel

    fun bindSendModel(nft: NftSendModel) {
        this.sendModel = nft
    }

    fun load() {
        viewModelIOScope(this) {
            AccountManager.userInfo()?.let { userInfo ->
                WalletManager.selectedWalletAddress().let {
                    userInfoLiveData.postValue(userInfo.apply { address = it })
                }
            }
        }
    }

    fun send() {
        viewModelIOScope(this) {
            sendModel.target.address?.let { toAddress ->
                val nft = sendModel.nft
                if (nft.canBridgeToEVM() || nft.canBridgeToFlow()) {
                    if (EVMWalletManager.isEVMWalletAddress(sendModel.fromAddress)) {
                        if (isFlowAddress(toAddress)) {
                            if (WalletManager.isChildAccount(toAddress)) {
                                // COA -> Child
                                val txId = cadenceBridgeChildNFTFromEvm(nft.getNFTIdentifier(), nft.id, toAddress)
                                postTransaction(txId)
                                trackTransferNFT(nft.getNFTIdentifier(), txId, TransferAccountType.COA, TransferAccountType.CHILD)
                            } else {
                                // COA -> Flow
                                if (WalletManager.isSelfFlowAddress(toAddress)) {
                                    val txId = cadenceBridgeNFTFromEvm(nft.getNFTIdentifier(), nft.id)
                                    postTransaction(txId)
                                } else {
                                    bridgeNFTFromEVMToFlow(nft.getNFTIdentifier(), nft.id, toAddress)
                                }
                            }
                        } else {
                            // COA -> EOA/COA
                            val function = Function(
                                "safeTransferFrom",
                                listOf(
                                    Address(sendModel.fromAddress), Address(toAddress),
                                    Uint256(nft.id.toBigInteger())), emptyList()
                            )
                            val data = Numeric.hexStringToByteArray(FunctionEncoder.encode(function) ?: "")
                            val txId = cadenceSendEVMTransaction(nft.getEVMAddress().orEmpty(), 0f.toBigDecimal(), data)
                            postTransaction(txId)
                            trackTransferNFT(nft.getNFTIdentifier(), txId, TransferAccountType.COA, TransferAccountType.EVM)
                        }
                    } else if (WalletManager.isChildAccount(sendModel.fromAddress)) {
                        if (isFlowAddress(toAddress)) {
                            if (WalletManager.isChildAccount(toAddress)) {
                                sendNFTFromChildToChild()
                            } else {
                                sendNFTFromChildToFlow()
                            }
                        } else if (EVMWalletManager.isEVMWalletAddress(toAddress)) {
                            // Child -> COA
                            val txId = cadenceBridgeChildNFTToEvm(nft.getNFTIdentifier(), nft.id, sendModel.fromAddress)
                            postTransaction(txId)
                            trackTransferNFT(nft.getNFTIdentifier(), txId, TransferAccountType.CHILD,
                                TransferAccountType.COA)
                        }
                    } else {
                        if (isFlowAddress(toAddress)) {
                            if (WalletManager.isChildAccount(toAddress)) {
                                // Parent -> Child
                                sendNFTFromParentToChild()
                            } else {
                                // Flow -> Flow
                                sendNFTFromFlowToFlow()
                            }
                        } else {
                            // Flow -> EOA/COA
                            val txId = cadenceBridgeNFTFromFlowToEVM(
                                nft.getNFTIdentifier(),
                                nft.id,
                                toAddress.removeAddressPrefix()
                            )
                            postTransaction(txId)
                            trackTransferNFT(nft.getNFTIdentifier(), txId, TransferAccountType.FLOW, TransferAccountType.EVM)
                        }
                    }
                } else {
                    if (EVMWalletManager.isEVMWalletAddress(sendModel.fromAddress)) {
                        if (isFlowAddress(toAddress)) {
                            resultLiveData.postValue(false)
                            return@viewModelIOScope
                        }
                        // COA -> EOA/COA
                        val function = Function(
                            "safeTransferFrom",
                            listOf(
                                Address(sendModel.fromAddress), Address(toAddress),
                                Uint256(nft.id.toBigInteger())
                            ), emptyList()
                        )
                        val data =
                            Numeric.hexStringToByteArray(FunctionEncoder.encode(function) ?: "")
                        val txId = cadenceSendEVMTransaction(nft.getEVMAddress().orEmpty(), 0f.toBigDecimal(), data)
                        postTransaction(txId)
                        trackTransferNFT(nft.getNFTIdentifier(), txId, TransferAccountType.COA, TransferAccountType.EVM)
                    } else if (WalletManager.isChildAccount(sendModel.fromAddress)) {
                        if (isFlowAddress(toAddress)) {
                            if (WalletManager.isChildAccount(toAddress)) {
                                sendNFTFromChildToChild()
                            } else {
                                sendNFTFromChildToFlow()
                            }
                        }
                    } else {
                        if (isFlowAddress(toAddress)) {
                            if (WalletManager.isChildAccount(toAddress)) {
                                // Parent -> Child
                                sendNFTFromParentToChild()
                            } else {
                                // Flow -> Flow
                                sendNFTFromFlowToFlow()
                            }
                        } else {
                            resultLiveData.postValue(false)
                        }
                    }
                }
            }
        }
    }

    private fun trackTransferNFT(
        nftIdentifier: String,
        txId: String?,
        from: TransferAccountType,
        to: TransferAccountType
    ) {
        MixpanelManager.transferNFT(
            sendModel.fromAddress,
            sendModel.target.address.orEmpty(),
            nftIdentifier,
            txId.orEmpty(),
            from,
            to,
            false
        )
    }

    private suspend fun sendNFTFromChildToChild() {
        val collection = NftCollectionConfig.get(sendModel.nft.collectionAddress, sendModel.nft.contractName())
        val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
        val txId = cadenceSendNFTFromChildToChild(
            sendModel.fromAddress,
            sendModel.target.address!!,
            identifier,
            sendModel.nft
        )
        postTransaction(txId)
        trackTransferNFT(sendModel.nft.getNFTIdentifier(), txId, TransferAccountType.CHILD,
            TransferAccountType.CHILD)
    }


    private suspend fun sendNFTFromChildToFlow() {
        val collection = NftCollectionConfig.get(sendModel.nft.collectionAddress, sendModel.nft.contractName())
        val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
        val txId = cadenceSendNFTFromChildToFlow(
            sendModel.fromAddress,
            sendModel.target.address!!,
            identifier,
            sendModel.nft
        )
        postTransaction(txId)
        trackTransferNFT(sendModel.nft.getNFTIdentifier(), txId, TransferAccountType.CHILD,
            TransferAccountType.FLOW)

    }

    private suspend fun sendNFTFromParentToChild() {
        val collection = NftCollectionConfig.get(sendModel.nft.collectionAddress, sendModel.nft.contractName())
        val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
        val txId = cadenceSendNFTFromParentToChild(
            sendModel.target.address!!,
            identifier,
            sendModel.nft
        )
        postTransaction(txId)
        trackTransferNFT(sendModel.nft.getNFTIdentifier(), txId, TransferAccountType.FLOW,
            TransferAccountType.CHILD)
    }

    private suspend fun bridgeNFTFromEVMToFlow(nftIdentifier: String, nftId: String, recipient: String) {
        val txId = cadenceBridgeNFTFromEVMToFlow(nftIdentifier, nftId, recipient)
        postTransaction(txId)
        trackTransferNFT(nftIdentifier, txId, TransferAccountType.COA, TransferAccountType.FLOW)
    }

    private suspend fun sendNFTFromFlowToFlow() {
        val txId = cadenceTransferNft(sendModel.target.address!!, sendModel.nft)
        postTransaction(txId)
        trackTransferNFT(sendModel.nft.getNFTIdentifier(), txId, TransferAccountType.FLOW,
            TransferAccountType.FLOW)
    }

    private fun postTransaction(txId: String?) {
        resultLiveData.postValue(txId != null)
        if (txId.isNullOrBlank()) {
            return
        }
        val transactionState = TransactionState(
            transactionId = txId,
            time = System.currentTimeMillis(),
            state = FlowTransactionStatus.PENDING.num,
            type = TransactionState.TYPE_TRANSFER_NFT,
            data = Gson().toJson(sendModel),
        )
        TransactionStateManager.newTransaction(transactionState)
        pushBubbleStack(transactionState)
    }

    private fun isFlowAddress(address: String): Boolean {
        return addressPattern.matches(address)
    }
}
