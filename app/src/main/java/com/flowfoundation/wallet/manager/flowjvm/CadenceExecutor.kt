package com.flowfoundation.wallet.manager.flowjvm

import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.FlowScriptResponse
import com.nftco.flow.sdk.ScriptBuilder
import com.nftco.flow.sdk.cadence.marshall
import com.nftco.flow.sdk.simpleFlowScript
import com.flowfoundation.wallet.manager.account.parsePublicKeyMap
import com.flowfoundation.wallet.manager.cadence.CadenceApiManager
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
import com.flowfoundation.wallet.wallet.removeAddressPrefix
import com.flowfoundation.wallet.wallet.toAddress
import java.math.BigDecimal

private const val TAG = "CadenceExecutor"

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
    logd(TAG, "cadenceCheckTokenEnabled() address:${coin.address()}")
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
        arg { address(walletAddress)}
    }
    logd(TAG, "cadenceCheckTokenListEnabled address:$walletAddress :: response:${String(result?.bytes ?: byteArrayOf())}")
    return result?.parseStringBoolMap()
}

fun cadenceQueryTokenListBalance(): Map<String, Float>? {
    val walletAddress = WalletManager.selectedWalletAddress().toAddress()
    logd(TAG, "cadenceQueryTokenListBalance()")
    val result = CADENCE_GET_TOKEN_LIST_BALANCE.executeCadence {
        arg { address(walletAddress)}
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
    logd(TAG, "cadenceQueryTokenBalanceWithAddress response:${String(result?.bytes ?: byteArrayOf())}")
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
    if(walletAddress.isEmpty()) return emptyList()
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
        nft.formatCadence(if (nft.isNBA()) CADENCE_NBA_NFT_TRANSFER else CADENCE_NFT_TRANSFER)
            .transactionByMainWallet {
                arg { address(toAddress.toAddress()) }
                arg { uint64(nft.id) }
            }
    logd(TAG, "cadenceTransferNft() transactionId:$transactionId")
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

suspend fun cadenceCreateCOAAccount(): String? {
    logd(TAG, "cadenceCreateCOAAccount()")
    val transactionId = CADENCE_CREATE_COA_ACCOUNT.transactionByMainWallet {
        arg { ufix64Safe(BigDecimal(0.0)) }
    }
    logd(TAG, "cadenceCreateCOAAccount() transactionId:$transactionId")
    return transactionId
}

fun cadenceQueryEVMAddress(): String? {
    logd(TAG, "cadenceQueryEVMAddress()")
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return null
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
            script { this@executeCadence.trimIndent() }
            block()
        }
    } catch (e: Throwable) {
        loge(e)
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
            script(this@transactionByMainWallet)
        }
    } catch (e: Exception) {
        loge(e)
        null
    }
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
