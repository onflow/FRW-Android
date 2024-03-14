package com.flowfoundation.wallet.page.profile.subpage.walletconnect.sessiondetail.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WalletConnectSessionDetailModel(
    val topic:String,
    val icon: String,
    val name: String,
    val url: String,
    val expiry: Long,
    val address: String,
    val network: String,
) : Parcelable
