package com.flowfoundation.wallet.manager.backup

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
class BackupItem(
    @SerializedName("address")
    var address: String,
    @SerializedName("userId")
    var userId: String,
    @SerializedName("userName")
    var userName: String,
    @SerializedName("publicKey")
    var publicKey: String,
    @SerializedName("signAlgo")
    var signAlgo: Int,
    @SerializedName("hashAlgo")
    var hashAlgo: Int,
    @SerializedName("keyIndex")
    var keyIndex: Int,
    @SerializedName("updateTime")
    var updateTime: Long,
    @SerializedName("data")
    var data: String,
) : Parcelable