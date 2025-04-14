package com.flowfoundation.wallet.page.profile.subpage.claimdomain

import java.lang.ref.WeakReference

private val listeners = mutableListOf<WeakReference<MeowDomainClaimedStateChangeListener>>()

fun observeMeowDomainClaimedStateChange(listener: MeowDomainClaimedStateChangeListener) {
    listeners.add(WeakReference(listener))
}

interface MeowDomainClaimedStateChangeListener {
    fun onDomainClaimedStateChange(isClaimed: Boolean)
}