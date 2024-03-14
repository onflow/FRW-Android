package com.flowfoundation.wallet.manager.account

import com.google.gson.Gson
import com.nftco.flow.sdk.FlowScriptResponse

/**
 * Created by Mengxy on 8/29/23.
 */
data class Item(
    val key: KeyValue,
    val value: KeyValue
)

data class KeyValue(
    val value: String,
    val type: String
)

data class PublicKeyJsonData(
    val value: List<Item>,
    val type: String
)

/*
* {"value":[{"key":{"value":"0x4b684356cd452904","type":"String"},"value":{"value":"44e02ae3135b58979916c79e8dd97f70ed432e00eb7b2553200256e90c1dfc424bd92239d3789e02473eefbc66ede69a036cb836abe2e0304ef5290320effbea","type":"String"}}],"type":"Dictionary"}
*/

fun FlowScriptResponse.parsePublicKeyMap(): Map<String, String> {
    val jsonData = Gson().fromJson(stringValue, PublicKeyJsonData::class.java)
    val keyMap = mutableMapOf<String, String>()
    for (item in jsonData.value) {
        keyMap[item.key.value] = item.value.value
    }
    return keyMap
}
