package com.flowfoundation.wallet.page.swap.presenter

import com.zackratos.ultimatebarx.ultimatebarx.addNavigationBarBottomPadding
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivitySwapBinding
import com.flowfoundation.wallet.page.swap.*
import com.flowfoundation.wallet.page.swap.dialog.confirm.SwapTokenConfirmDialog
import com.flowfoundation.wallet.page.swap.dialog.select.SelectTokenDialog
import com.flowfoundation.wallet.page.swap.model.SwapModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.uiScope

class SwapPresenter(
    private val binding: ActivitySwapBinding,
    private val activity: SwapActivity,
) : BasePresenter<SwapModel> {

    init {
        setupToolbar()
        binding.bindInputListener()
        with(binding) {
            root.addStatusBarTopPadding()
            root.addNavigationBarBottomPadding()
            maxButton.setOnClickListener { setMaxAmount() }
            switchButton.setOnClickListener {
                if (viewModel().fromCoin() == null || viewModel().toCoin() == null) return@setOnClickListener
                viewModel().switchCoin()
                binding.switchCoin()
            }
            swapButton.setOnClickListener { SwapTokenConfirmDialog.show(activity.supportFragmentManager) }
            fromButton.setOnClickListener { showSelectTokenDialog(true) }
            toButton.setOnClickListener { showSelectTokenDialog(false) }
        }
    }

    override fun bind(model: SwapModel) {
        model.fromCoin?.let { binding.updateFromCoin(it) }
        model.toCoin?.let { binding.updateToCoin(it) }
        model.onBalanceUpdate?.let { binding.onBalanceUpdate() }
        model.onCoinRateUpdate?.let { binding.onCoinRateUpdate() }
        model.onEstimateFromUpdate?.let { binding.updateFromAmount(it) }
        model.onEstimateToUpdate?.let { binding.updateToAmount(it) }
        model.onEstimateLoading?.let { binding.updateProgressState(it) }
        model.estimateData?.let { binding.updateEstimate(it) }
    }

    private fun showSelectTokenDialog(isFrom: Boolean) {
        uiScope {
            val viewModel = binding.viewModel()
            val symbol = if (isFrom) viewModel.fromCoin()?.symbol else viewModel.toCoin()?.symbol
            SelectTokenDialog().show(
                selectedCoin = symbol,
                disableCoin = if (isFrom) viewModel.toCoin()?.symbol else viewModel.fromCoin()?.symbol,
                activity.supportFragmentManager,
            )?.let {
                if (isFrom) viewModel.updateFromCoin(it) else viewModel.updateToCoin(it)
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = R.string.swap.res2String()
    }
}