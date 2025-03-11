package com.flowfoundation.wallet.manager.flowjvm

import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.formatCadence
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.flow.CadenceScriptBuilder
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.flowjvm.transaction.sendTransaction
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.address.FlowDomainServer
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logv
import com.flowfoundation.wallet.wallet.toAddress
import com.nftco.flow.sdk.Flow
import org.onflow.flow.infrastructure.Cadence
import java.math.BigDecimal

private const val TAG = "CadenceExecutor"
const val EVM_GAS_LIMIT = 30000000

suspend fun cadenceQueryAddressByDomainFlowns(domain: String, root: String = "fn"): String? {
    logd(TAG, "cadenceQueryAddressByDomainFlowns(): domain=$domain, root=$root")
    val result = CadenceScript.CADENCE_QUERY_ADDRESS_BY_DOMAIN_FLOWNS.executeCadence {
        arg { Cadence.string(domain) }
        arg { Cadence.string(root) }
    }
    logd(
        TAG,
        "cadenceQueryAddressByDomainFlowns domain=$domain, root=$root response:${result?.encode()}"
    )
    return result?.decode<String>()
}

suspend fun cadenceQueryAddressByDomainFind(domain: String): String? {
    logd(TAG, "cadenceQueryAddressByDomainFind()")
    val result = CadenceScript.CADENCE_QUERY_ADDRESS_BY_DOMAIN_FIND.executeCadence {
        arg { Cadence.string(domain) }
    }
    logd(TAG, "cadenceQueryAddressByDomainFind response:${result?.encode()}")
    return result?.decode<String>()
}

suspend fun cadenceCheckTokenEnabled(coin: FlowCoin): Boolean? {
    logd(TAG, "cadenceCheckTokenEnabled() address:${coin.address}")
    val walletAddress = WalletManager.selectedWalletAddress()
    val script = CadenceScript.CADENCE_CHECK_TOKEN_IS_ENABLED
    val result = coin.formatCadence(script).executeCadence(script.scriptId) {
        arg { Cadence.address(walletAddress) }
    }
    logd(TAG, "cadenceCheckTokenEnabled response:${result?.encode()}")
    return result?.decode<Boolean>()
}

suspend fun cadenceCheckTokenListEnabled(): Map<String, Boolean>? {
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    logd(TAG, "cadenceCheckTokenListEnabled()")
    val result = CadenceScript.CADENCE_CHECK_TOKEN_LIST_ENABLED.executeCadence {
        arg { Cadence.address(walletAddress) }
    }
    logd(TAG, "cadenceCheckTokenListEnabled address:$walletAddress :: response:${result?.encode()}")
    return result?.decode<Map<String, Boolean>>()
}

suspend fun cadenceCheckLinkedAccountTokenListEnabled(): Map<String, Boolean>? {
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    logd(TAG, "cadenceCheckLinkedAccountTokenListEnabled()")
    val result = CadenceScript.CADENCE_CHECK_LINKED_ACCOUNT_TOKEN_LIST_ENABLED.executeCadence {
        arg { Cadence.address(walletAddress) }
    }
    logd(TAG, "cadenceCheckLinkedAccountTokenListEnabled address:$walletAddress :: response:${result?.encode()}")
    return result?.decode<Map<String, Boolean>>()
}

suspend fun cadenceQueryTokenListBalance(): Map<String, BigDecimal>? {
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    logd(TAG, "cadenceQueryTokenListBalance()")
    val result = CadenceScript.CADENCE_GET_TOKEN_LIST_BALANCE.executeCadence {
        arg { Cadence.address(walletAddress) }
    }
    logd(TAG, "cadenceQueryTokenListBalance response:${result?.encode()}")
    return result?.decode<Map<String, String>>().parseBigDecimalMap()
}

suspend fun cadenceQueryTokenListBalanceWithAddress(address: String): Map<String, BigDecimal>? {
    logd(TAG, "cadenceQueryTokenListBalanceWithAddress()")
    val result = CadenceScript.CADENCE_GET_TOKEN_LIST_BALANCE.executeCadence {
        arg { Cadence.address(address) }
    }
    logd(TAG, "cadenceQueryTokenListBalanceWithAddress response:${result?.encode()}")
    return result?.decode<Map<String, String>>().parseBigDecimalMap()
}

suspend fun cadenceQueryTokenBalance(coin: FlowCoin): BigDecimal? {
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    logd(TAG, "cadenceQueryTokenBalance()")
    val script = CadenceScript.CADENCE_GET_BALANCE
    val result = coin.formatCadence(script).executeCadence(script.scriptId) {
        arg { Cadence.address(walletAddress) }
    }
    logd(TAG, "cadenceQueryTokenBalance response:${result?.encode()}")
    return result?.parseBigDecimal()
}

suspend fun cadenceQueryTokenBalanceWithAddress(coin: FlowCoin?, address: String?): BigDecimal? {
    if (coin == null || address == null) {
        return null
    }
    logd(TAG, "cadenceQueryTokenBalanceWithAddress()")
    val script = CadenceScript.CADENCE_GET_BALANCE
    val result = coin.formatCadence(script).executeCadence(script.scriptId) {
        arg { Cadence.address(address) }
    }
    logd(
        TAG,
        "cadenceQueryTokenBalanceWithAddress response:${result?.encode()}"
    )
    return result?.parseBigDecimal()
}

suspend fun cadenceEnableToken(coin: FlowCoin): String? {
    logd(TAG, "cadenceEnableToken()")
    val transactionId = coin.formatCadence(CadenceScript.CADENCE_ADD_TOKEN).transactionByMainWallet {}
    logd(TAG, "cadenceEnableToken() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceTransferToken(coin: FlowCoin, toAddress: String, amount: Double): String? {
    logd(TAG, "cadenceTransferToken()")
    val transactionId = coin.formatCadence(CadenceScript.CADENCE_TRANSFER_TOKEN).transactionByMainWallet {
        arg { ufix64Safe(BigDecimal(amount)) }
        arg { address(toAddress.toAddress()) }
    }
    logd(TAG, "cadenceTransferToken() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceNftEnabled(nft: NftCollection): String? {
    logd(TAG, "cadenceNftEnabled() nft:${nft.name}")
    val transactionId = nft.formatCadence(CadenceScript.CADENCE_NFT_ENABLE).transactionByMainWallet {}
    logd(TAG, "cadenceEnableToken() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceCheckNFTListEnabled(): Map<String, Boolean>? {
    logd(TAG, "cadenceCheckNFTListEnabled()")
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    val result = CadenceScript.CADENCE_CHECK_NFT_LIST_ENABLED.executeCadence {
        arg { Cadence.address(walletAddress) }
    }
    logd(TAG, "cadenceCheckNFTListEnabled response:${result?.encode()}")
    return result?.decode<Map<String, Boolean>>()
}

suspend fun cadenceTransferNft(toAddress: String, nft: Nft): String? {
    logd(TAG, "cadenceTransferNft()")
    val transactionId =
        nft.formatCadence(
            if (nft.isNBA()) {
                CadenceScript.CADENCE_NBA_NFT_TRANSFER
            } else {
                CadenceScript.CADENCE_NFT_TRANSFER
            }
        ).transactionByMainWallet {
            arg { address(toAddress.toAddress()) }
            arg { uint64(nft.id) }
        }
    logd(TAG, "cadenceTransferNft() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceSendNFTFromParentToChild(
    childAddress: String, identifier: String, nft: Nft): String? {
    logd(TAG, "cadenceSendNFTFromParentToChild()")
    val transactionId =
        nft.formatCadence(
            CadenceScript.CADENCE_SEND_NFT_FROM_PARENT_TO_CHILD
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { string(identifier) }
            arg { uint64(nft.id) }
        }
    logd(TAG, "cadenceSendNFTFromParentToChild() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceMoveNFTFromChildToParent(childAddress: String, identifier: String, nft: Nft):
        String? {
    logd(TAG, "cadenceMoveNFTFromChildToParent()")
    val transactionId =
        nft.formatCadence(
            CadenceScript.CADENCE_MOVE_NFT_FROM_CHILD_TO_PARENT
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { string(identifier) }
            arg { uint64(nft.id) }
        }
    logd(TAG, "cadenceMoveNFTFromChildToParent() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceMoveNFTListFromChildToParent(
    childAddress: String, identifier: String, collection: NftCollection, nftIdList: List<String>
): String? {
    logd(TAG, "cadenceMoveNFTListFromChildToParent()")
    val transactionId =
        collection.formatCadence(
            CadenceScript.CADENCE_MOVE_NFT_LIST_FROM_CHILD_TO_PARENT
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { string(identifier) }
            arg { array(nftIdList.map { uint64(it) }) }
        }
    logd(TAG, "cadenceMoveNFTListFromChildToParent() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceSendNFTListFromParentToChild(
    childAddress: String, identifier: String, collection: NftCollection, nftIdList: List<String>
): String? {
    logd(TAG, "cadenceSendNFTListFromParentToChild()")
    val transactionId =
        collection.formatCadence(
            CadenceScript.CADENCE_SEND_NFT_LIST_FROM_PARENT_TO_CHILD
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { string(identifier) }
            arg { array(nftIdList.map { uint64(it) }) }
        }
    logd(TAG, "cadenceMoveNFTListFromParentToChild() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceSendNFTListFromChildToChild(
    childAddress: String, toAddress: String,
    identifier: String, collection: NftCollection, nftIdList: List<String>
): String? {
    logd(TAG, "cadenceSendNFTListFromChildToChild()")
    val transactionId =
        collection.formatCadence(
            CadenceScript.CADENCE_SEND_NFT_LIST_FROM_CHILD_TO_CHILD
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { address(toAddress.toAddress()) }
            arg { string(identifier) }
            arg { array(nftIdList.map { uint64(it) }) }
        }
    logd(TAG, "cadenceSendNFTListFromChildToChild() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceSendNFTFromChildToFlow(
    childAddress: String, toAddress: String,
    identifier: String, nft: Nft
): String? {
    logd(TAG, "cadenceSendNFTFromChildToFlow()")
    val transactionId =
        nft.formatCadence(
            CadenceScript.CADENCE_SEND_NFT_FROM_CHILD_TO_FLOW
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { address(toAddress.toAddress()) }
            arg { string(identifier) }
            arg { uint64(nft.id) }
        }
    logd(TAG, "cadenceSendNFTFromChildToFlow() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceSendNFTFromChildToChild(
    childAddress: String, toAddress: String,
    identifier: String, nft: Nft
): String? {
    logd(TAG, "cadenceSendNFTFromChildToChild()")
    val transactionId =
        nft.formatCadence(
            CadenceScript.CADENCE_SEND_NFT_FROM_CHILD_TO_CHILD
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { address(toAddress.toAddress()) }
            arg { string(identifier) }
            arg { uint64(nft.id) }
        }
    logd(TAG, "cadenceSendNFTFromChildToChild() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceClaimInboxToken(
    domain: String,
    key: String,
    coin: FlowCoin,
    amount: BigDecimal,
    root: String = FlowDomainServer.MEOW.domain,
): String? {
    logd(TAG, "cadenceClaimInboxToken()")
    val txid = coin.formatCadence(CadenceScript.CADENCE_CLAIM_INBOX_TOKEN).transactionByMainWallet {
        arg { string(domain) }
        arg { string(root) }
        arg { string(key) }
        arg { ufix64Safe(amount) }
    }
    logd(TAG, "cadenceClaimInboxToken() txid:$txid")
    return txid
}

suspend fun cadenceClaimInboxNft(
    domain: String,
    key: String,
    collection: NftCollection,
    itemId: Number,
    root: String = FlowDomainServer.MEOW.domain,
): String? {
    logd(TAG, "cadenceClaimInboxToken()")
    val txId = collection.formatCadence(CadenceScript.CADENCE_CLAIM_INBOX_NFT)
        .transactionByMainWallet {
        arg { string(domain) }
        arg { string(root) }
        arg { string(key) }
        arg { uint64(itemId) }
    }
    logd(TAG, "cadenceClaimInboxToken() txId:$txId")
    return txId
}

suspend fun cadenceQueryMinFlowBalance(): BigDecimal? {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return null
    logd(TAG, "cadenceQueryMinFlowBalance address:$walletAddress")
    val result = CadenceScript.CADENCE_QUERY_MIN_FLOW_BALANCE.executeCadence {
        arg { Cadence.address(walletAddress) }
    }
    logd(TAG, "cadenceQueryMinFlowBalance response:${result?.encode()}")
    return result?.parseBigDecimal()
}

suspend fun cadenceCreateCOAAccount(): String? {
    logd(TAG, "cadenceCreateCOAAccount()")
    val transactionId = CadenceScript.CADENCE_CREATE_COA_ACCOUNT.transactionByMainWallet {}
    logd(TAG, "cadenceCreateCOAAccount() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceCheckCOALink(address: String): Boolean? {
    logd(TAG, "cadenceCheckCOALink address:$address")
    val result = CadenceScript.CADENCE_CHECK_COA_LINK.executeCadence {
        arg { Cadence.address(address) }
    }
    logd(TAG, "cadenceCheckCOALink response:${result?.encode()}")
    return result?.decode<Boolean>()
}

suspend fun cadenceCOALink(): String? {
    logd(TAG, "cadenceCOALink()")
    val transactionId = CadenceScript.CADENCE_COA_LINK.transactionByMainWallet {}
    logd(TAG, "cadenceCOALink() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceQueryEVMAddress(): String? {
    logd(TAG, "cadenceQueryEVMAddress()")
    val walletAddress = WalletManager.selectedWalletAddress()
    val result = CadenceScript.CADENCE_QUERY_COA_EVM_ADDRESS.executeCadence {
        arg { Cadence.address(walletAddress) }
    }
    logd(TAG, "cadenceQueryEVMAddress response:${result?.encode()}")
    return result?.decode<String>()
}

suspend fun cadenceQueryCOATokenBalance(): BigDecimal? {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return null
    logd(TAG, "cadenceQueryCOATokenBalance address:$walletAddress")
    val result = CadenceScript.CADENCE_QUERY_COA_FLOW_BALANCE.executeCadence {
        arg { Cadence.address(walletAddress) }
    }
    logd(TAG, "cadenceQueryCOATokenBalance response:${result?.encode()}")
    return result?.parseBigDecimal()
}

suspend fun cadenceFundFlowToCOAAccount(amount: BigDecimal): String? {
    logd(TAG, "cadenceFundFlowToCOAAccount()")
    val transactionId = CadenceScript.CADENCE_FUND_COA_FLOW_BALANCE.transactionByMainWallet {
        arg { ufix64Safe(amount) }
    }
    logd(TAG, "cadenceFundFlowToCOAAccount() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceWithdrawTokenFromCOAAccount(amount: BigDecimal, toAddress: String): String? {
    logd(TAG, "cadenceWithdrawTokenFromCOAAccount()")
    val transactionId = CadenceScript.CADENCE_WITHDRAW_COA_FLOW_BALANCE.transactionByMainWallet {
        arg { ufix64Safe(amount) }
        arg { address(toAddress) }
    }
    logd(TAG, "cadenceWithdrawTokenFromCOAAccount() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceTransferFlowToEvmAddress(toAddress: String, amount: BigDecimal): String? {
    logd(TAG, "cadenceSendEVMTransaction")
    val transactionId = CadenceScript.CADENCE_TRANSFER_FLOW_TO_EVM.transactionByMainWallet {
        arg { string(toAddress) }
        arg { ufix64Safe(amount) }
        arg { uint64(EVM_GAS_LIMIT) }
    }
    logd(TAG, "cadenceSendEVMTransaction transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceSendEVMTransaction(
    toAddress: String, amount: BigDecimal, data: ByteArray,
    gasLimit: Int = EVM_GAS_LIMIT
): String? {
    logd(TAG, "cadenceSendEVMTransaction")
    val transactionId = CadenceScript.CADENCE_CALL_EVM_CONTRACT.transactionByMainWallet {
        arg { string(toAddress) }
        arg { ufix64Safe(amount) }
        arg { byteArray(data) }
        arg { uint64(gasLimit) }
    }
    logd(TAG, "cadenceSendEVMTransaction transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceSendEVMV2Transaction(
    toAddress: String, amount: BigDecimal, data: ByteArray,
    gasLimit: Int = EVM_GAS_LIMIT
): String? {
    logd(TAG, "cadenceSendEVMTransaction")
    val transactionId = CadenceScript.CADENCE_CALL_EVM_CONTRACT_V2.transactionByMainWallet {
        arg { string(toAddress) }
        arg { uint256(amount) }
        arg { byteArray(data) }
        arg { uint64(gasLimit) }
    }
    logd(TAG, "cadenceSendEVMTransaction transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceGetNonce(address: String): BigDecimal? {
    logd(TAG, "cadenceGetNonce()")
    val result = CadenceScript.CADENCE_GET_NONCE.executeCadence {
        arg { Cadence.string(address) }
    }
    logd(TAG, "cadenceGetNonce response:${result?.encode()}")
    return result?.parseBigDecimal()
}

suspend fun cadenceGetAssociatedFlowIdentifier(evmContractAddress: String): String? {
    logd(TAG, "cadenceGetAssociatedFlowIdentifier()")
    val result = CadenceScript.CADENCE_GET_ASSOCIATED_FLOW_IDENTIFIER.executeCadence {
        arg { Cadence.string(evmContractAddress) }
    }
    logd(TAG, "cadenceGetAssociatedFlowIdentifier response:${result?.encode()}")
    return result?.decode<String>()
}

suspend fun cadenceBridgeNFTToEvm(
    nftIdentifier: String,
    nftId: String
): String? {
    logd(TAG, "cadenceBridgeNFTToEvm")
    val transactionId = CadenceScript.CADENCE_BRIDGE_NFT_TO_EVM.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { uint64(nftId) }
    }
    logd(TAG, "cadenceBridgeNFTToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTListToEvm(
    nftIdentifier: String,
    nftIdList: List<String>
): String? {
    logd(TAG, "cadenceBridgeNFTListToEvm")
    val transactionId = CadenceScript.CADENCE_BRIDGE_NFT_LIST_TO_EVM.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { array(nftIdList.map { uint64(it) }) }
    }
    logd(TAG, "cadenceBridgeNFTListToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTListFromEvm(
    nftIdentifier: String,
    nftIdList: List<String>
): String? {
    logd(TAG, "cadenceBridgeNFTListFromEvm")
    val transactionId = CadenceScript.CADENCE_BRIDGE_NFT_LIST_FROM_EVM.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { array(nftIdList.map { uint256(it) }) }
    }
    logd(TAG, "cadenceBridgeNFTListFromEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTFromEvm(
    nftIdentifier: String,
    nftId: String
): String? {
    logd(TAG, "cadenceBridgeNFTFromEvm")
    val transactionId = CadenceScript.CADENCE_BRIDGE_NFT_FROM_EVM.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { uint256(nftId) }
    }
    logd(TAG, "cadenceBridgeNFTFromEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeChildNFTToEvm(
    nftIdentifier: String,
    nftId: String,
    childAddress: String
): String? {
    logd(TAG, "cadenceBridgeChildNFTToEvm")
    val transactionId = CadenceScript.CADENCE_BRIDGE_CHILD_NFT_TO_EVM.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { uint64(nftId) }
        arg { address(childAddress) }
    }
    logd(TAG, "cadenceBridgeChildNFTToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeChildNFTFromEvm(
    nftIdentifier: String,
    nftId: String,
    childAddress: String
): String? {
    logd(TAG, "cadenceBridgeChildNFTFromEvm")
    val transactionId = CadenceScript.CADENCE_BRIDGE_CHILD_NFT_FROM_EVM.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { address(childAddress) }
        arg { uint256(nftId) }
    }
    logd(TAG, "cadenceBridgeChildNFTFromEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeChildFTToCOA(
    identifier: String,
    childAddress: String,
    amount: BigDecimal
): String? {
    logd(TAG, "cadenceBridgeChildFTToCOA")
    val transactionId = CadenceScript.CADENCE_BRIDGE_CHILD_FT_TO_EVM.transactionByMainWallet {
        arg { string(identifier) }
        arg { address(childAddress) }
        arg { ufix64Safe(amount) }
    }
    logd(TAG, "cadenceBridgeChildFTToCOA transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeChildFTFromCOA(
    identifier: String,
    childAddress: String,
    amount: BigDecimal
): String? {
    logd(TAG, "cadenceBridgeChildFTFromCOA")
    val transactionId = CadenceScript.CADENCE_BRIDGE_CHILD_FT_FROM_EVM.transactionByMainWallet {
        arg { string(identifier) }
        arg { address(childAddress) }
        arg { uint256(amount.toBigInteger()) }
    }
    logd(TAG, "cadenceBridgeChildFTFromCOA transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeChildNFTListToEvm(
    nftIdentifier: String,
    nftIdList: List<String>,
    childAddress: String
): String? {
    logd(TAG, "cadenceBridgeChildNFTListToEvm")
    val transactionId = CadenceScript.CADENCE_BRIDGE_CHILD_NFT_LIST_TO_EVM.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { address(childAddress) }
        arg { array(nftIdList.map { uint64(it) }) }
    }
    logd(TAG, "cadenceBridgeChildNFTListToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeChildNFTListFromEvm(
    nftIdentifier: String,
    nftIdList: List<String>,
    childAddress: String
): String? {
    logd(TAG, "cadenceBridgeChildNFTListFromEvm")
    val transactionId = CadenceScript.CADENCE_BRIDGE_CHILD_NFT_LIST_FROM_EVM.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { address(childAddress) }
        arg { array(nftIdList.map { uint256(it) }) }
    }
    logd(TAG, "cadenceBridgeChildNFTListFromEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTFromFlowToEVM(
    nftIdentifier: String,
    nftId: String, recipient: String
): String? {
    logd(TAG, "cadenceBridgeNFTFromFlowToEVM")
    val transactionId = CadenceScript.CADENCE_BRIDGE_NFT_FROM_FLOW_TO_EVM.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { uint64(nftId) }
        arg { string(recipient) }
    }
    logd(TAG, "cadenceBridgeNFTFromFlowToEVM transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTFromEVMToFlow(
    nftIdentifier: String,
    nftId: String, recipient: String
): String? {
    logd(TAG, "cadenceBridgeNFTFromEVMToFlow")
    val transactionId = CadenceScript.CADENCE_BRIDGE_NFT_FROM_EVM_TO_FLOW.transactionByMainWallet {
        arg { string(nftIdentifier) }
        arg { uint256(nftId) }
        arg { address(recipient) }
    }
    logd(TAG, "cadenceBridgeNFTFromEVMToFlow transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeFTToCOA(
    flowIdentifier: String,
    amount: BigDecimal
): String? {
    logd(TAG, "cadenceBridgeFTToCOA")
    val transactionId = CadenceScript.CADENCE_BRIDGE_FT_TO_EVM.transactionByMainWallet {
        arg { string(flowIdentifier) }
        arg { ufix64Safe(amount) }
    }
    logd(TAG, "cadenceBridgeFTToCOA transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeFTFromFlowToEVM(
    vaultIdentifier: String, amount: BigDecimal, recipient: String
): String? {
    logd(TAG, "cadenceBridgeFTFromFlowToEVM")
    val transactionId = CadenceScript.CADENCE_BRIDGE_FT_FROM_FLOW_TO_EVM.transactionByMainWallet {
        arg { string(vaultIdentifier) }
        arg { ufix64Safe(amount) }
        arg { string(recipient) }
    }
    logd(TAG, "cadenceBridgeFTFromFlowToEVM transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeFTFromEVMToFlow(
    flowIdentifier: String,
    amount: BigDecimal, recipient: String
): String? {
    logd(TAG, "cadenceBridgeFTFromEVMToFlow")
    val transactionId = CadenceScript.CADENCE_BRIDGE_FT_FROM_EVM_TO_FLOW.transactionByMainWallet {
        arg { string(flowIdentifier) }
        arg { uint256(amount.toBigInteger()) }
        arg { address(recipient) }
    }
    logd(TAG, "cadenceBridgeFTFromEVMToFlow transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeFTFromCOA(
    flowIdentifier: String,
    amount: BigDecimal
): String? {
    logd(TAG, "cadenceBridgeFTFromCOA")
    val transactionId = CadenceScript.CADENCE_BRIDGE_FT_FROM_EVM.transactionByMainWallet {
        arg { string(flowIdentifier) }
        arg { uint256(amount.toBigInteger()) }
    }
    logd(TAG, "cadenceBridgeFTFromCOA transactionId:$transactionId")
    return transactionId
}

suspend fun CadenceScript.executeCadence(block: CadenceScriptBuilder.() -> Unit): Cadence.Value? {
    return this.getScript().executeCadence(this.scriptId, block)
}

suspend fun String.executeCadence(scriptId: String, block: CadenceScriptBuilder.() -> Unit): Cadence.Value? {
    logv(
        TAG,
        "executeScript:\n${
            Flow.DEFAULT_ADDRESS_REGISTRY.processScript(
                this,
                chainId = Flow.DEFAULT_CHAIN_ID
            )
        }"
    )
    return try {
        FlowCadenceApi.executeCadenceScript {
            script { this@executeCadence.addPlatformInfo().trimIndent() }
            block()
        }
    } catch (e: Throwable) {
        loge(ScriptExecutionException(scriptId, e))
        MixpanelManager.scriptError(scriptId, e.cause?.message.orEmpty())
//        reportCadenceErrorToDebugView()
        return null
    }
}

class ScriptExecutionException(
    val script: String,
    cause: Throwable
) : Throwable("Error while running script :: \n $script", cause)

suspend fun CadenceScript.transactionByMainWallet(arguments: CadenceArgumentsBuilder.() -> Unit): String? {
    return this.getScript().transactionByMainWallet(arguments)
}

suspend fun String.transactionByMainWallet(arguments: CadenceArgumentsBuilder.() -> Unit): String? {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return null
    logd(TAG, "transactionByMainWallet() walletAddress:$walletAddress")
    val args = CadenceArgumentsBuilder().apply { arguments(this) }
    return try {
        sendTransaction {
            args.build().forEach { arg(it) }
            walletAddress(walletAddress)
            script(this@transactionByMainWallet.addPlatformInfo())
        }
    } catch (e: Exception) {
        loge(e)
        null
    }
}

fun String.addPlatformInfo(): String {
    return this.replace("<platform_info>", "Android - ${BuildConfig.VERSION_NAME} - ${BuildConfig
        .VERSION_CODE} ${devPrefix()}")
}

private fun devPrefix(): String {
    return if (isDev()) {
        "- dev"
    } else {
        ""
    }
}

