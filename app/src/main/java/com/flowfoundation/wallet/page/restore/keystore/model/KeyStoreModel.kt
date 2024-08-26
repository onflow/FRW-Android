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
    val weight: Int
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
    val signAlgo: Int,
)

enum class KeyStoreOption(val layoutId: Int) {
    INPUT_INFO(R.id.fragment_private_key_store_info),
    CREATE_USERNAME(R.id.fragment_private_key_store_username)
}
