//
//  BridgeModels.kt
//  
//  Auto-generated from TypeScript bridge types
//  Do not edit manually
//

package com.flowfoundation.wallet.reactnative.bridge

import com.google.gson.annotations.SerializedName

class RNBridge {
    enum class AccountType {
        @SerializedName("main") MAIN,
        @SerializedName("child") CHILD,
        @SerializedName("evm") EVM,
        @SerializedName("eoa") EOA
    }

    enum class ScreenType {
        @SerializedName("send-asset") SEND_ASSET,
        @SerializedName("token-detail") TOKEN_DETAIL,
        @SerializedName("onboarding") ONBOARDING,
        @SerializedName("receive") RECEIVE
    }

    enum class AccountTypeType {
        @SerializedName("eoa") EOA,
        @SerializedName("coa") COA,
        @SerializedName("null") NULL
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
        @SerializedName("parentAddress")
        val parentAddress: String?,
        @SerializedName("avatar")
        val avatar: String?,
        @SerializedName("isActive")
        val isActive: Boolean,
        @SerializedName("type")
        val type: AccountType?,
        @SerializedName("balance")
        val balance: String?,
        @SerializedName("nfts")
        val nfts: String?
    )

    data class RecentContactsResponse(
        @SerializedName("contacts")
        val contacts: List<Contact>
    )

    data class WalletAccountsResponse(
        @SerializedName("accounts")
        val accounts: List<WalletAccount>
    )

    data class WalletProfile(
        @SerializedName("name")
        val name: String,
        @SerializedName("avatar")
        val avatar: String,
        @SerializedName("uid")
        val uid: String,
        @SerializedName("accounts")
        val accounts: List<WalletAccount>
    )

    data class WalletProfilesResponse(
        @SerializedName("profiles")
        val profiles: List<WalletProfile>
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
        @SerializedName("selectedNFTs")
        val selectedNFTs: List<NFTModel>?,
        @SerializedName("targetAddress")
        val targetAddress: String?
    )

    data class InitialProps(
        @SerializedName("screen")
        val screen: ScreenType,
        @SerializedName("sendToConfig")
        val sendToConfig: String?
    )

    data class EnvironmentVariables(
        @SerializedName("NODE_API_URL")
        val NODE_API_URL: String,
        @SerializedName("GO_API_URL")
        val GO_API_URL: String,
        @SerializedName("INSTABUG_TOKEN")
        val INSTABUG_TOKEN: String
    )

    data class Currency(
        @SerializedName("name")
        val name: String,
        @SerializedName("symbol")
        val symbol: String,
        @SerializedName("rate")
        val rate: String
    )

    data class SaveMnemonicResponse(
        @SerializedName("success")
        val success: Boolean,
        @SerializedName("error")
        val error: String
    )

    data class CreateAccountResponse(
        @SerializedName("success")
        val success: Boolean,
        @SerializedName("address")
        val address: String,
        @SerializedName("username")
        val username: String,
        @SerializedName("accountType")
        val accountType: AccountTypeType,
        @SerializedName("txId")
        val txId: String,
        @SerializedName("error")
        val error: String
    )

    data class CreateEOAAccountResponse(
        @SerializedName("success")
        val success: Boolean,
        @SerializedName("address")
        val address: String,
        @SerializedName("username")
        val username: String,
        @SerializedName("mnemonic")
        val mnemonic: String,
        @SerializedName("phrase")
        val phrase: String,
        @SerializedName("accountType")
        val accountType: AccountTypeType,
        @SerializedName("error")
        val error: String
    )

    data class AccountKey(
        @SerializedName("publicKey")
        val publicKey: String,
        @SerializedName("hashAlgoStr")
        val hashAlgoStr: String,
        @SerializedName("signAlgoStr")
        val signAlgoStr: String,
        @SerializedName("weight")
        val weight: Int,
        @SerializedName("hashAlgo")
        val hashAlgo: Int,
        @SerializedName("signAlgo")
        val signAlgo: Int
    )

    data class SeedPhraseGenerationResponse(
        @SerializedName("mnemonic")
        val mnemonic: String,
        @SerializedName("accountKey")
        val accountKey: AccountKey,
        @SerializedName("drivepath")
        val drivepath: String
    )

    data class SPResponse(
        @SerializedName("mnemonic")
        val mnemonic: String,
        @SerializedName("accountKey")
        val accountKey: AccountKey,
        @SerializedName("drivepath")
        val drivepath: String
    )

    data class DeviceInfo(
        @SerializedName("device_id")
        val device_id: String?,
        @SerializedName("name")
        val name: String?,
        @SerializedName("type")
        val type: String?,
        @SerializedName("user_agent")
        val user_agent: String?,
        @SerializedName("ip")
        val ip: String?,
        @SerializedName("city")
        val city: String?,
        @SerializedName("country")
        val country: String?,
        @SerializedName("countryCode")
        val countryCode: String?,
        @SerializedName("continent")
        val continent: String?,
        @SerializedName("continentCode")
        val continentCode: String?,
        @SerializedName("regionName")
        val regionName: String?,
        @SerializedName("district")
        val district: String?,
        @SerializedName("zip")
        val zip: String?,
        @SerializedName("lat")
        val lat: Int?,
        @SerializedName("lon")
        val lon: Int?,
        @SerializedName("isp")
        val isp: String?,
        @SerializedName("org")
        val org: String?,
        @SerializedName("currency")
        val currency: String?
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

    data class NFTPostMedia(
        @SerializedName("image")
        val image: String?,
        @SerializedName("isSvg")
        val isSvg: Boolean?,
        @SerializedName("description")
        val description: String?,
        @SerializedName("title")
        val title: String?
    )

    data class FlowPath(
        @SerializedName("domain")
        val domain: String?,
        @SerializedName("identifier")
        val identifier: String?
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

    /**
     * Initial route for launching React Native screens
     * Maps to specific screen names for navigation
     */
    enum class InitialRoute(val routeName: String) {
        @SerializedName("GetStarted") GET_STARTED("GetStarted"),
        @SerializedName("ProfileTypeSelection") PROFILE_TYPE_SELECTION("ProfileTypeSelection"),
        @SerializedName("SelectTokens") SELECT_TOKENS("SelectTokens"),
        @SerializedName("SendTo") SEND_TO("SendTo"),
        @SerializedName("SendTokens") SEND_TOKENS("SendTokens"),
        @SerializedName("Home") HOME("Home")
    }

    /**
     * Native screen names for Android/iOS
     * Used with launchNativeScreen bridge method
     */
    enum class NativeScreenName {
        @SerializedName("multiBackup") MULTI_BACKUP,
        @SerializedName("deviceBackup") DEVICE_BACKUP,
        @SerializedName("seedPhraseBackup") SEED_PHRASE_BACKUP,
        @SerializedName("backupOptions") BACKUP_OPTIONS,
        @SerializedName("walletRestore") WALLET_RESTORE,
        @SerializedName("recoveryPhraseRestore") RECOVERY_PHRASE_RESTORE,
        @SerializedName("keyStoreRestore") KEY_STORE_RESTORE,
        @SerializedName("privateKeyRestore") PRIVATE_KEY_RESTORE,
        @SerializedName("googleDriveRestore") GOOGLE_DRIVE_RESTORE,
        @SerializedName("multiRestore") MULTI_RESTORE
    }

}
