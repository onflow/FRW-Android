//
//  BridgeModels.kt
//  
//  Auto-generated from TypeScript bridge types
//  Do not edit manually
//

package com.flowfoundation.wallet.bridge

import com.google.gson.annotations.SerializedName

class RNBridge {
    enum class TransactionType {
        @SerializedName("tokens") TOKENS,
        @SerializedName("single-nft") SINGLE_NFT,
        @SerializedName("multiple-nfts") MULTIPLE_NFTS,
        @SerializedName("target-address") TARGET_ADDRESS
    }

    enum class AccountType {
        @SerializedName("main") MAIN,
        @SerializedName("child") CHILD,
        @SerializedName("evm") EVM
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

    data class SendToConfig(
        @SerializedName("selectedToken")
        val selectedToken: Any?,
        @SerializedName("fromAccount")
        val fromAccount: WalletAccount?,
        @SerializedName("transactionType")
        val transactionType: TransactionType,
        @SerializedName("selectedNFTs")
        val selectedNFTs: List<Any>?,
        @SerializedName("targetAddress")
        val targetAddress: String?
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

}
