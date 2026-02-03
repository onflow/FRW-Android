package com.flowfoundation.wallet.network.model

import com.google.gson.annotations.SerializedName


data class MnemonicCheckResponse(
    @SerializedName("data")
    val data: CheckResult?,
    @SerializedName("status")
    val status: Int?
)

data class CheckResult(
    @SerializedName("result")
    val isExist: Boolean?
)
