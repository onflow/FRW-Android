package com.flowfoundation.wallet.page.send.nft.confirm

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTFromEVMToFlow
import com.flowfoundation.wallet.manager.flowjvm.cadenceBridgeNFTFromFlowToEVM
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendEVMTransaction
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTFromChildToFlow
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTFromParentToChild
import com.flowfoundation.wallet.manager.flowjvm.cadenceTransferNft
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.send.nft.NftSendModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.addressPattern
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.flowfoundation.wallet.wallet.toAddress
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
                            // COA -> Flow
                            val contractAddress = if (nft.flowIdentifier != null) {
                                val identifier = nft.flowIdentifier.split(".")
                                if (identifier.size > 1) {
                                    identifier[1].toAddress()
                                } else {
                                    ""
                                }
                            } else {
                                ""
                            }
                            val contractName = if (nft.flowIdentifier != null) {
                                val identifier = nft.flowIdentifier.split(".")
                                if (identifier.size > 2) {
                                    identifier[2]
                                } else {
                                    ""
                                }
                            } else {
                                ""
                            }
                            if (contractAddress.isEmpty() || contractName.isEmpty()) {
                                resultLiveData.postValue(false)
                                return@viewModelIOScope
                            }
                            bridgeNFTFromEVMToFlow(contractAddress, contractName, nft.id, toAddress)
                        } else {
                            // COA -> EOA/COA
                            val function = Function(
                                "safeTransferFrom",
                                listOf(
                                    Address(sendModel.fromAddress), Address(toAddress),
                                    Uint256(nft.id.toBigInteger())), emptyList()
                            )
                            val data = Numeric.hexStringToByteArray(FunctionEncoder.encode(function) ?: "")
                            val txId = cadenceSendEVMTransaction(nft.collectionAddress.removeAddressPrefix(), 0f.toBigDecimal(), data)
                            postTransaction(txId)
                        }
                    } else if (WalletManager.isChildAccount(sendModel.fromAddress)) {
                        if (isFlowAddress(toAddress)) {
                            sendNFTFromChildToFlow()
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
                            val evmAddress = EVMWalletManager.getEVMAddress()
                            val function = Function(
                                "safeTransferFrom",
                                listOf(
                                    Address(evmAddress), Address(toAddress),
                                    Uint256(nft.id.toBigInteger())
                                ), emptyList()
                            )
                            val data =
                                Numeric.hexStringToByteArray(FunctionEncoder.encode(function) ?: "")
                            val collection = NftCollectionConfig.get(nft.collectionAddress, nft.collectionContractName)
                            if (collection?.evmAddress == null || collection.evmAddress.isEmpty()) {
                                resultLiveData.postValue(false)
                                return@viewModelIOScope
                            }
                            val txId = cadenceBridgeNFTFromFlowToEVM(
                                nft.collectionAddress.removeAddressPrefix(),
                                nft.collectionContractName,
                                nft.id,
                                collection.evmAddress.removeAddressPrefix(),
                                data
                            )
                            postTransaction(txId)
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
                        val txId = cadenceSendEVMTransaction(
                            nft.collectionAddress.removeAddressPrefix(), 0f.toBigDecimal(), data)
                        postTransaction(txId)
                    } else if (WalletManager.isChildAccount(sendModel.fromAddress)) {
                        if (isFlowAddress(toAddress)) {
                            sendNFTFromChildToFlow()
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

    private suspend fun sendNFTFromChildToFlow() {
        val collection = NftCollectionConfig.get(sendModel.nft.collectionAddress, sendModel.nft.collectionContractName)
        val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
        val txId = cadenceSendNFTFromChildToFlow(
            sendModel.fromAddress,
            sendModel.target.address!!,
            identifier,
            sendModel.nft
        )
        postTransaction(txId)
    }

    private suspend fun sendNFTFromParentToChild() {
        val collection = NftCollectionConfig.get(sendModel.nft.collectionAddress, sendModel.nft.collectionContractName)
        val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
        val txId = cadenceSendNFTFromParentToChild(
            sendModel.target.address!!,
            identifier,
            sendModel.nft
        )
        postTransaction(txId)
    }

    private suspend fun bridgeNFTFromEVMToFlow(
        nftContractAddress: String, nftContractName:
        String, nftId: String, toFlowAddress: String
    ) {
        val txId =
            cadenceBridgeNFTFromEVMToFlow(nftContractAddress, nftContractName, nftId, toFlowAddress)
        postTransaction(txId)
    }

    private suspend fun sendNFTFromFlowToFlow() {
        val txId = cadenceTransferNft(sendModel.target.address!!, sendModel.nft)
        postTransaction(txId)
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
