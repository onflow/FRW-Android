package com.flowfoundation.wallet.utils

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.page.address.FlowDomainServer


fun meowDomain(): String? {
    val username = AccountManager.userInfo()?.username ?: return null
    return "$username.${FlowDomainServer.MEOW.domain}"
}

fun meowDomainHost(): String? {
    return AccountManager.userInfo()?.username
}