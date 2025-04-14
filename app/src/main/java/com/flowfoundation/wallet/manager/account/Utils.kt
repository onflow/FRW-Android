package com.flowfoundation.wallet.manager.account

import com.google.gson.annotations.SerializedName

/**
 * Created by Mengxy on 8/29/23.
 */
data class Item(
    @SerializedName("key")
    val key: KeyValue,
    @SerializedName("value")
    val value: KeyValue
)

data class KeyValue(
    @SerializedName("value")
    val value: String,
    @SerializedName("type")
    val type: String
)