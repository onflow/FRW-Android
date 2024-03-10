package com.flowfoundation.wallet.network.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class UserInfoResponse(
    @SerializedName("data")
    val data: UserInfoData,

    @SerializedName("message")
    val message: String,

    @SerializedName("status")
    val status: Int,
)

@Parcelize
data class UserInfoData(
    @SerializedName("nickname")
    var nickname: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("avatar")
    var avatar: String,
    @SerializedName("address")
    var address: String? = null,
    @SerializedName("private")
    var isPrivate: Int,
    @SerializedName("created")
    var created: String,
) : Parcelable