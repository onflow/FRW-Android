package com.flowfoundation.wallet.page.profile.subpage.wallet.account.model

import com.flowfoundation.wallet.manager.childaccount.ChildAccount

data class ChildAccountsModel(
    val accounts: List<ChildAccount>? = null,
)
