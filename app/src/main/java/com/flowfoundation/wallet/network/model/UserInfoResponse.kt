package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.ExperimentalSerializationApi

data class UserInfoResponse(
    @SerializedName("data")
    val data: UserInfoData,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

@Serializable
@Parcelize
@OptIn(ExperimentalSerializationApi::class)
data class UserInfoData(
    @SerializedName("nickname")
    var nickname: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("avatar")
    var avatar: String,
    @SerializedName("address")
    var address: String? = null,
    @SerialName("private")
    @SerializedName("private")
    @JsonNames("isPrivate")
    var isPrivate: Int = 1,
    @SerializedName("created")
    var created: String,
) : Parcelable
