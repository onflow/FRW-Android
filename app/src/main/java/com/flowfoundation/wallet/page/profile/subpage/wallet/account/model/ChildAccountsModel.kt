package com.flowfoundation.wallet.page.profile.subpage.wallet.account.model

import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.google.gson.annotations.SerializedName

data class ChildAccountsModel(
    @SerializedName("accounts")
    val accounts: List<ChildAccount>? = null,
)
