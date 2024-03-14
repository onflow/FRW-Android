package com.flowfoundation.wallet.page.staking.amount.dialog

import android.os.Parcelable
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency
import kotlinx.parcelize.Parcelize

@Parcelize
class StakingAmountConfirmModel(
    val amount: Float,
    val coinRate: Float,
    val currency: Currency,
    val rate: Float,
    val rewardCoin: Float,
    val rewardUsd: Float,
    val provider: StakingProvider,
    val isUnstake: Boolean,
) : Parcelable