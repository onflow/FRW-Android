package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

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
data class UserInfoData(
    @SerialName("nickname")
    @SerializedName("nickname")
    var nickname: String,
    @SerialName("username")
    @SerializedName("username")
    val username: String,
    @SerialName("avatar")
    @SerializedName("avatar")
    var avatar: String,
    @SerialName("address")
    @SerializedName("address")
    var address: String? = null,
    @SerialName("private")
    @SerializedName("private")
    var isPrivate: Int,
    @SerialName("created")
    @SerializedName("created")
    var created: String,
) : Parcelable