package com.flowfoundation.wallet.page.staking.amount

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.utils.extensions.toSafeDecimal
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.ioScope
import java.math.BigDecimal

class StakingAmountViewModel : ViewModel(), FungibleTokenUpdateListener {

    val balanceLiveData = MutableLiveData<BigDecimal>()

    val processingLiveData = MutableLiveData<Boolean>()

    private var coinRate = BigDecimal.ZERO
    private var isUnStake = false

    init {
        FungibleTokenListManager.addTokenUpdateListener(this)
    }


    fun coinRate(): BigDecimal = coinRate

    fun load(provider: StakingProvider, isUnStake: Boolean) {
        this.isUnStake = isUnStake
        ioScope {
            val token = FungibleTokenListManager.getFlowToken() ?: return@ioScope
            if (isUnStake) {
                val node = StakingManager.stakingNode(provider) ?: return@ioScope
                balanceLiveData.postValue((node.tokensStaked + node.tokensCommitted).formatNum().toSafeDecimal())
            }
            FungibleTokenListManager.updateTokenInfo(token.contractId())
        }
    }

    override fun onTokenUpdated(token: FungibleToken) {
        if (token.isFlowToken()) {
            if (isUnStake.not()) {
                balanceLiveData.postValue(token.tokenBalance())
            }
            coinRate = token.tokenPrice()
        }
    }
}