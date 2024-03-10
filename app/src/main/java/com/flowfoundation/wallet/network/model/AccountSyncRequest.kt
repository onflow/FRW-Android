package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName


data class AccountSyncRequest(
    @SerializedName("account_key")
    val accountKey: AccountKey,

    @SerializedName("device_info")
    val deviceInfo: DeviceInfoRequest? = null,

    @SerializedName("backup_info")
    val backupInfo: BackupInfoRequest? = null
)