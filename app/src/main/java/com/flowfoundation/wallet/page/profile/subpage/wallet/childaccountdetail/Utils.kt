package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail

import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.executeCadence
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import org.onflow.flow.infrastructure.Cadence

suspend fun queryChildAccountTokens(childAddress: String): List<TokenData> {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return emptyList()
    val response = CadenceScript.CADENCE_QUERY_CHILD_ACCOUNT_TOKENS.executeCadence {
        arg { Cadence.address(walletAddress) }
        arg { Cadence.address(childAddress) }
    }
    response ?: return emptyList()
    return parseTokenList(response.encode())
}

suspend fun queryChildAccountNFTCollectionID(childAddress: String): List<String> {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return emptyList()
    val response = CadenceScript.CADENCE_QUERY_CHILD_ACCOUNT_NFT_COLLECTIONS.executeCadence {
        arg { Cadence.address(walletAddress) }
        arg { Cadence.address(childAddress) }
    }
    return response?.decode<List<String>>() ?: emptyList()
}

data class CoinData(
    @SerializedName("name")
    val name: String,
    @SerializedName("icon")
    val icon: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("balance")
    val balance: Float
)

@Serializable
data class TokenData(
    @SerializedName("id")
    val id: String,
    @SerializedName("balance")
    val balance: Float
)

data class CollectionData(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("logo")
    val logo: String,
    @SerializedName("accountAddress")
    val accountAddress: String,
    @SerializedName("contractName")
    val contractName: String,
    @SerializedName("idList")
    val idList: List<String>
)

fun parseTokenList(json: String): List<TokenData> {
    val list = mutableListOf<TokenData>()

    val root = JSONObject(json)
    val valueArray = root.optJSONArray("value") ?: JSONArray()
    for (i in 0 until valueArray.length()) {
        val collection = valueArray.getJSONObject(i)
        val fields = collection.optJSONObject("value")?.optJSONArray("fields") ?: JSONArray()

        var id = ""
        var balance = 0f

        for (j in 0 until fields.length()) {
            val field = fields.getJSONObject(j)

            when (field.optString("name")) {
                "id" -> id = field.optJSONObject("value")?.optString("value") ?: ""
                "balance" -> balance = field.optJSONObject("value")?.optString("value")?.toFloatOrNull() ?: 0f
            }
        }

        val tokenData = TokenData(id, balance)
        list.add(tokenData)
    }

    return list
}
