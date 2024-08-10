package com.flowfoundation.wallet.manager.flowjvm

import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.FlowScriptResponse
import com.nftco.flow.sdk.ScriptBuilder
import com.nftco.flow.sdk.cadence.marshall
import com.nftco.flow.sdk.simpleFlowScript
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.formatCadence
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.flowjvm.transaction.sendTransaction
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.address.FlowDomainServer
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logv
import com.flowfoundation.wallet.utils.reportCadenceErrorToDebugView
import com.flowfoundation.wallet.wallet.toAddress
import java.math.BigDecimal

private const val TAG = "CadenceExecutor"
const val EVM_GAS_LIMIT = 30000000

fun cadenceQueryAddressByDomainFlowns(domain: String, root: String = "fn"): String? {
    logd(TAG, "cadenceQueryAddressByDomainFlowns(): domain=$domain, root=$root")
    val result = CADENCE_QUERY_ADDRESS_BY_DOMAIN_FLOWNS.executeCadence {
        arg { marshall { string(domain) } }
        arg { marshall { string(root) } }
    }
    logd(
        TAG,
        "cadenceQueryAddressByDomainFlowns domain=$domain, root=$root response:${String(result?.bytes ?: byteArrayOf())}"
    )
    return result?.parseSearchAddress()
}

fun cadenceQueryDomainByAddressFlowns(address: String): FlowScriptResponse? {
    logd(TAG, "cadenceQueryDomainByAddressFlowns()")
    val result = CADENCE_QUERY_DOMAIN_BY_ADDRESS_FLOWNS.executeCadence {
        arg { address(address) }
    }
    logd(
        TAG,
        "cadenceQueryDomainByAddressFlowns response:${String(result?.bytes ?: byteArrayOf())}"
    )
    return result
}

fun cadenceQueryAddressByDomainFind(domain: String): String? {
    logd(TAG, "cadenceQueryAddressByDomainFind()")
    val result = CADENCE_QUERY_ADDRESS_BY_DOMAIN_FIND.executeCadence {
        arg { marshall { string(domain) } }
    }
    logd(TAG, "cadenceQueryAddressByDomainFind response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseSearchAddress()
}

fun cadenceQueryDomainByAddressFind(address: String): FlowScriptResponse? {
    logd(TAG, "cadenceQueryDomainByAddressFind()")
    val result = CADENCE_QUERY_DOMAIN_BY_ADDRESS_FIND.executeCadence {
        arg { address(address) }
    }
    logd(TAG, "cadenceQueryDomainByAddressFind response:${String(result?.bytes ?: byteArrayOf())}")
    return result
}

fun cadenceCheckTokenEnabled(coin: FlowCoin): Boolean? {
    logd(TAG, "cadenceCheckTokenEnabled() address:${coin.address}")
    val walletAddress = WalletManager.selectedWalletAddress()
    val result = coin.formatCadence(CADENCE_CHECK_TOKEN_IS_ENABLED).executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceCheckTokenEnabled response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseBool()
}

fun cadenceCheckTokenListEnabled(): Map<String, Boolean>? {
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    logd(TAG, "cadenceCheckTokenListEnabled()")
    val result = CADENCE_CHECK_TOKEN_LIST_ENABLED.executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceCheckTokenListEnabled address:$walletAddress :: response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseStringBoolMap()
}

fun cadenceCheckLinkedAccountTokenListEnabled(): Map<String, Boolean>? {
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    logd(TAG, "cadenceCheckLinkedAccountTokenListEnabled()")
    val result = CADENCE_CHECK_LINKED_ACCOUNT_TOKEN_LIST_ENABLED.executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceCheckLinkedAccountTokenListEnabled address:$walletAddress :: response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseStringBoolMap()
}

fun cadenceQueryTokenListBalance(): Map<String, Float>? {
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    logd(TAG, "cadenceQueryTokenListBalance()")
    val result = CADENCE_GET_TOKEN_LIST_BALANCE.executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceQueryTokenListBalance response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseStringFloatMap()
}

fun cadenceQueryTokenBalance(coin: FlowCoin): Float? {
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    logd(TAG, "cadenceQueryTokenBalance()")
    val result = coin.formatCadence(CADENCE_GET_BALANCE).executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceQueryTokenBalance response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseFloat()
}

fun cadenceQueryTokenBalanceWithAddress(coin: FlowCoin?, address: String?): Float? {
    if (coin == null || address == null) {
        return null
    }
    logd(TAG, "cadenceQueryTokenBalanceWithAddress()")
    val result = coin.formatCadence(CADENCE_GET_BALANCE).executeCadence {
        arg { address(address) }
    }
    logd(
        TAG,
        "cadenceQueryTokenBalanceWithAddress response:${String(result?.bytes ?: byteArrayOf())}"
    )
    return result?.parseFloat()
}

suspend fun cadenceEnableToken(coin: FlowCoin): String? {
    logd(TAG, "cadenceEnableToken()")
    val transactionId = coin.formatCadence(CADENCE_ADD_TOKEN).transactionByMainWallet {}
    logd(TAG, "cadenceEnableToken() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceTransferToken(coin: FlowCoin, toAddress: String, amount: Double): String? {
    logd(TAG, "cadenceTransferToken()")
    val transactionId = coin.formatCadence(CADENCE_TRANSFER_TOKEN).transactionByMainWallet {
        arg { ufix64Safe(BigDecimal(amount)) }
        arg { address(toAddress.toAddress()) }
    }
    logd(TAG, "cadenceTransferToken() transactionId:$transactionId")
    return transactionId
}

fun cadenceNftCheckEnabled(nft: NftCollection): Boolean? {
    logd(TAG, "cadenceNftCheckEnabled() nft:${nft.name}")
    val walletAddress = WalletManager.selectedWalletAddress()
    logd(TAG, "cadenceNftCheckEnabled() walletAddress:${walletAddress}")
    val result = nft.formatCadence(CADENCE_NFT_CHECK_ENABLED).executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceNftCheckEnabled response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseBool()
}

suspend fun cadenceNftEnabled(nft: NftCollection): String? {
    logd(TAG, "cadenceNftEnabled() nft:${nft.name}")
    val transactionId = nft.formatCadence(CADENCE_NFT_ENABLE).transactionByMainWallet {}
    logd(TAG, "cadenceEnableToken() transactionId:$transactionId")
    return transactionId
}

fun cadenceCheckNFTListEnabled(): Map<String, Boolean>? {
    logd(TAG, "cadenceCheckNFTListEnabled()")
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    val result = CADENCE_CHECK_NFT_LIST_ENABLED.executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceCheckNFTListEnabled response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseStringBoolMap()
}

fun cadenceNftListCheckEnabled(nfts: List<NftCollection>): List<Boolean>? {
    logd(TAG, "cadenceNftListCheckEnabled()")
    if (nfts.isEmpty()) return emptyList()
    val walletAddress = WalletManager.selectedWalletAddress()
    if (walletAddress.isEmpty()) return emptyList()
    val tokenImports = nfts.map { nft -> nft.formatCadence("import <Token> from <TokenAddress>") }
        .joinToString("\r\n") { it }
    val tokenFunctions = nfts.map { nft ->
        nft.formatCadence(
            """
            pub fun check<Token>Vault(address: Address) : Bool {
                let account = getAccount(address)
                let vaultRef = account
                .getCapability<&{NonFungibleToken.CollectionPublic}>(<TokenCollectionPublicPath>)
                .check()
                return vaultRef
            }
        """.trimIndent()
        )
    }.joinToString("\r\n") { it }

    val tokenCalls = nfts.map { nft ->
        nft.formatCadence(
            """
        check<Token>Vault(address: address)
        """.trimIndent()
        )
    }.joinToString(",") { it }


    val cadence = """
        import NonFungibleToken from 0xNonFungibleToken
          <TokenImports>
          <TokenFunctions>
          pub fun main(address: Address) : [Bool] {
            return [<TokenCall>]
        }
    """.trimIndent().replace("<TokenFunctions>", tokenFunctions)
        .replace("<TokenImports>", tokenImports)
        .replace("<TokenCall>", tokenCalls)

    val result = cadence.executeCadence {
        arg { address(walletAddress) }
    }

    logd(TAG, "cadenceNftListCheckEnabled response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseBoolList()
}

suspend fun cadenceTransferNft(toAddress: String, nft: Nft): String? {
    logd(TAG, "cadenceTransferNft()")
    val transactionId =
        nft.formatCadence(
            if (nft.isNBA()) {
                CADENCE_NBA_NFT_TRANSFER
            } else {
                CADENCE_NFT_TRANSFER
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
            CADENCE_SEND_NFT_FROM_PARENT_TO_CHILD
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
            CADENCE_MOVE_NFT_FROM_CHILD_TO_PARENT
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { string(identifier) }
            arg { uint64(nft.id) }
        }
    logd(TAG, "cadenceMoveNFTFromChildToParent() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceMoveFTFromChildToParent(coin: FlowCoin, childAddress: String, path:
String, amount: Double):
        String? {
    logd(TAG, "cadenceMoveFTFromChildToParent()")
    val transactionId =
        coin.formatCadence(
            CADENCE_MOVE_FT_FROM_CHILD_TO_PARENT
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { string(path) }
            arg { ufix64Safe(BigDecimal(amount)) }
        }
    logd(TAG, "cadenceMoveFTFromChildToParent() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceMoveNFTListFromChildToParent(
    childAddress: String, identifier: String, collection: NftCollection, nftIdList: List<String>
): String? {
    logd(TAG, "cadenceMoveNFTListFromChildToParent()")
    val transactionId =
        collection.formatCadence(
            CADENCE_MOVE_NFT_LIST_FROM_CHILD_TO_PARENT
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
            CADENCE_SEND_NFT_LIST_FROM_PARENT_TO_CHILD
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
            CADENCE_SEND_NFT_LIST_FROM_CHILD_TO_CHILD
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
            CADENCE_SEND_NFT_FROM_CHILD_TO_FLOW
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
            CADENCE_SEND_NFT_FROM_CHILD_TO_CHILD
        ).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { address(toAddress.toAddress()) }
            arg { string(identifier) }
            arg { uint64(nft.id) }
        }
    logd(TAG, "cadenceSendNFTFromChildToChild() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceSendFTFromChildToFlow(
    coin: FlowCoin, childAddress: String, path: String, amount: Double
): String? {
    logd(TAG, "cadenceSendFTFromChildToFlow()")
    val transactionId =
        coin.formatCadence(CADENCE_SEND_FT_FROM_CHILD_TO_FLOW).transactionByMainWallet {
            arg { address(childAddress.toAddress()) }
            arg { string(path) }
            arg { ufix64Safe(BigDecimal(amount)) }
        }
    logd(TAG, "cadenceSendFTFromChildToFlow() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceClaimInboxToken(
    domain: String,
    key: String,
    coin: FlowCoin,
    amount: Float,
    root: String = FlowDomainServer.MEOW.domain,
): String? {
    logd(TAG, "cadenceClaimInboxToken()")
    val txid = coin.formatCadence(CADENCE_CLAIM_INBOX_TOKEN).transactionByMainWallet {
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
    val txid = collection.formatCadence(CADENCE_CLAIM_INBOX_NFT).transactionByMainWallet {
        arg { string(domain) }
        arg { string(root) }
        arg { string(key) }
        arg { uint64(itemId) }
    }
    logd(TAG, "cadenceClaimInboxToken() txid:$txid")
    return txid
}

fun cadenceQueryMinFlowBalance(): Float? {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return null
    logd(TAG, "cadenceQueryMinFlowBalance address:$walletAddress")
    val result = CADENCE_QUERY_MIN_FLOW_BALANCE.executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceQueryMinFlowBalance response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseFloat()
}

suspend fun cadenceCreateCOAAccount(): String? {
    logd(TAG, "cadenceCreateCOAAccount()")
    val transactionId = CADENCE_CREATE_COA_ACCOUNT.transactionByMainWallet {}
    logd(TAG, "cadenceCreateCOAAccount() transactionId:$transactionId")
    return transactionId
}

fun cadenceQueryEVMAddress(): String? {
    logd(TAG, "cadenceQueryEVMAddress()")
    val walletAddress = WalletManager.selectedWalletAddress() ?: return null
    val result = CADENCE_QUERY_COA_EVM_ADDRESS.executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceQueryEVMAddress response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseString()
}

fun cadenceQueryCOATokenBalance(): Float? {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return null
    logd(TAG, "cadenceQueryCOATokenBalance address:$walletAddress")
    val result = CADENCE_QUERY_COA_FLOW_BALANCE.executeCadence {
        arg { address(walletAddress) }
    }
    logd(TAG, "cadenceQueryCOATokenBalance response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseFloat()
}

suspend fun cadenceFundFlowToCOAAccount(amount: Float): String? {
    logd(TAG, "cadenceFundFlowToCOAAccount()")
    val transactionId = CADENCE_FUND_COA_FLOW_BALANCE.transactionByMainWallet {
        arg { ufix64Safe(amount) }
    }
    logd(TAG, "cadenceFundFlowToCOAAccount() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceWithdrawTokenFromCOAAccount(amount: Float, toAddress: String): String? {
    logd(TAG, "cadenceWithdrawTokenFromCOAAccount()")
    val transactionId = CADENCE_WITHDRAW_COA_FLOW_BALANCE.transactionByMainWallet {
        arg { ufix64Safe(amount) }
        arg { address(toAddress) }
    }
    logd(TAG, "cadenceWithdrawTokenFromCOAAccount() transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceTransferFlowToEvmAddress(toAddress: String, amount: Float): String? {
    logd(TAG, "cadenceSendEVMTransaction")
    val transactionId = CADENCE_TRANSFER_FLOW_TO_EVM.transactionByMainWallet {
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
    val transactionId = CADENCE_CALL_EVM_CONTRACT.transactionByMainWallet {
        arg { string(toAddress) }
        arg { ufix64Safe(amount) }
        arg { byteArray(data) }
        arg { uint64(gasLimit) }
    }
    logd(TAG, "cadenceSendEVMTransaction transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTToEvm(
    contractAddress: String,
    contractName: String,
    nftId: String
): String? {
    logd(TAG, "cadenceBridgeNFTToEvm")
    val transactionId = CADENCE_BRIDGE_NFT_TO_EVM.transactionByMainWallet {
        arg { address(contractAddress) }
        arg { string(contractName) }
        arg { uint64(nftId) }
    }
    logd(TAG, "cadenceBridgeNFTToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTListToEvm(
    contractAddress: String,
    contractName: String,
    nftIdList: List<String>
): String? {
    logd(TAG, "cadenceBridgeNFTListToEvm")
    val transactionId = CADENCE_BRIDGE_NFT_LIST_TO_EVM.transactionByMainWallet {
        arg { address(contractAddress) }
        arg { string(contractName) }
        arg { array(nftIdList.map { uint64(it) }) }
    }
    logd(TAG, "cadenceBridgeNFTListToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTListFromEvm(
    contractAddress: String,
    contractName: String,
    nftIdList: List<String>
): String? {
    logd(TAG, "cadenceBridgeNFTListFromEvm")
    val transactionId = CADENCE_BRIDGE_NFT_LIST_FROM_EVM.transactionByMainWallet {
        arg { address(contractAddress) }
        arg { string(contractName) }
        arg { array(nftIdList.map { uint256(it) }) }
    }
    logd(TAG, "cadenceBridgeNFTListFromEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTFromEvm(
    contractAddress: String,
    contractName: String,
    nftId: String
): String? {
    logd(TAG, "cadenceBridgeNFTToEvm")
    val transactionId = CADENCE_BRIDGE_NFT_FROM_EVM.transactionByMainWallet {
        arg { address(contractAddress) }
        arg { string(contractName) }
        arg { uint256(nftId) }
    }
    logd(TAG, "cadenceBridgeNFTToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTFromFlowToEVM(
    nftContractAddress: String, nftContractName: String,
    nftId: String, toEVMAddress: String, data: ByteArray
): String? {
    logd(TAG, "cadenceBridgeNFTFromFlowToEVM")
    val transactionId = CADENCE_BRIDGE_NFT_FROM_FLOW_TO_EVM.transactionByMainWallet {
        arg { address(nftContractAddress) }
        arg { string(nftContractName) }
        arg { uint64(nftId) }
        arg { string(toEVMAddress) }
        arg { byteArray(data) }
        arg { uint64(EVM_GAS_LIMIT) }
    }
    logd(TAG, "cadenceBridgeNFTFromFlowToEVM transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeNFTFromEVMToFlow(
    nftContractAddress: String, nftContractName: String,
    nftId: String, toFlowAddress: String
): String? {
    logd(TAG, "cadenceBridgeNFTFromEVMToFlow")
    val transactionId = CADENCE_BRIDGE_NFT_FROM_EVM_TO_FLOW.transactionByMainWallet {
        arg { address(nftContractAddress) }
        arg { string(nftContractName) }
        arg { uint256(nftId) }
        arg { address(toFlowAddress) }
    }
    logd(TAG, "cadenceBridgeNFTFromEVMToFlow transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeFTToEvm(
    tokenContractAddress: String,
    tokenContractName: String,
    amount: Float
): String? {
    logd(TAG, "cadenceBridgeFTToEvm")
    val transactionId = CADENCE_BRIDGE_FT_TO_EVM.transactionByMainWallet {
        arg { address(tokenContractAddress) }
        arg { string(tokenContractName) }
        arg { ufix64Safe(amount) }
    }
    logd(TAG, "cadenceBridgeFTToEvm transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeFTFromFlowToEVM(
    tokenContractAddress: String, tokenContractName: String,
    amount: Float, toEVMAddress: String, data: ByteArray
): String? {
    logd(TAG, "cadenceBridgeFTFromFlowToEVM")
    val transactionId = CADENCE_BRIDGE_FT_FROM_FLOW_TO_EVM.transactionByMainWallet {
        arg { address(tokenContractAddress) }
        arg { string(tokenContractName) }
        arg { ufix64Safe(amount) }
        arg { string(toEVMAddress) }
        arg { byteArray(data) }
        arg { uint64(EVM_GAS_LIMIT) }
    }
    logd(TAG, "cadenceBridgeFTFromFlowToEVM transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeFTFromEVMToFlow(
    tokenContractAddress: String, tokenContractName: String,
    amount: BigDecimal, toFlowAddress: String
): String? {
    logd(TAG, "cadenceBridgeFTFromEVMToFlow")
    val transactionId = CADENCE_BRIDGE_FT_FROM_EVM_TO_FLOW.transactionByMainWallet {
        arg { address(tokenContractAddress) }
        arg { string(tokenContractName) }
        arg { uint256(amount) }
        arg { address(toFlowAddress) }
    }
    logd(TAG, "cadenceBridgeFTFromEVMToFlow transactionId:$transactionId")
    return transactionId
}

suspend fun cadenceBridgeFTFromEvm(
    tokenContractAddress: String,
    tokenContractName: String,
    amount: BigDecimal
): String? {
    logd(TAG, "cadenceBridgeFTFromEvm")
    val transactionId = CADENCE_BRIDGE_FT_FROM_EVM.transactionByMainWallet {
        arg { address(tokenContractAddress) }
        arg { string(tokenContractName) }
        arg { uint256(amount) }
    }
    logd(TAG, "cadenceBridgeFTFromEvm transactionId:$transactionId")
    return transactionId
}

fun String.executeCadence(block: ScriptBuilder.() -> Unit): FlowScriptResponse? {
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
        FlowApi.get().simpleFlowScript {
            script { this@executeCadence.addPlatformInfo().trimIndent() }
            block()
        }
    } catch (e: Throwable) {
        loge(e)
//        reportCadenceErrorToDebugView()
        return null
    }
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

private fun String.addPlatformInfo(): String {
    return this.replace("<platform_info>", "Android - ${BuildConfig.VERSION_NAME} - ${BuildConfig.VERSION_CODE}")
}

suspend fun String.executeTransaction(arguments: CadenceArgumentsBuilder.() -> Unit): String? {
    val args = CadenceArgumentsBuilder().apply { arguments(this) }
    return try {
        sendTransaction {
            args.build().forEach { arg(it) }
            script(this@executeTransaction)
        }
    } catch (e: Exception) {
        loge(e)
        null
    }
}
