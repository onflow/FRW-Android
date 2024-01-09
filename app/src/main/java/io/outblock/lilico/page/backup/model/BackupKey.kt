package io.outblock.lilico.page.backup.model

import com.google.gson.annotations.SerializedName
import io.outblock.lilico.network.model.KeyDeviceInfo


data class BackupKey(
    @SerializedName("key_id")
    val keyId: Int,
    @SerializedName("key_device_info")
    val info: KeyDeviceInfo?,
    @SerializedName("is_revoking")
    val isRevoking: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val otherKey = other as BackupKey

        return keyId == otherKey.keyId
    }

    override fun hashCode(): Int {
        return keyId.hashCode()
    }
}