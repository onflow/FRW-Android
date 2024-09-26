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
import com.flowfoundation.wallet.utils.format
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.ioScope

class StakingAmountViewModel : ViewModel(), OnBalanceUpdate, OnCoinRateUpdate {

    val balanceLiveData = MutableLiveData<Float>()

    val processingLiveData = MutableLiveData<Boolean>()

    private var coinRate = 0.0f

    init {
        BalanceManager.addListener(this)
        CoinRateManager.addListener(this)
    }


    fun coinRate() = coinRate

    fun load(provider: StakingProvider, isUnstake: Boolean) {
        ioScope {
            val coin = FlowCoinListManager.getCoin(FlowCoin.SYMBOL_FLOW) ?: return@ioScope
            if (isUnstake) {
                val node = StakingManager.stakingNode(provider) ?: return@ioScope
                balanceLiveData.postValue((node.tokensStaked + node.tokensCommitted).formatNum().toFloat())
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

    override fun onCoinRateUpdate(coin: FlowCoin, price: Float) {
        coinRate = price
    }
}