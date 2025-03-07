package com.flowfoundation.wallet.page.staking.detail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.manager.account.Balance
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.account.OnBalanceUpdate
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.manager.flowjvm.*
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.manager.staking.createStakingDelegatorId
import com.flowfoundation.wallet.manager.staking.delegatorId
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.staking.detail.model.StakingDetailModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import kotlinx.coroutines.delay
import java.math.BigDecimal

class StakingDetailViewModel : ViewModel(), OnBalanceUpdate, OnCoinRateUpdate {

    val dataLiveData = MutableLiveData<StakingDetailModel>()

    private var detailModel = StakingDetailModel()

    init {
        BalanceManager.addListener(this)
        CoinRateManager.addListener(this)
    }

    fun load(provider: StakingProvider) {
        ioScope {
            detailModel = detailModel.copy(
                currency = selectedCurrency(),
                stakingNode = StakingManager.stakingInfo().nodes.first { it.nodeID == provider.id }
            )

            updateLiveData(detailModel)

            val coin = FlowCoinListManager.getFlowCoin() ?: return@ioScope

            logd("xxx", "coin:$coin")
            BalanceManager.getBalanceByCoin(coin)
            CoinRateManager.fetchCoinRate(coin)
        }
    }

    fun claimRewards(provider: StakingProvider) {
        CadenceScript.CADENCE_CLAIM_REWARDS.rewardsAction(provider)
    }

    fun reStakeRewards(provider: StakingProvider) {
        CadenceScript.CADENCE_RESTAKE_REWARDS.rewardsAction(provider)
    }

    fun claimUnStaked(provider: StakingProvider) {
        CadenceScript.CADENCE_STAKING_UNSATKED_CLAIM.rewardsAction(provider, true)
    }

    fun reStakeUnStaked(provider: StakingProvider) {
        CadenceScript.CADENCE_STAKING_UNSATKED_RESTAKE.rewardsAction(provider, true)
    }

    private fun CadenceScript.rewardsAction(provider: StakingProvider, isUnStaked: Boolean = false) {
        ioScope {

            val amount = if (isUnStaked) {
                StakingManager.stakingNode(provider)?.tokensUnstaked
            } else {
                StakingManager.stakingNode(provider)?.tokensRewarded
            } ?: 0.0

            if (amount <= 0) {
                return@ioScope
            }

            var delegatorId = provider.delegatorId()
            if (delegatorId == null) {
                createStakingDelegatorId(provider, amount.toBigDecimal())
                delay(2000)
                StakingManager.refreshDelegatorInfo()
                delegatorId = provider.delegatorId()
            }
            if (delegatorId == null) {
                return@ioScope
            }

            val txId = transactionByMainWallet {
                arg { string(provider.id) }
                arg { uint32(delegatorId) }
                arg { ufix64Safe(amount) }
            }

            val transactionState = TransactionState(
                transactionId = txId!!,
                time = System.currentTimeMillis(),
                state = FlowTransactionStatus.PENDING.num,
                type = TransactionState.TYPE_STAKE_FLOW,
                data = ""
            )
            TransactionStateManager.newTransaction(transactionState)
            pushBubbleStack(transactionState)
        }
    }

    override fun onBalanceUpdate(coin: FlowCoin, balance: Balance) {
        if (coin.isFlowCoin()) {
            logd("xxx", "balance:${balance.balance}")
            detailModel = detailModel.copy(balance = balance.balance.toFloat())
            updateLiveData(detailModel)
        }
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: BigDecimal) {
        logd("xxx", "price:${price}")
        if (coin.isFlowCoin()) {
            detailModel = detailModel.copy(coinRate = price.toFloat())
            updateLiveData(detailModel)
        }
    }

    private fun updateLiveData(data: StakingDetailModel) {
        uiScope {
            logd("xxx", "updateLiveData:$data")
            dataLiveData.value = data
        }
    }
}