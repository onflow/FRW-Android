package com.flowfoundation.wallet.manager.flowjvm

import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.formatCadence
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.flow.CadenceScriptBuilder
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.flowjvm.transaction.sendBridgeTransaction
import com.flowfoundation.wallet.manager.flowjvm.transaction.sendTransaction
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.address.FlowDomainServer
import com.flowfoundation.wallet.utils.error.CadenceError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logv
import com.flowfoundation.wallet.utils.reportCadenceErrorToDebugView
import com.flowfoundation.wallet.wallet.toAddress
import org.onflow.flow.AddressRegistry
import org.onflow.flow.infrastructure.Cadence
import org.onflow.flow.infrastructure.Cadence.Companion.string
import java.math.BigDecimal

private const val TAG = "CadenceExecutor"
const val EVM_GAS_LIMIT = 30000000

suspend fun cadenceQueryAddressByDomainFlowns(domain: String, root: String = "fn"): String? {
    logd(TAG, "cadenceQueryAddressByDomainFlowns(): domain=$domain, root=$root")
    val result = CadenceScript.CADENCE_QUERY_ADDRESS_BY_DOMAIN_FLOWNS.executeCadence {
        arg { string(domain) }
        arg { string(root) }
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
        arg { string(domain) }
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

suspend fun cadenceGetTokenBalanceStorage(): Map<String, BigDecimal>? {
    val walletAddress = WalletManager.selectedWalletAddress()
    logd(TAG, "cadenceGetTokenBalanceStorage() walletAddress: '$walletAddress'")
    
    // Check if we have a valid wallet address before proceeding
    if (walletAddress.isBlank()) {
        logd(TAG, "cadenceGetTokenBalanceStorage: No wallet address available, skipping")
        return null
    }
    
    val formattedAddress = walletAddress.toAddress()
    logd(TAG, "cadenceGetTokenBalanceStorage() formatted address: '$formattedAddress'")
    
    // Additional check to ensure the address is not just "0x" or invalid
    if (formattedAddress == "0x" || formattedAddress.length < 10) {
        logd(TAG, "cadenceGetTokenBalanceStorage: Invalid wallet address format '$formattedAddress', skipping")
        return null
    }
    
    val result = CadenceScript.CADENCE_GET_TOKEN_BALANCE_STORAGE.executeCadence {
        arg { Cadence.address(formattedAddress) }
    }
    logd(TAG, "cadenceGetTokenBalanceStorage response:${result?.encode()}")
    return result?.decode<Map<String, String>>().parseBigDecimalMap()
}

suspend fun cadenceGetAllFlowBalance(list: List<String>): Map<String, BigDecimal>? {
    logd(TAG, "cadenceGetAllFlowBalance()")
    val result = CadenceScript.CADENCE_GET_ALL_FLOW_BALANCE.executeCadence {
        arg { Cadence.array(list.map { string(it) }) }
    }
    logd(TAG, "cadenceGetAllFlowBalance response:${result?.encode()}")
    return result?.decode<Map<String, String>>().parseBigDecimalMap()
}

suspend fun cadenceCheckLinkedAccountTokenListEnabled(): Map<String, Boolean>? {
    val walletAddress = WalletManager.selectedWalletAddress()
    logd(TAG, "cadenceCheckLinkedAccountTokenListEnabled() walletAddress: '$walletAddress'")
    
    // Check if we have a valid wallet address before proceeding
    if (walletAddress.isBlank()) {
        logd(TAG, "cadenceCheckLinkedAccountTokenListEnabled: No wallet address available, skipping")
        return null
    }
    
    val formattedAddress = walletAddress.toAddress()
    logd(TAG, "cadenceCheckLinkedAccountTokenListEnabled() formatted address: '$formattedAddress'")
    
    // Additional check to ensure the address is not just "0x" or invalid
    if (formattedAddress == "0x" || formattedAddress.length < 10) {
        logd(TAG, "cadenceCheckLinkedAccountTokenListEnabled: Invalid wallet address format '$formattedAddress', skipping")
        return null
    }
    
    val result = CadenceScript.CADENCE_CHECK_LINKED_ACCOUNT_TOKEN_LIST_ENABLED.executeCadence {
        arg { Cadence.address(formattedAddress) }
    }
    logd(TAG, "cadenceCheckLinkedAccountTokenListEnabled address:$formattedAddress :: response:${result?.encode()}")
    return result?.decode<Map<String, Boolean>>()
}

suspend fun cadenceQueryTokenListBalanceWithAddress(address: String): Map<String, BigDecimal>? {
    logd(TAG, "cadenceQueryTokenListBalanceWithAddress()")
    val result = CadenceScript.CADENCE_GET_TOKEN_LIST_BALANCE.executeCadence {
        arg { Cadence.address(address) }
    }
    logd(TAG, "cadenceQueryTokenListBalanceWithAddress response:${result?.encode()}")
    return result?.decode<Map<String, String>>().parseBigDecimalMap()
}

suspend fun cadenceQueryTokenBalance(coin: FlowCoin, address: String? = null): BigDecimal? {
    val walletAddress = address ?: WalletManager.selectedWalletAddress()
    logd(TAG, "cadenceQueryTokenBalance() walletAddress: '$walletAddress'")
    
    // Check if we have a valid wallet address before proceeding
    if (walletAddress.isBlank()) {
        logd(TAG, "cadenceQueryTokenBalance: No wallet address available, skipping")
        return null
    }
    
    val formattedAddress = walletAddress.toAddress()
    logd(TAG, "cadenceQueryTokenBalance() formatted address: '$formattedAddress'")
    
    // Additional check to ensure the address is not just "0x" or invalid
    if (formattedAddress == "0x" || formattedAddress.length < 10) {
        logd(TAG, "cadenceQueryTokenBalance: Invalid wallet address format '$formattedAddress', skipping")
        return null
    }
    
    val script = CadenceScript.CADENCE_GET_BALANCE
    val result = coin.formatCadence(script).executeCadence(script.scriptId) {
        arg { Cadence.address(formattedAddress) }
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
    val script = CadenceScript.CADENCE_ADD_TOKEN
    val transactionId = coin.formatCadence(script).transactionByMainWallet(script.scriptId) {}
    logd(TAG, "cadenceEnableToken() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceTransferToken(coin: FlowCoin, toAddress: String, amount: Double): String? {
    logd(TAG, "cadenceTransferToken()")
    val script = CadenceScript.CADENCE_TRANSFER_TOKEN
    val transactionId = coin.formatCadence(script).transactionByMainWallet(script.scriptId) {
        arg { ufix64Safe(BigDecimal(amount)) }
        arg { address(toAddress.toAddress()) }
    }
    logd(TAG, "cadenceTransferToken() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceNftEnabled(nft: NftCollection): String? {
    logd(TAG, "cadenceNftEnabled() nft:${nft.name}")
    val script = CadenceScript.CADENCE_NFT_ENABLE
    val transactionId = nft.formatCadence(script).transactionByMainWallet(script.scriptId) {}
    logd(TAG, "cadenceEnableToken() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceGetNFTBalanceStorage(): Map<String, Int>? {
    val walletAddress = WalletManager.selectedWalletAddress()
    logd(TAG, "cadenceGetNFTBalanceStorage() walletAddress: '$walletAddress'")
    
    // Check if we have a valid wallet address before proceeding
    if (walletAddress.isBlank()) {
        logd(TAG, "cadenceGetNFTBalanceStorage: No wallet address available, skipping")
        return null
    }
    
    val formattedAddress = walletAddress.toAddress()
    logd(TAG, "cadenceGetNFTBalanceStorage() formatted address: '$formattedAddress'")
    
    // Additional check to ensure the address is not just "0x" or invalid
    if (formattedAddress == "0x" || formattedAddress.length < 10) {
        logd(TAG, "cadenceGetNFTBalanceStorage: Invalid wallet address format '$formattedAddress', skipping")
        return null
    }
    
    val result = CadenceScript.CADENCE_GET_NFT_BALANCE_STORAGE.executeCadence {
        arg { Cadence.address(formattedAddress) }
    }
    logd(TAG, "cadenceGetNFTBalanceStorage response:${result?.encode()}")
    return result?.decode<Map<String, Int>>()
}

suspend fun cadenceTransferNft(toAddress: String, nft: Nft): String? {
    logd(TAG, "cadenceTransferNft()")
    val script = if (nft.isNBA()) {
        CadenceScript.CADENCE_NBA_NFT_TRANSFER
    } else {
        CadenceScript.CADENCE_NFT_TRANSFER
    }
    val transactionId = nft.formatCadence(script).transactionByMainWallet(script.scriptId) {
        arg { address(toAddress.toAddress()) }
        arg { uint64(nft.id) }
    }
    logd(TAG, "cadenceTransferNft() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceSendNFTFromParentToChild(
    childAddress: String, identifier: String, nft: Nft): String? {
    logd(TAG, "cadenceSendNFTFromParentToChild()")
    val script = CadenceScript.CADENCE_SEND_NFT_FROM_PARENT_TO_CHILD
    val transactionId = nft.formatCadence(script).transactionByMainWallet(script.scriptId) {
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
    val script = CadenceScript.CADENCE_MOVE_NFT_FROM_CHILD_TO_PARENT
    val transactionId = nft.formatCadence(script).transactionByMainWallet(script.scriptId) {
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
    val script = CadenceScript.CADENCE_MOVE_NFT_LIST_FROM_CHILD_TO_PARENT
    val transactionId = collection.formatCadence(script).transactionByMainWallet(script.scriptId) {
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
    val script = CadenceScript.CADENCE_SEND_NFT_LIST_FROM_PARENT_TO_CHILD
    val transactionId = collection.formatCadence(script).transactionByMainWallet(script.scriptId) {
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
    val script = CadenceScript.CADENCE_SEND_NFT_LIST_FROM_CHILD_TO_CHILD
    val transactionId = collection.formatCadence(script).transactionByMainWallet(script.scriptId) {
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
    val script = CadenceScript.CADENCE_SEND_NFT_FROM_CHILD_TO_FLOW
    val transactionId = nft.formatCadence(script).transactionByMainWallet(script.scriptId) {
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
    val script = CadenceScript.CADENCE_SEND_NFT_FROM_CHILD_TO_CHILD
    val transactionId = nft.formatCadence(script).transactionByMainWallet(script.scriptId) {
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
    val script = CadenceScript.CADENCE_CLAIM_INBOX_TOKEN
    val transactionId = coin.formatCadence(script).transactionByMainWallet(script.scriptId) {
        arg { string(domain) }
        arg { string(root) }
        arg { string(key) }
        arg { ufix64Safe(amount) }
    }
    logd(TAG, "cadenceClaimInboxToken() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceClaimInboxNft(
    domain: String,
    key: String,
    collection: NftCollection,
    itemId: Number,
    root: String = FlowDomainServer.MEOW.domain,
): String? {
    logd(TAG, "cadenceClaimInboxToken()")
    val script = CadenceScript.CADENCE_CLAIM_INBOX_NFT
    val txId = collection.formatCadence(script).transactionByMainWallet(script.scriptId) {
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
        arg { Cadence.ufix64(amount.toDouble()) }
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
        arg { string(address) }
    }
    logd(TAG, "cadenceGetNonce response:${result?.encode()}")
    return result?.parseBigDecimal()
}

suspend fun cadenceGetAssociatedFlowIdentifier(evmContractAddress: String): String? {
    logd(TAG, "cadenceGetAssociatedFlowIdentifier()")
    val result = CadenceScript.CADENCE_GET_ASSOCIATED_FLOW_IDENTIFIER.executeCadence {
        arg { string(evmContractAddress) }
    }
    logd(TAG, "cadenceGetAssociatedFlowIdentifier response:${result?.encode()}")
    return result?.decode<String>()
}

suspend fun cadenceBridgeNFTToEvm(
    nftIdentifier: String,
    nftId: String
): String? {
    logd(TAG, "cadenceBridgeNFTToEvm")
    val transactionId = if (AppConfig.coverBridgeFee()) {
        CadenceScript.CADENCE_BRIDGE_NFT_TO_EVM_WITH_PAYER.transactionWithBridgePayer {
            arg { string(nftIdentifier) }
            arg { uint64(nftId) }
        }
    } else {
        CadenceScript.CADENCE_BRIDGE_NFT_TO_EVM.transactionByMainWallet {
            arg { string(nftIdentifier) }
            arg { uint64(nftId) }
        }
    }
    logd(TAG, "cadenceBridgeNFTToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTListToEvm(
    nftIdentifier: String,
    nftIdList: List<String>
): String? {
    logd(TAG, "cadenceBridgeNFTListToEvm")
    val transactionId = if (AppConfig.coverBridgeFee()) {
        CadenceScript.CADENCE_BRIDGE_NFT_LIST_TO_EVM_WITH_PAYER.transactionWithBridgePayer {
            arg { string(nftIdentifier) }
            arg { array(nftIdList.map { uint64(it) }) }
        }
    } else {
        CadenceScript.CADENCE_BRIDGE_NFT_LIST_TO_EVM.transactionByMainWallet {
            arg { string(nftIdentifier) }
            arg { array(nftIdList.map { uint64(it) }) }
        }
    }
    logd(TAG, "cadenceBridgeNFTListToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTListFromEvm(
    nftIdentifier: String,
    nftIdList: List<String>
): String? {
    logd(TAG, "cadenceBridgeNFTListFromEvm")
    val transactionId = if (AppConfig.coverBridgeFee()) {
        CadenceScript.CADENCE_BRIDGE_NFT_LIST_FROM_EVM_WITH_PAYER.transactionWithBridgePayer {
            arg { string(nftIdentifier) }
            arg { array(nftIdList.map { uint256(it) }) }
        }
    } else {
        CadenceScript.CADENCE_BRIDGE_NFT_LIST_FROM_EVM.transactionByMainWallet {
            arg { string(nftIdentifier) }
            arg { array(nftIdList.map { uint256(it) }) }
        }
    }
    logd(TAG, "cadenceBridgeNFTListFromEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTFromEvm(
    nftIdentifier: String,
    nftId: String
): String? {
    logd(TAG, "cadenceBridgeNFTFromEvm")
    val transactionId = if (AppConfig.coverBridgeFee()) {
        CadenceScript.CADENCE_BRIDGE_NFT_FROM_EVM_WITH_PAYER.transactionWithBridgePayer {
            arg { string(nftIdentifier) }
            arg { uint256(nftId) }
        }
    } else {
        CadenceScript.CADENCE_BRIDGE_NFT_FROM_EVM.transactionByMainWallet {
            arg { string(nftIdentifier) }
            arg { uint256(nftId) }
        }
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
    val transactionId = if (AppConfig.coverBridgeFee()) {
        CadenceScript.CADENCE_BRIDGE_NFT_FROM_FLOW_TO_EVM_WITH_PAYER.transactionWithBridgePayer {
            arg { string(nftIdentifier) }
            arg { uint64(nftId) }
            arg { string(recipient) }
        }
    } else {
        CadenceScript.CADENCE_BRIDGE_NFT_FROM_FLOW_TO_EVM.transactionByMainWallet {
            arg { string(nftIdentifier) }
            arg { uint64(nftId) }
            arg { string(recipient) }
        }
    }
    logd(TAG, "cadenceBridgeNFTFromFlowToEVM transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTFromEVMToFlow(
    nftIdentifier: String,
    nftId: String, recipient: String
): String? {
    logd(TAG, "cadenceBridgeNFTFromEVMToFlow")
    val transactionId = if (AppConfig.coverBridgeFee()) {
        CadenceScript.CADENCE_BRIDGE_NFT_FROM_EVM_TO_FLOW_WITH_PAYER.transactionWithBridgePayer {
            arg { string(nftIdentifier) }
            arg { uint256(nftId) }
            arg { address(recipient) }
        }
    } else {
        CadenceScript.CADENCE_BRIDGE_NFT_FROM_EVM_TO_FLOW.transactionByMainWallet {
            arg { string(nftIdentifier) }
            arg { uint256(nftId) }
            arg { address(recipient) }
        }
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
            AddressRegistry().processScript(
                this,
                chainId = AddressRegistry().defaultChainId
            )
        }"
    )
    return try {
        FlowCadenceApi.executeCadenceScript {
            script { this@executeCadence.addPlatformInfo().trimIndent() }
            block()
        }
    } catch (e: Throwable) {
        val exception = ScriptExecutionException(scriptId, e)
        loge(exception)
        ErrorReporter.reportWithMixpanel(CadenceError.EXECUTE_FAILED, exception)
        MixpanelManager.scriptError(scriptId, e.cause?.message.orEmpty())
        reportCadenceErrorToDebugView(scriptId, e)
        return null
    }
}

class ScriptExecutionException(
    val script: String,
    cause: Throwable
) : Throwable("Error while running script :: \n $script", cause)

suspend fun CadenceScript.transactionByMainWallet(arguments: CadenceArgumentsBuilder.() -> Unit): String? {
    return this.getScript().transactionByMainWallet(this.scriptId, arguments)
}

suspend fun String.transactionByMainWallet(scriptId: String, arguments: CadenceArgumentsBuilder.() -> Unit): String? {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return null
    logd(TAG, "transactionByMainWallet() walletAddress:$walletAddress")
    val args = CadenceArgumentsBuilder().apply { arguments(this) }
    val txId = try {
        sendTransaction {
            args.build().forEach { arg(it) }
            walletAddress(walletAddress)
            script(this@transactionByMainWallet.addPlatformInfo())
            scriptId(scriptId)
        }
    } catch (e: Exception) {
        loge(e)
        null
    }?.apply {
        TransactionStateManager.recordTransactionScript(this, scriptId)
    }
    return txId
}

suspend fun CadenceScript.transactionWithBridgePayer(arguments: CadenceArgumentsBuilder.() -> Unit): String? {
    val walletAddress = WalletManager.wallet()?.walletAddress()?: return null
    logd(TAG, "transactionBridge() walletAddress:$walletAddress")
    val args = CadenceArgumentsBuilder().apply { arguments(this) }
    val txId = try {
        sendBridgeTransaction {
            args.build().forEach { arg(it) }
            walletAddress(walletAddress)
            script(this@transactionWithBridgePayer.getScript().addPlatformInfo())
            payer(AppConfig.bridgeFeePayer().address)
            scriptId(this@transactionWithBridgePayer.scriptId)
        }
    } catch (e: Exception) {
        loge(e)
        null
    }?.apply {
        TransactionStateManager.recordTransactionScript(this, this@transactionWithBridgePayer.scriptId)
    }
    return txId
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

