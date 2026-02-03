package com.flowfoundation.wallet.manager.evm

import com.google.gson.annotations.SerializedName

data class DAppEVMAccount(
  @SerializedName("address")
  val address: String,
  @SerializedName("type")
  val type: DAppEVMAccountType,
  @SerializedName("balance")
  val balance: String? = null
)

enum class DAppEVMAccountType {
  @SerializedName("COA")
  COA,
  @SerializedName("EOA")
  EOA
}
