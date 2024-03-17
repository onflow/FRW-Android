package com.flowfoundation.wallet.page.profile.subpage.wallet.key.model

import com.nftco.flow.sdk.FlowPublicKey
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm


data class AccountKey(
    val id: Int = -1,
    val publicKey: FlowPublicKey,
    val signAlgo: SignatureAlgorithm,
    val hashAlgo: HashAlgorithm,
    val weight: Int,
    val sequenceNumber: Int = -1,
    val revoked: Boolean = false,
    val isRevoking: Boolean = false,
    val isCurrentDevice: Boolean = false,
    var deviceName: String,
    var backupType: Int,
    var deviceType: Int,
)