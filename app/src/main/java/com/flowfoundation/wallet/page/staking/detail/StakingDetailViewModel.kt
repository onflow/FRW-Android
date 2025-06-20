package com.flowfoundation.wallet.page.staking.detail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.onflow.flow.models.TransactionStatus
import com.flowfoundation.wallet.manager.flowjvm.*
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.manager.staking.createStakingDelegatorId
import com.flowfoundation.wallet.manager.staking.delegatorId
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.staking.detail.model.StakingDetailModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import kotlinx.coroutines.delay
import org.onflow.flow.infrastructure.Cadence.Companion.uint32

class StakingDetailViewModel : ViewModel(), FungibleTokenUpdateListener {

    val dataLiveData = MutableLiveData<StakingDetailModel>()

    private var detailModel = StakingDetailModel()

    init {
        FungibleTokenListManager.addTokenUpdateListener(this)
    }

    fun load(provider: StakingProvider) {
        ioScope {
            detailModel = detailModel.copy(
                currency = selectedCurrency(),
                stakingNode = StakingManager.stakingInfo().nodes.first { it.nodeID == provider.id }
            )

            updateLiveData(detailModel)

            val token = FungibleTokenListManager.getFlowToken() ?: return@ioScope

            logd("xxx", "token:$token")
            FungibleTokenListManager.updateTokenInfo(token.contractId())
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
                arg { uint32(delegatorId.toUInt()) }
                arg { ufix64Safe(amount) }
            }

            val transactionState = TransactionState(
                transactionId = txId!!,
                time = System.currentTimeMillis(),
                state = TransactionStatus.PENDING.ordinal,
                type = TransactionState.TYPE_STAKE_FLOW,
                data = ""
            )
            TransactionStateManager.newTransaction(transactionState)
            pushBubbleStack(transactionState)
        }
    }

    private fun updateLiveData(data: StakingDetailModel) {
        uiScope {
            logd("StakingDetail", "updateLiveData:$data")
            dataLiveData.value = data
        }
    }

    override fun onTokenUpdated(token: FungibleToken) {
        logd("StakingDetail", "price:${token.tokenPrice()}, balance:${token.tokenBalance()}")
        if (token.isFlowToken()) {
            detailModel = detailModel.copy(
                coinRate = token.tokenPrice().toFloat(),
                balance = token.tokenBalance().toFloat()
            )
            updateLiveData(detailModel)
        }
    }


}