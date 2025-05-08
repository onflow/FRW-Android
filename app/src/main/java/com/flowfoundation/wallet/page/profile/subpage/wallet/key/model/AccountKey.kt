package com.flowfoundation.wallet.page.profile.subpage.wallet.key.model

import com.google.gson.annotations.SerializedName
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm

data class AccountKey(
    @SerializedName("id")
    val id: Int = -1,
    @SerializedName("publicKey")
    val publicKey: AccountPublicKey,
    @SerializedName("signAlgo")
    val signAlgo: SigningAlgorithm,
    @SerializedName("hashAlgo")
    val hashAlgo: HashingAlgorithm,
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