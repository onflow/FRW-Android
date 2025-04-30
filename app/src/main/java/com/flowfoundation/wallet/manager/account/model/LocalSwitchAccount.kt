package com.flowfoundation.wallet.manager.account.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class LocalSwitchAccount(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("address")
    val address: String,
    
    @SerializedName("username")
    val username: String,
    
    @SerializedName("balance")
    val balance: BigDecimal,
    
    @SerializedName("storageUsed")
    val storageUsed: Long,
    
    @SerializedName("storageCapacity")
    val storageCapacity: Long,
    
    @SerializedName("storageFlow")
    val storageFlow: BigDecimal,
    
    @SerializedName("network")
    val network: String,
    
    @SerializedName("prefix")
    val prefix: String? = null
)
