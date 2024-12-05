package com.flowfoundation.wallet.page.staking.amount.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogStakingAmountConfirmBinding
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.flowjvm.*
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.StakingProvider
import com.flowfoundation.wallet.manager.staking.createStakingDelegatorId
import com.flowfoundation.wallet.manager.staking.delegatorId
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.ButtonState
import kotlinx.coroutines.delay

class StakingAmountConfirmDialog : BottomSheetDialogFragment() {

    private val data by lazy { arguments?.getParcelable<StakingAmountConfirmModel>(DATA)!! }
    private lateinit var binding: DialogStakingAmountConfirmBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogStakingAmountConfirmBinding.inflate(inflater)
        return binding.rootView
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            Glide.with(providerIcon).load(data.provider.icon)
                .placeholder(R.drawable.ic_placeholder).into(providerIcon)
            providerName.text = data.provider.name
            amountView.text = data.amount.format(3)
            amoundPriceView.text =
                (data.amount * data.coinRate).formatPrice(3, includeSymbol = true)
            amoundPriceCurrencyView.text = data.currency.name

            rateView.text = (data.rate * 100).format(2) + "%"
            rewardCoinView.text =
                "${(data.rewardCoin).format(digits = 2)} " + R.string.flow_coin_name.res2String()
            rewardPriceView.text =
                "≈ ${(data.rewardUsd).formatPrice(digits = 2, includeSymbol = true)}"
            rewardPriceCurrencyView.text = data.currency.name

            sendButton.setOnProcessing { sendStake() }

            closeButton.setOnClickListener { dismiss() }
            descWrapper.setVisible(!data.isUnstake)
            sendButton.updateDefaultText(if (data.isUnstake) R.string.hold_to_unstake else R.string.hold_to_stake)
            titleView.setText(if (data.isUnstake) R.string.confirm_your_unstake else R.string.confirm_your_stake)
        }
    }

    private fun sendStake() {
        ioScope {
            if (!checkStakingEnabled()) {
                toast(msg = getString(R.string.staking_not_enabled, chainNetWorkString()))
                uiScope { safeRun { dismiss() } }
                return@ioScope
            }
            val isSuccess = if (data.isUnstake) unStake(data.provider) else stake(data.provider)
            safeRun {
                if (isSuccess) {
                    requireActivity().finish()
                } else {
                    toast(msgRes = if (data.isUnstake) R.string.unstake_failed else R.string.stake_failed)
                    uiScope { binding.sendButton.changeState(ButtonState.DEFAULT) }
                }
            }
        }
    }

    private fun checkStakingEnabled(): Boolean {
        return try {
            val response = Cadence.CADENCE_CHECK_STAKING_ENABLED.executeCadence { }
            response?.parseBool(false) ?: false
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun stake(provider: StakingProvider): Boolean {
        try {
            var delegatorId = provider.delegatorId()
            val amount = data.amount
            if (delegatorId == null) {
                createStakingDelegatorId(provider, amount)
                delay(2000)
                StakingManager.refreshDelegatorInfo()
                delegatorId = provider.delegatorId()
            }
            if (delegatorId == null) {
                return false
            }
            val txId = Cadence.CADENCE_STAKE_FLOW.transactionByMainWallet {
                arg { string(data.provider.id) }
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
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private suspend fun unStake(provider: StakingProvider): Boolean {
        try {
            var delegatorId = provider.delegatorId()
            val amount = data.amount
            if (delegatorId == null) {
                createStakingDelegatorId(provider, amount)
                delay(2000)
                StakingManager.refreshDelegatorInfo()
                delegatorId = provider.delegatorId()
            }
            if (delegatorId == null) {
                return false
            }
            val txId = Cadence.CADENCE_UNSTAKE_FLOW.transactionByMainWallet {
                arg { string(data.provider.id) }
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
            return true
        } catch (e: Exception) {
            return false
        }
    }

    companion object {
        private const val DATA = "data"
        fun show(activity: FragmentActivity, data: StakingAmountConfirmModel) {
            StakingAmountConfirmDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(DATA, data)
                }
            }.show(activity.supportFragmentManager, "")
        }
    }
}