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

data class PublicKeyJsonData(
    @SerializedName("value")
    val value: List<Item>,
    @SerializedName("type")
    val type: String
)

/*
* {"value":[{"key":{"value":"0x4b684356cd452904","type":"String"},"value":{"value":"44e02ae3135b58979916c79e8dd97f70ed432e00eb7b2553200256e90c1dfc424bd92239d3789e02473eefbc66ede69a036cb836abe2e0304ef5290320effbea","type":"String"}}],"type":"Dictionary"}
*/

