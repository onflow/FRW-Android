package com.flowfoundation.wallet.page.profile.subpage.wallet.key.model

import com.google.gson.annotations.SerializedName
import com.nftco.flow.sdk.FlowPublicKey
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm

data class AccountKey(
    @SerializedName("id")
    val id: Int = -1,
    @SerializedName("publicKey")
    val publicKey: FlowPublicKey,
    @SerializedName("signAlgo")
    val signAlgo: SignatureAlgorithm,
    @SerializedName("hashAlgo")
    val hashAlgo: HashAlgorithm,
    @SerializedName("weight")
    val weight: Int,
    @SerializedName("sequenceNumber")
    val sequenceNumber: Int = -1,
    @SerializedName("revoked")
    val revoked: Boolean = false,
    @SerializedName("isRevoking")
    val isRevoking: Boolean = false,
    @SerializedName("isCurrentDevice")
    val isCurrentDevice: Boolean = false,
    @SerializedName("deviceName")
    var deviceName: String,
    @SerializedName("backupType")
    var backupType: Int,
    @SerializedName("deviceType")
    var deviceType: Int,
)