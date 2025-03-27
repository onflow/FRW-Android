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
    @SerializedName("userAvatar")
    var userAvatar: String,
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
) : Parcelable {
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupItem) return false
        
        return address == other.address &&
                userId == other.userId &&
                publicKey == other.publicKey &&
                signAlgo == other.signAlgo &&
                hashAlgo == other.hashAlgo &&
                keyIndex == other.keyIndex &&
                data == other.data
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + signAlgo
        result = 31 * result + hashAlgo
        result = 31 * result + keyIndex
        result = 31 * result + data.hashCode()
        return result
    }
}