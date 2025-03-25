package com.flowfoundation.wallet.page.staking.amount.presenter

import android.annotation.SuppressLint
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.addNavigationBarBottomPadding
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityStakingAmountBinding
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.staking.*
import com.flowfoundation.wallet.page.profile.subpage.currency.model.findCurrencyFromFlag
import com.flowfoundation.wallet.page.staking.amount.StakingAmountActivity
import com.flowfoundation.wallet.page.staking.amount.StakingAmountViewModel
import com.flowfoundation.wallet.page.staking.amount.dialog.StakingAmountConfirmDialog
import com.flowfoundation.wallet.page.staking.amount.dialog.StakingAmountConfirmModel
import com.flowfoundation.wallet.page.staking.amount.model.StakingAmountModel
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.*
import java.math.BigDecimal

@SuppressLint("SetTextI18n")
class StakingAmountPresenter(
    private val binding: ActivityStakingAmountBinding,
    private val provider: StakingProvider,
    private val activity: StakingAmountActivity,
    private val isUnstake: Boolean,
) : BasePresenter<StakingAmountModel> {
    private val viewModel by lazy { ViewModelProvider(activity)[StakingAmountViewModel::class.java] }

    private val currency by lazy { findCurrencyFromFlag(CurrencyManager.currencyFlag()) }

    init {
        with(binding) {
            root.addStatusBarTopPadding()
            root.addNavigationBarBottomPadding()
            rateView.text = "${((if (provider.isLilico()) StakingManager.apy() else STAKING_DEFAULT_NORMAL_APY) * 100).format(2)}%"
            currencyName.text = currency.name
            rewardPriceCurrencyView.text = currency.name
            onAmountChange()
            amountPercent30.setOnClickListener { updateAmountByPercent(0.3f) }
            amountPercent50.setOnClickListener { updateAmountByPercent(0.5f) }
            amountPercentMax.setOnClickListener { updateAmountByPercent(1.0f) }
            button.setOnClickListener {
                StakingAmountConfirmDialog.show(
                    activity, StakingAmountConfirmModel(
                        amount = amount(),
                        coinRate = viewModel.coinRate(),
                        currency = currency,
                        rate = provider.rate(),
                        rewardCoin = amount() * StakingManager.apy().toBigDecimal(),
                        rewardUsd = (amount() * StakingManager.apy().toBigDecimal() * viewModel.coinRate()),
                        provider = provider,
                        isUnstake = isUnstake,
                    )
                )
            }
            descWrapper.setVisible(!isUnstake)
        }
        with(binding.inputView) {
            doOnTextChanged { _, _, _, _ ->
                onAmountChange()
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard()
                }
                return@setOnEditorActionListener false
            }
        }
        setupToolbar()
    }

    override fun bind(model: StakingAmountModel) {
        model.balance?.let { onUpdateBalance(it) }
        model.processingState?.let { onProcessingUpdate(it) }
    }

    private fun onProcessingUpdate(it: Boolean) {
        binding.button.setProgressVisible(false)
        if (it) {
            activity.finish()
        } else {
            toast(R.string.claim_failed)
            onAmountChange()
        }
    }

    private fun onUpdateBalance(it: BigDecimal) {
        binding.balanceView.text = if (isUnstake) {
            activity.getString(R.string.staked_flow_num, it.format())
        } else activity.getString(R.string.flow_available_num, it.format())
    }

    private fun onAmountChange() {
        with(binding) {
            priceView.text = (amount() * viewModel.coinRate()).formatPrice(includeSymbol = true)
            rewardCoinView.text = "${
                (amount() * StakingManager.apy().toBigDecimal()).format(digits = 2)
            } " + R.string.flow_coin_name.res2String()
            rewardPriceView.text = "≈ ${(amount() * StakingManager.apy().toBigDecimal() * viewModel.coinRate())
                .formatPrice(digits = 2, includeSymbol = true)} "
            availableCheck()
        }
    }

    private fun availableCheck() {
        val amount = amount()
        if (amount == BigDecimal.ZERO) {
            binding.button.setText(R.string.next)
            binding.button.isEnabled = false
        } else if (amount > balance()) {
            binding.button.setText(R.string.insufficient_balance)
            binding.button.isEnabled = false
        } else if (amount < BigDecimal(50)) {
            binding.button.setText(R.string.minimum_required)
            binding.button.isEnabled = false
        } else {
            binding.button.setText(R.string.next)
            binding.button.isEnabled = true
        }
    }

    private fun updateAmountByPercent(percent: Float) {
        val text = ((viewModel.balanceLiveData.value ?: BigDecimal.ZERO) * percent.toBigDecimal()).format()
        binding.inputView.setText(text)
        binding.inputView.setSelection(text.length)
    }

    private fun balance() = viewModel.balanceLiveData.value ?: BigDecimal.ZERO

    private fun amount(): BigDecimal {
        val userInputString = binding.inputView.text.toString().trim()
        if (userInputString.isBlank()) {
            return BigDecimal.ZERO
        }
        return userInputString.toSafeDecimal()
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = if (isUnstake) R.string.unstake_amount.res2String() else R.string.stake_amount.res2String()
    }
}