package com.flowfoundation.wallet.page.token.detail.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityTokenDetailBinding
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.staking.STAKING_DEFAULT_NORMAL_APY
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.isLilico
import com.flowfoundation.wallet.manager.staking.stakingCount
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.evm.EnableEVMActivity
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.page.receive.ReceiveActivity
import com.flowfoundation.wallet.page.send.transaction.TransactionSendActivity
import com.flowfoundation.wallet.page.staking.openStakingPage
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailModel
import com.flowfoundation.wallet.page.token.detail.widget.MoveTokenDialog
import com.flowfoundation.wallet.page.wallet.dialog.SwapDialog
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.formatPrice
import com.flowfoundation.wallet.utils.uiScope
import com.zackratos.ultimatebarx.ultimatebarx.addNavigationBarBottomPadding
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding

class TokenDetailPresenter(
    private val activity: AppCompatActivity,
    private val binding: ActivityTokenDetailBinding,
    private val coin: FlowCoin,
) : BasePresenter<TokenDetailModel> {

    init {
        setupToolbar()
        with(binding) {
            root.addStatusBarTopPadding()
            root.addNavigationBarBottomPadding()
            nameView.text = coin.name
            coinTypeView.text = coin.symbol.uppercase()
            Glide.with(iconView).load(coin.icon()).into(iconView)
            getMoreWrapper.setOnClickListener { }
            btnSend.setOnClickListener {
                TransactionSendActivity.launch(activity, coinContractId = coin.contractId())
            }
            btnReceive.setOnClickListener { ReceiveActivity.launch(activity) }
            btnSwap.setOnClickListener {
                if (WalletManager.isChildAccountSelected()) {
                    return@setOnClickListener
                }
                openBrowser(
                    activity, "https://${if (isTestnet()) "demo" else "app"}" +
                        ".increment.fi/swap")
            }
            btnTrade.setOnClickListener {
                if (WalletManager.isChildAccountSelected()) {
                    return@setOnClickListener
                }
                SwapDialog.show(activity.supportFragmentManager)
            }
            btnSend.isEnabled = !WalletManager.isChildAccountSelected()
            val bgColor = if (WalletManager.isChildAccountSelected()) {
                R.color.accent_gray_8.res2color()
            } else {
                R.color.accent_green_8.res2color()
            }
            val icColor = if (WalletManager.isChildAccountSelected()) {
                R.color.accent_gray.res2color()
            } else {
                R.color.accent_green.res2color()
            }
            val bgTintColor = ColorStateList.valueOf(bgColor)
            val icTintColor = ColorStateList.valueOf(icColor)
            ivSend.imageTintList = icTintColor
            ivSwap.imageTintList = icTintColor
            ivTrade.imageTintList = icTintColor
            btnSwap.setBackgroundColor(bgColor)
            btnSend.backgroundTintList = bgTintColor
            btnTrade.backgroundTintList = bgTintColor

            val moveVisible = !WalletManager.isChildAccountSelected()
                    && (coin.isFlowCoin() || coin.isCOABridgeCoin() || coin.canBridgeToCOA())
            llEvmMoveToken.setVisible(moveVisible)
            llEvmMoveToken.setOnClickListener {
                if (EVMWalletManager.haveEVMAddress()) {
                    uiScope {
                        MoveTokenDialog().showDialog(activity, coin.contractId())
                    }
                } else {
                    EnableEVMActivity.launch(activity)
                }
            }
            if (coin.website().isEmpty()) {
                ivLink.gone()
            } else {
                ivLink.visible()
                nameWrapper.setOnClickListener { openBrowser(activity, coin.website()) }
            }
        }

        if (!coin.isFlowCoin()) {
            binding.getMoreWrapper.setVisible(false)
            binding.chartWrapper.root.setVisible(false)
        }

        if (!StakingManager.isStaking() && coin.isFlowCoin() && isMainnet()) {
            binding.stakingBanner.root.setVisible(true)
            binding.getMoreWrapper.setVisible(false)
            binding.stakingBanner.root.setOnClickListener { openStakingPage(activity) }
        }

        if (StakingManager.isStaking() && coin.isFlowCoin() && isMainnet()) {
            binding.getMoreWrapper.setVisible(false)
            setupStakingRewards()
        }

        if (!isMainnet()) {
            binding.getMoreWrapper.setOnClickListener {
                openBrowser(
                    activity,
                    "https://testnet-faucet.onflow.org/fund-account"
                )
            }
        }
        bindAccessible(coin)
    }

    private fun bindAccessible(coin: FlowCoin) {
        if (ChildAccountCollectionManager.isTokenAccessible(coin.contractName(), coin.address)) {
            binding.inaccessibleTip.gone()
            return
        }
        val accountName = WalletManager.childAccount(WalletManager.selectedWalletAddress())?.name ?: R.string.default_child_account_name.res2String()
        binding.tvInaccessibleTip.text = activity.getString(R.string.inaccessible_token_tip, coin.name, accountName)
        binding.inaccessibleTip.visible()
    }

    @SuppressLint("SetTextI18n")
    override fun bind(model: TokenDetailModel) {
        model.balanceAmount?.let { binding.balanceAmountView.text = it.formatLargeBalanceNumber() }
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
            val coinRate = CoinRateManager.coinRate(FlowCoinListManager.getFlowCoinContractId()) ?: 0f
            val stakingCount = StakingManager.stakingCount()

            val dayRewards =
                StakingManager.stakingInfo().nodes.sumOf { it.stakingCount() * (if (it.isLilico()) StakingManager.apy() else STAKING_DEFAULT_NORMAL_APY).toDouble() }
                    .toFloat() / 365.0f
            stakingCountView.text = activity.getString(R.string.flow_num, stakingCount.formatNum(3))
            dailyView.text = (dayRewards * coinRate.toDouble()).formatPrice(3, includeSymbol = true)
            dailyCurrencyName.text = currency.name
            dailyFlowCount.text = activity.getString(R.string.flow_num, dayRewards.formatNum(3))

            val monthRewards = dayRewards * 30
            monthlyView.text = (monthRewards * coinRate.toDouble()).formatPrice(3, includeSymbol = true)
            monthlyCurrencyName.text = currency.name
            monthlyFlowCount.text =
                activity.getString(R.string.flow_num, monthRewards.formatNum(3))
            root.setOnClickListener { openStakingPage(activity) }
            root.setVisible(true)
        }
    }
}