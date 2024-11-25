package com.flowfoundation.wallet.page.staking.amount

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.Balance
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.account.OnBalanceUpdate
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.utils.extensions.toSafeDecimal
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.ioScope
import java.math.BigDecimal

class StakingAmountViewModel : ViewModel(), OnBalanceUpdate, OnCoinRateUpdate {

    val balanceLiveData = MutableLiveData<BigDecimal>()

    val processingLiveData = MutableLiveData<Boolean>()

    private var coinRate = BigDecimal.ZERO

    init {
        BalanceManager.addListener(this)
        CoinRateManager.addListener(this)
    }


    fun coinRate(): BigDecimal = coinRate

    fun load(provider: StakingProvider, isUnstake: Boolean) {
        ioScope {
            val coin = FlowCoinListManager.getFlowCoin() ?: return@ioScope
            if (isUnstake) {
                val node = StakingManager.stakingNode(provider) ?: return@ioScope
                balanceLiveData.postValue((node.tokensStaked + node.tokensCommitted).formatNum().toSafeDecimal())
            } else {
                BalanceManager.getBalanceByCoin(coin)
            }
            CoinRateManager.fetchCoinRate(coin)
        }
    }

    override fun onBalanceUpdate(coin: FlowCoin, balance: Balance) {
        if (coin.isFlowCoin()) {
            balanceLiveData.postValue(balance.balance)
        }
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: BigDecimal) {
        coinRate = price
    }
}