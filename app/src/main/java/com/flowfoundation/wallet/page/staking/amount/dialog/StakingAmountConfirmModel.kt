package com.flowfoundation.wallet.page.staking.amount.dialog

import android.os.Parcelable
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
class StakingAmountConfirmModel(
    val amount: BigDecimal,
    val coinRate: BigDecimal,
    val currency: Currency,
    val rate: Float,
    val rewardCoin: BigDecimal,
    val rewardUsd: BigDecimal,
    val provider: StakingProvider,
    val isUnstake: Boolean,
) : Parcelable