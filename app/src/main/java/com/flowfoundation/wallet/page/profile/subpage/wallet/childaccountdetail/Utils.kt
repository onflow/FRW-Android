package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail

import com.flowfoundation.wallet.manager.flowjvm.Cadence
import com.flowfoundation.wallet.manager.flowjvm.executeCadence
import com.flowfoundation.wallet.manager.flowjvm.parseStringList
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.google.gson.annotations.SerializedName
import org.json.JSONArray
import org.json.JSONObject


fun queryChildAccountNftCollections(childAddress: String): List<NFTCollectionData> {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return emptyList()
    val response = Cadence.CADENCE_QUERY_CHILD_ACCOUNT_NFT.executeCadence {
        arg { address(walletAddress) }
        arg { address(childAddress) }
// for test
//        arg { address("0x84221fe0294044d7") }
//        arg { address("0x16c41a2b76dee69b") }
    }
    response ?: return emptyList()
    return parseJson(response.stringValue)
}

fun queryChildAccountTokens(childAddress: String): List<TokenData> {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return emptyList()
    val response = Cadence.CADENCE_QUERY_CHILD_ACCOUNT_TOKENS.executeCadence {
        arg { address(walletAddress) }
        arg { address(childAddress) }
    }
    response ?: return emptyList()
    return parseTokenList(response.stringValue)
}

fun queryChildAccountNFTCollectionID(childAddress: String): List<String> {
    val walletAddress = WalletManager.wallet()?.walletAddress() ?: return emptyList()
    val response = Cadence.CADENCE_QUERY_CHILD_ACCOUNT_NFT_COLLECTIONS.executeCadence {
        arg { address(walletAddress) }
        arg { address(childAddress) }
    }
    return response?.parseStringList() ?: emptyList()
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

data class NFTCollectionData(
    @SerializedName("id")
    val id: String,
    @SerializedName("path")
    val path: String,
    @SerializedName("display")
    val display: DisplayData,
    @SerializedName("idList")
    val idList: List<String>
)

data class DisplayData(
    @SerializedName("name")
    val name: String,
    @SerializedName("squareImage")
    val squareImage: String,
    @SerializedName("mediaType")
    val mediaType: String
)

fun parseJson(json: String): List<NFTCollectionData> {
    val list = mutableListOf<NFTCollectionData>()

    val root = JSONObject(json)
    val valueArray = root.optJSONArray("value") ?: JSONArray()
    for (i in 0 until valueArray.length()) {
        val collection = valueArray.getJSONObject(i)
        val fields = collection.optJSONObject("value")?.optJSONArray("fields") ?: JSONArray()

        var id = ""
        var path = ""
        var display = DisplayData("", "", "")
        val idList = mutableListOf<String>()

        for (j in 0 until fields.length()) {
            val field = fields.getJSONObject(j)

            when (field.optString("name")) {
                "id" -> id = field.optJSONObject("value")?.optString("value") ?: ""
                "path" -> path = field.optJSONObject("value")?.optString("value") ?: ""
                "display" -> {
                    val displayFields =
                        field.optJSONObject("value")?.optJSONObject("value")?.optJSONObject("value")
                            ?.optJSONArray("fields") ?: JSONArray()

                    var name = ""
                    var squareImage = ""
                    var mediaType = ""

                    for (k in 0 until displayFields.length()) {
                        val displayField = displayFields.getJSONObject(k)

                        when (displayField.optString("name")) {
                            "name" -> name =
                                displayField.optJSONObject("value")?.optString("value") ?: ""

                            "squareImage" -> squareImage =
                                displayField.optJSONObject("value")?.optString("value") ?: ""

                            "mediaType" -> mediaType =
                                displayField.optJSONObject("value")?.optString("value") ?: ""
                        }
                    }

                    display = DisplayData(name, squareImage, mediaType)
                }

                "idList" -> {
                    val idListArray =
                        field.optJSONObject("value")?.optJSONArray("value") ?: JSONArray()
                    for (l in 0 until idListArray.length()) {
                        val idItem = idListArray.getJSONObject(l)
                        idList.add(idItem.optString("value", ""))
                    }
                }
            }
        }

        val nftCollectionData = NFTCollectionData(id, path, display, idList)
        list.add(nftCollectionData)
    }

    return list
}

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
