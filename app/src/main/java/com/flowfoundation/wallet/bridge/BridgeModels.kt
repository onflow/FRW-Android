//
//  BridgeModels.kt
//  
//  Auto-generated from TypeScript bridge types
//  Do not edit manually
//

package com.flowfoundation.wallet.bridge

import com.google.gson.annotations.SerializedName

class RNBridge {
    enum class AccountType {
        @SerializedName("main") MAIN,
        @SerializedName("child") CHILD,
        @SerializedName("evm") EVM
    }

    enum class TransactionType {
        @SerializedName("tokens") TOKENS,
        @SerializedName("single-nft") SINGLE_NFT,
        @SerializedName("multiple-nfts") MULTIPLE_NFTS,
        @SerializedName("target-address") TARGET_ADDRESS
    }

    data class EmojiInfo(
        @SerializedName("emoji")
        val emoji: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("color")
        val color: String
    )

    data class Contact(
        @SerializedName("id")
        val id: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("address")
        val address: String,
        @SerializedName("avatar")
        val avatar: String?,
        @SerializedName("username")
        val username: String?,
        @SerializedName("contactName")
        val contactName: String?
    )

    data class AddressBookContact(
        @SerializedName("id")
        val id: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("address")
        val address: String,
        @SerializedName("avatar")
        val avatar: String?,
        @SerializedName("username")
        val username: String?,
        @SerializedName("contactName")
        val contactName: String?
    )

    data class WalletAccount(
        @SerializedName("id")
        val id: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("address")
        val address: String,
        @SerializedName("emojiInfo")
        val emojiInfo: EmojiInfo?,
        @SerializedName("parentEmoji")
        val parentEmoji: EmojiInfo?,
        @SerializedName("avatar")
        val avatar: String?,
        @SerializedName("isActive")
        val isActive: Boolean,
        @SerializedName("type")
        val type: AccountType?
    )

    data class RecentContactsResponse(
        @SerializedName("contacts")
        val contacts: List<Contact>
    )

    data class WalletAccountsResponse(
        @SerializedName("accounts")
        val accounts: List<WalletAccount>
    )

    data class AddressBookResponse(
        @SerializedName("contacts")
        val contacts: List<AddressBookContact>
    )

    data class SendToConfig(
        @SerializedName("selectedToken")
        val selectedToken: TokenModel?,
        @SerializedName("fromAccount")
        val fromAccount: WalletAccount?,
        @SerializedName("transactionType")
        val transactionType: TransactionType,
        @SerializedName("selectedNFTs")
        val selectedNFTs: List<NFTModel>?,
        @SerializedName("targetAddress")
        val targetAddress: String?
    )

    data class EnvironmentVariables(
        @SerializedName("NODE_API_URL")
        val NODE_API_URL: String,
        @SerializedName("GO_API_URL")
        val GO_API_URL: String,
        @SerializedName("INSTABUG_TOKEN")
        val INSTABUG_TOKEN: String
    )

    data class NFTModel(
        @SerializedName("id")
        val id: String?,
        @SerializedName("name")
        val name: String?,
        @SerializedName("description")
        val description: String?,
        @SerializedName("thumbnail")
        val thumbnail: String?,
        @SerializedName("externalURL")
        val externalURL: String?,
        @SerializedName("collectionName")
        val collectionName: String?,
        @SerializedName("collectionContractName")
        val collectionContractName: String?,
        @SerializedName("contractAddress")
        val contractAddress: String?,
        @SerializedName("evmAddress")
        val evmAddress: String?,
        @SerializedName("address")
        val address: String?,
        @SerializedName("contractName")
        val contractName: String?,
        @SerializedName("collectionDescription")
        val collectionDescription: String?,
        @SerializedName("collectionSquareImage")
        val collectionSquareImage: String?,
        @SerializedName("collectionBannerImage")
        val collectionBannerImage: String?,
        @SerializedName("collectionExternalURL")
        val collectionExternalURL: String?,
        @SerializedName("flowIdentifier")
        val flowIdentifier: String?,
        @SerializedName("postMedia")
        val postMedia: NFTPostMedia?,
        @SerializedName("contractType")
        val contractType: String?,
        @SerializedName("amount")
        val amount: String?,
        @SerializedName("type")
        val type: WalletType
    )

    data class TokenModel(
        @SerializedName("type")
        val type: WalletType,
        @SerializedName("name")
        val name: String,
        @SerializedName("symbol")
        val symbol: String?,
        @SerializedName("description")
        val description: String?,
        @SerializedName("balance")
        val balance: String?,
        @SerializedName("contractAddress")
        val contractAddress: String?,
        @SerializedName("contractName")
        val contractName: String?,
        @SerializedName("storagePath")
        val storagePath: FlowPath?,
        @SerializedName("receiverPath")
        val receiverPath: FlowPath?,
        @SerializedName("balancePath")
        val balancePath: FlowPath?,
        @SerializedName("identifier")
        val identifier: String?,
        @SerializedName("isVerified")
        val isVerified: Boolean?,
        @SerializedName("logoURI")
        val logoURI: String?,
        @SerializedName("priceInUSD")
        val priceInUSD: String?,
        @SerializedName("balanceInUSD")
        val balanceInUSD: String?,
        @SerializedName("priceInFLOW")
        val priceInFLOW: String?,
        @SerializedName("balanceInFLOW")
        val balanceInFLOW: String?,
        @SerializedName("currency")
        val currency: String?,
        @SerializedName("priceInCurrency")
        val priceInCurrency: String?,
        @SerializedName("balanceInCurrency")
        val balanceInCurrency: String?,
        @SerializedName("displayBalance")
        val displayBalance: String?,
        @SerializedName("availableBalanceToUse")
        val availableBalanceToUse: String?,
        @SerializedName("change")
        val change: String?,
        @SerializedName("decimal")
        val decimal: Int?,
        @SerializedName("evmAddress")
        val evmAddress: String?,
        @SerializedName("website")
        val website: String?
    )

    enum class WalletType {
        @SerializedName("flow") FLOW,
        @SerializedName("evm") EVM
    }

    data class FlowPath(
        @SerializedName("domain")
        val domain: String?,
        @SerializedName("identifier")
        val identifier: String?
    )

}
