package com.flowfoundation.wallet.page.token.detail.presenter

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.zackratos.ultimatebarx.ultimatebarx.addNavigationBarBottomPadding
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityTokenDetailBinding
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.staking.STAKING_DEFAULT_NORMAL_APY
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.isLilico
import com.flowfoundation.wallet.manager.staking.stakingCount
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.page.receive.ReceiveActivity
import com.flowfoundation.wallet.page.send.transaction.TransactionSendActivity
import com.flowfoundation.wallet.page.staking.openStakingPage
import com.flowfoundation.wallet.page.token.detail.TokenDetailViewModel
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailModel
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.formatPrice

class TokenDetailPresenter(
    private val activity: AppCompatActivity,
    private val binding: ActivityTokenDetailBinding,
    private val coin: FlowCoin,
) : BasePresenter<TokenDetailModel> {

    private val viewModel by lazy { ViewModelProvider(activity)[TokenDetailViewModel::class.java] }

    init {
        setupToolbar()
        with(binding) {
            root.addStatusBarTopPadding()
            root.addNavigationBarBottomPadding()
            nameView.text = coin.name
            coinTypeView.text = coin.symbol.uppercase()
            Glide.with(iconView).load(coin.icon).into(iconView)
            nameWrapper.setOnClickListener { openBrowser(activity, coin.website) }
            getMoreWrapper.setOnClickListener { }
            sendButton.setOnClickListener { TransactionSendActivity.launch(activity, coinSymbol = coin.symbol) }
            receiveButton.setOnClickListener { ReceiveActivity.launch(activity) }
            sendButton.isEnabled = !WalletManager.isChildAccountSelected()
        }

        if (!coin.isFlowCoin() && coin.symbol != FlowCoin.SYMBOL_FUSD) {
            binding.getMoreWrapper.setVisible(false)
            binding.chartWrapper.root.setVisible(false)
        }

        if (!StakingManager.isStaked() && coin.isFlowCoin() && !isTestnet()) {
            binding.stakingBanner.root.setVisible(true)
            binding.getMoreWrapper.setVisible(false)
            binding.stakingBanner.root.setOnClickListener { openStakingPage(activity) }
        }

        if (StakingManager.isStaked() && coin.isFlowCoin() && !isTestnet()) {
            binding.getMoreWrapper.setVisible(false)
            setupStakingRewards()
        }

        if (!isMainnet()) {
            binding.getMoreWrapper.setOnClickListener {
                openBrowser(
                    activity,
                    if (isTestnet()) "https://testnet-faucet.onflow.org/fund-account" else "https://sandboxnet-faucet.flow.com/"
                )
            }
        }
        bindAccessible(coin)
    }

    private fun bindAccessible(coin: FlowCoin) {
        if (ChildAccountCollectionManager.isTokenAccessible(coin.contractName, coin.address())) {
            binding.inaccessibleTip.gone()
            return
        }
        val accountName = WalletManager.childAccount(WalletManager.selectedWalletAddress())?.name ?: R.string.default_child_account_name.res2String()
        binding.tvInaccessibleTip.text = activity.getString(R.string.inaccessible_token_tip, coin.name, accountName)
        binding.inaccessibleTip.visible()
    }

    @SuppressLint("SetTextI18n")
    override fun bind(model: TokenDetailModel) {
        model.balanceAmount?.let { binding.balanceAmountView.text = it.formatNum() }
        model.balancePrice?.let { binding.balancePriceView.text = "${it.formatPrice(includeSymbol = true)} ${selectedCurrency().name}" }
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = ""
    }

    private fun setupStakingRewards() {
        with(binding.stakingRewardWrapper) {
            val currency = selectedCurrency()
            val coinRate = CoinRateManager.coinRate(FlowCoin.SYMBOL_FLOW) ?: 0f
            val stakingCount = StakingManager.stakingCount()

            val dayRewards =
                StakingManager.stakingInfo().nodes.sumOf { it.stakingCount() * (if (it.isLilico()) StakingManager.apy() else STAKING_DEFAULT_NORMAL_APY).toDouble() }
                    .toFloat() / 365.0f
            stakingCountView.text = activity.getString(R.string.flow_num, stakingCount.formatNum(3))
            dailyView.text = (dayRewards * coinRate).formatPrice(3, includeSymbol = true)
            dailyCurrencyName.text = currency.name
            dailyFlowCount.text = activity.getString(R.string.flow_num, dayRewards.formatNum(3))

            val monthRewards = dayRewards * 30
            monthlyView.text = (monthRewards * coinRate).formatPrice(3, includeSymbol = true)
            monthlyCurrencyName.text = currency.name
            monthlyFlowCount.text =
                activity.getString(R.string.flow_num, monthRewards.formatNum(3))
            root.setOnClickListener { openStakingPage(activity) }
            root.setVisible(true)
        }
    }
}