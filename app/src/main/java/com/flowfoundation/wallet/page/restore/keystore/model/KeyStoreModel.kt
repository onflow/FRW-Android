package com.flowfoundation.wallet.page.restore.keystore.model

import com.flowfoundation.wallet.R
import com.google.gson.annotations.SerializedName


data class KeystoreAddressResponse(
    @SerializedName("publicKey")
    val publicKey: String,
    @SerializedName("accounts")
    val accounts: List<KeystoreAccount>
)

data class KeystoreAccount(
    @SerializedName("address")
    val address: String,
    @SerializedName("keyId")
    val keyId: Int,
    @SerializedName("weight")
    val weight: Int,
    @SerializedName("sigAlgo")
    val signAlgo: Int,
    @SerializedName("hashAlgo")
    val hashAlgo: Int
)

data class KeystoreAddress(
    @SerializedName("address")
    val address: String,
    @SerializedName("publicKey")
    val publicKey: String,
    @SerializedName("privateKey")
    val privateKey: String,
    @SerializedName("keyId")
    val keyId: Int,
    @SerializedName("weight")
    val weight: Int,
    @SerializedName("hashAlgo")
    val hashAlgo: Int,
    @SerializedName("signAlgo")
    val signAlgo: Int
)

enum class KeyStoreOption(val layoutId: Int, val titleResId: Int) {
    INPUT_KEYSTORE_INFO(R.id.fragment_private_key_store_info, R.string.key_store),
    INPUT_PRIVATE_KEY_INFO(R.id.fragment_private_key_info, R.string.private_key),
    INPUT_SEED_PHRASE_INFO(R.id.fragment_seed_phrase_info, R.string.recovery_phrase),
    CREATE_USERNAME(R.id.fragment_private_key_store_username, R.string.create_wallet)
}
