package com.flowfoundation.wallet.manager.account.model

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.manager.evm.EVMAddressData
import com.flowfoundation.wallet.manager.emoji.model.WalletEmojiInfo
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.model.UserInfoData
import kotlinx.serialization.Serializable
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.HashingAlgorithm
import java.math.BigDecimal

@Serializable
data class Key(
    @SerializedName("index")
    val index: String,
    
    @SerializedName("publicKey")
    val publicKey: String,
    
    @SerializedName("weight")
    val weight: String,
    
    @SerializedName("signatureAlgorithm")
    val signatureAlgorithm: SigningAlgorithm,
    
    @SerializedName("hashingAlgorithm")
    val hashingAlgorithm: HashingAlgorithm,
    
    @SerializedName("sequenceNumber")
    val sequenceNumber: String,
    
    @SerializedName("revoked")
    val revoked: Boolean
)

@Serializable
data class Account(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("address")
    val address: String,
    
    @SerializedName("balance")
    val balance: BigDecimal,
    
    @SerializedName("keys")
    val keys: List<Key>,
    
    @SerializedName("storageUsed")
    val storageUsed: Long,
    
    @SerializedName("storageCapacity")
    val storageCapacity: Long,
    
    @SerializedName("storageFlow")
    val storageFlow: BigDecimal,
    
    @SerializedName("network")
    val network: String,
    
    @SerializedName("username")
    var userInfo: UserInfoData? = null,
    
    @SerializedName("isActive")
    var isActive: Boolean = false,
    
    @SerializedName("wallet")
    var wallet: WalletListData? = null,
    
    @SerializedName("prefix")
    var prefix: String? = null,
    
    @SerializedName("evmAddressData")
    var evmAddressData: EVMAddressData? = null,
    
    @SerializedName("walletEmojiList")
    var walletEmojiList: List<WalletEmojiInfo>? = null,
    
    @SerializedName("keyStoreInfo")
    var keyStoreInfo: String? = null
) 