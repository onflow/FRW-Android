package com.flowfoundation.wallet.page.token.detail.presenter

import android.annotation.SuppressLint
import android.graphics.Paint
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityTokenDetailBinding
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.staking.STAKING_DEFAULT_NORMAL_APY
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.staking.isLilico
import com.flowfoundation.wallet.manager.staking.stakingCount
import com.flowfoundation.wallet.manager.token.model.FungibleToken
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
import com.flowfoundation.wallet.page.wallet.dialog.SwapProviderDialog
import com.flowfoundation.wallet.utils.debug.ResourceUtility.getString
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.formatPrice
import com.flowfoundation.wallet.utils.toHumanReadableSIPrefixes
import com.flowfoundation.wallet.utils.uiScope
import com.zackratos.ultimatebarx.ultimatebarx.addNavigationBarBottomPadding
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding

class TokenDetailPresenter(
    private val activity: AppCompatActivity,
    private val binding: ActivityTokenDetailBinding,
    private val token: FungibleToken,
) : BasePresenter<TokenDetailModel> {

    init {
        setupToolbar()
        with(binding) {
            root.addStatusBarTopPadding()
            root.addNavigationBarBottomPadding()
            nameView.text = token.name
            coinTypeView.text = token.symbol.uppercase()
            Glide.with(iconView).load(token.tokenIcon()).into(iconView)
            getMoreWrapper.setOnClickListener { }
            btnSend.setOnClickListener {
                TransactionSendActivity.launch(activity, coinContractId = token.contractId())
            }
            btnReceive.setOnClickListener { ReceiveActivity.launch(activity) }
            btnSwap.setOnClickListener {
                if (WalletManager.isChildAccountSelected()) {
                    return@setOnClickListener
                }
                SwapProviderDialog.show(
                    activity.supportFragmentManager,
                    isEVMToken = WalletManager.isEVMAccountSelected(),
                    isFlowToken = token.isFlowToken())
            }
            btnTrade.setVisible(token.isFlowToken())
            btnTrade.setOnClickListener {
                if (WalletManager.isChildAccountSelected()) {
                    return@setOnClickListener
                }
                SwapDialog.show(activity.supportFragmentManager)
            }
            btnSend.isEnabled = !WalletManager.isChildAccountSelected()
            val moveVisible = !WalletManager.isChildAccountSelected()
                    && (token.isFlowToken() || token.canBridgeToEVM() || token.canBridgeToCadence())
            llEvmMoveToken.setVisible(moveVisible)
            llEvmMoveToken.setOnClickListener {
                if (EVMWalletManager.haveEVMAddress()) {
                    uiScope {
                        MoveTokenDialog().showDialog(activity, token.contractId())
                    }
                } else {
                    EnableEVMActivity.launch(activity)
                }
            }
            if (token.tokenWebsite().isEmpty()) {
                ivLink.gone()
            } else {
                ivLink.visible()
                nameWrapper.setOnClickListener { openBrowser(activity, token.tokenWebsite()) }
            }
        }

        if (!token.isFlowToken()) {
            binding.getMoreWrapper.setVisible(false)
            binding.chartWrapper.root.setVisible(false)
        }

        if (!StakingManager.isStaking() && token.isFlowToken() && isMainnet()) {
            binding.stakingBanner.root.setVisible(true)
            binding.getMoreWrapper.setVisible(false)
            binding.stakingBanner.root.setOnClickListener { openStakingPage(activity) }
        }

        if (StakingManager.isStaking() && token.isFlowToken() && isMainnet()) {
            binding.getMoreWrapper.setVisible(false)
            setupStakingRewards(token)
        }

        if (!isMainnet()) {
            binding.getMoreWrapper.setOnClickListener {
                openBrowser(
                    activity,
                    "https://testnet-faucet.onflow.org/fund-account"
                )
            }
        }
        bindAccessible(token)
        bindStorageInfo(token)
        bindVerifiedInfo(token)
    }

    private fun bindVerifiedInfo(token: FungibleToken) {
        if (WalletManager.isEVMAccountSelected()) {
            binding.securityWrapper.gone()
            return
        }
        binding.securityWrapper.visible()
        binding.tvVerifiedInfo.text = if (token.isVerified) R.string.yes.res2String() else R.string.no.res2String()
        binding.tvContractAddress.text = token.tokenAddress()
        binding.tvContractAddress.paintFlags = binding.tvContractAddress.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvContractAddress.setOnClickListener {
            openBrowser(activity, "https://www.flowscan.io/ft/token/${token.tokenIdentifier()}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindStorageInfo(token: FungibleToken) {
        if (WalletManager.isEVMAccountSelected().not() && token.isFlowToken()) {
            binding.storageWrapper.visible()
            binding.tvStorageUsage.text = AccountInfoManager.getStorageUsageFlow()
            binding.tvTotalBalance.text = AccountInfoManager.getTotalFlowBalance()

            binding.tvStorageUsageProgress.text = getString(
                R.string.storage_info_count,
                toHumanReadableSIPrefixes(AccountInfoManager.getStorageUsed()),
                toHumanReadableSIPrefixes(AccountInfoManager.getStorageCapacity())
            )
            val progress = AccountInfoManager.getStorageUsageProgress()
            binding.tvStorageUsagePercent.text = (progress * 100).formatNum(3) + "%"
            binding.storageInfoProgress.progress = (progress * 1000).toInt()
        } else {
            binding.storageWrapper.gone()
        }
    }

    private fun bindAccessible(token: FungibleToken) {
        if (ChildAccountCollectionManager.isTokenAccessible(token.tokenContractName(), token.tokenAddress())) {
            binding.inaccessibleTip.gone()
            return
        }
        val accountName = WalletManager.childAccount(WalletManager.selectedWalletAddress())?.name ?: R.string.default_child_account_name.res2String()
        binding.tvInaccessibleTip.text = activity.getString(R.string.inaccessible_token_tip, token.name, accountName)
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

    private fun setupStakingRewards(token: FungibleToken) {
        with(binding.stakingRewardWrapper) {
            val currency = selectedCurrency()
            val coinRate = token.tokenPrice()
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