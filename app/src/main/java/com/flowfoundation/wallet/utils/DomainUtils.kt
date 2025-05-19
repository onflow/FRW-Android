package com.flowfoundation.wallet.utils

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.page.address.FlowDomainServer


fun meowDomain(accountManager: AccountManager = AccountManager): String? {
    val username = accountManager.userInfo()?.username ?: return null
    return "$username.${FlowDomainServer.MEOW.domain}"
}

fun meowDomainHost(accountManager: AccountManager = AccountManager): String? {
    return accountManager.userInfo()?.username
}