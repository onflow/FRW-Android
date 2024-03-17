package com.flowfoundation.wallet.manager.drive

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize


@Parcelize
class DriveItem(
    @SerializedName("username")
    var username: String,
    @SerializedName("uid")
    var uid: String? = null,
    @SerializedName("version")
    var version: String,
    @SerializedName("data")
    var data: String,
) : Parcelable