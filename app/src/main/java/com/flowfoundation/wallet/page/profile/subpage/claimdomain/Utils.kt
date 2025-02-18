package com.flowfoundation.wallet.page.profile.subpage.claimdomain

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.address.FlowDomainServer
import com.flowfoundation.wallet.page.address.queryAddressBookFromBlockchain
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.setMeowDomainClaimed
import java.lang.ref.WeakReference


private const val TAG = "ClaimDomainUtils"

private val listeners = mutableListOf<WeakReference<MeowDomainClaimedStateChangeListener>>()

fun observeMeowDomainClaimedStateChange(listener: MeowDomainClaimedStateChangeListener) {
    listeners.add(WeakReference(listener))
}

interface MeowDomainClaimedStateChangeListener {
    fun onDomainClaimedStateChange(isClaimed: Boolean)
}