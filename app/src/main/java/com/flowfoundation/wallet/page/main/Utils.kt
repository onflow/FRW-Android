package com.flowfoundation.wallet.page.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.size
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.LayoutMainDrawerLayoutBinding
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.networkId
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalanceWithAddress
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.clearWebViewCache
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.clearCacheDir
import com.flowfoundation.wallet.utils.extensions.colorStateList
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.setMeowDomainClaimed
import com.flowfoundation.wallet.utils.shortenEVMString
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateAccountTransferCount
import com.flowfoundation.wallet.utils.updateChainNetworkPreference
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.FlowLoadingDialog
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode


enum class HomeTab(val index: Int) {
    WALLET(0),
    NFT(1),
    EXPLORE(2),
    ACTIVITY(3),
    PROFILE(4),
}

private val svgMenu by lazy {
    listOf(
        R.drawable.ic_wallet,   // Replace with actual SVG icons
        R.drawable.ic_wallet,
        R.drawable.ic_explore,
        R.drawable.ic_wallet,
        R.drawable.ic_profile
    )
}


private val menuColor by lazy {
    listOf(
        R.color.bottom_navigation_color_wallet,
        R.color.bottom_navigation_color_wallet,
        R.color.bottom_navigation_color_wallet,
        R.color.bottom_navigation_color_wallet,
        R.color.bottom_navigation_color_wallet,
    )
}

fun BottomNavigationView.activeColor(index: Int): Int {
    return menuColor[index].colorStateList(context)
        ?.getColorForState(intArrayOf(android.R.attr.state_checked), 0)!!
}

fun BottomNavigationView.setSvgDrawable(index: Int, isSelected: Boolean) {
    if (index !in svgMenu.indices) {
        return
    }
    menu.getItem(index).setIcon(svgMenu[index])  // Set the vector drawable
}


fun LayoutMainDrawerLayoutBinding.refreshWalletList(refreshBalance: Boolean = false) {
    ioScope {
        val userInfo = AccountManager.userInfo() ?: return@ioScope
        uiScope {
            llMainAccount.removeAllViews()

            // todo multi account
            val wallet = WalletManager.wallet()?.wallet() ?: return@uiScope
            val list = mutableListOf<WalletData?>().apply {
                add(wallet)
            }.filterNotNull()

            if (list.isEmpty()) {
                return@uiScope
            }

            list.forEach { walletItem ->
                val itemView = LayoutInflater.from(root.context)
                    .inflate(R.layout.item_wallet_list_main_account, llMainAccount, false)
                (itemView as ViewGroup).setupWallet(walletItem, userInfo, refreshBalance)
                llMainAccount.addView(itemView)
            }
            this.setupLinkedAccount(wallet, userInfo, refreshBalance)
        }
    }
}

private fun LayoutMainDrawerLayoutBinding.setupLinkedAccount(
    wallet: WalletData,
    userInfo: UserInfoData,
    refreshBalance: Boolean
) {
    llLinkedAccount.removeAllViews()
    if (EVMWalletManager.showEVMAccount(wallet.network())) {
        EVMWalletManager.getEVMAccount()?.let {
            val childView = LayoutInflater.from(root.context)
                .inflate(R.layout.item_wallet_list_child_account, llLinkedAccount, false)
            childView.setupWalletItem(
                WalletItemData(
                    address = it.address,
                    name = it.name,
                    icon = it.icon,
                    isSelected = WalletManager.selectedWalletAddress() == it.address

                ),
                isEVMAccount = true,
                refreshBalance = refreshBalance
            )
            llLinkedAccount.addView(childView)
            clEvmLayout.gone()
        }
    }
    WalletManager.childAccountList(wallet.address())?.get()?.forEach { childAccount ->
        val childView = LayoutInflater.from(root.context)
            .inflate(R.layout.item_wallet_list_child_account, llLinkedAccount, false)
        childAccount.address.walletData(userInfo)?.let { data ->
            childView.setupWalletItem(data)
            llLinkedAccount.addView(childView)
        }
    }
    tvLinkedAccount.setVisible(llLinkedAccount.size > 0)
}

@SuppressLint("SetTextI18n")
private fun ViewGroup.setupWallet(
    wallet: WalletData,
    userInfo: UserInfoData,
    refreshBalance: Boolean
) {
    val data = wallet.address()?.walletData(userInfo) ?: return

    val itemView = findViewById<View>(R.id.wallet_item)
    val iconView = findViewById<TextView>(R.id.wallet_icon_view)
    val nameView = findViewById<TextView>(R.id.wallet_name_view)
    val balanceView = findViewById<TextView>(R.id.wallet_balance_view)
    val addressView = findViewById<TextView>(R.id.wallet_address_view)
    val selectedView = findViewById<ImageView>(R.id.wallet_selected_view)
    val copyView = findViewById<ImageView>(R.id.iv_copy)

    val emojiInfo = AccountEmojiManager.getEmojiByAddress(wallet.address())
    iconView.text = Emoji.getEmojiById(emojiInfo.emojiId)
    iconView.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(emojiInfo.emojiId))
    nameView.text = emojiInfo.emojiName
    addressView.text = data.address.toAddress()
    itemView.setBackgroundResource(if (data.isSelected) R.drawable.bg_account_selected else R.drawable.bg_empty_placeholder)
    selectedView.setVisible(data.isSelected)

    if (refreshBalance) {
        bindFlowBalance(balanceView, data.address.toAddress())
    }
    copyView.setOnClickListener {
        textToClipboard(data.address)
        toast(msgRes = R.string.copy_address_toast)
    }

    setOnClickListener {
        FlowLoadingDialog(context).show()
        WalletManager.selectWalletAddress(data.address)
        ioScope {
            delay(200)
            doNetworkChangeTask()
            clearCacheDir()
            clearWebViewCache()
            setMeowDomainClaimed(false)
            TokenStateManager.clear()
            NftCollectionStateManager.clear()
            TransactionStateManager.reload()
            FlowCoinListManager.reload()
            BalanceManager.clear()
            StakingManager.clear()
            CryptoProviderManager.clear()
            updateAccountTransferCount(0)
            delay(1000)
            uiScope {
                MainActivity.relaunch(Env.getApp())
            }
        }
    }
}

private fun String.walletData(userInfo: UserInfoData): WalletItemData? {
    val wallet = WalletManager.wallet()?.wallets?.firstOrNull { it.address() == this }
    return if (wallet == null) {
        // child account
        val childAccount = WalletManager.childAccount(this) ?: return null
        WalletItemData(
            address = childAccount.address,
            name = childAccount.name,
            icon = childAccount.icon,
            isSelected = WalletManager.selectedWalletAddress() == this
        )
    } else {
        WalletItemData(
            address = wallet.address().orEmpty(),
            name = userInfo.username,
            icon = userInfo.avatar,
            isSelected = WalletManager.selectedWalletAddress() == this
        )
    }
}

private class WalletItemData(
    val address: String,
    val name: String,
    val icon: String,
    val isSelected: Boolean,
)

@SuppressLint("SetTextI18n")
private fun View.setupWalletItem(
    data: WalletItemData?, network: String? = null, isEVMAccount: Boolean = false,
    refreshBalance: Boolean = false
) {
    data ?: return
    val itemView = findViewById<View>(R.id.wallet_item)
    val emojiIconView = findViewById<TextView>(R.id.tv_icon_view)
    val iconView = findViewById<ImageView>(R.id.wallet_icon_view)
    val nameView = findViewById<TextView>(R.id.wallet_name_view)
    val balanceView = findViewById<TextView>(R.id.wallet_balance_view)
    val addressView = findViewById<TextView>(R.id.wallet_address_view)
    val selectedView = findViewById<ImageView>(R.id.wallet_selected_view)
    val copyView = findViewById<ImageView>(R.id.iv_copy)

    if (isEVMAccount) {
        val emojiInfo = AccountEmojiManager.getEmojiByAddress(data.address)
        emojiIconView.text = Emoji.getEmojiById(emojiInfo.emojiId)
        emojiIconView.backgroundTintList =
            ColorStateList.valueOf(Emoji.getEmojiColorRes(emojiInfo.emojiId))
        nameView.text = emojiInfo.emojiName
        addressView.text = shortenEVMString(data.address.toAddress())
        if (refreshBalance) {
            bindEVMFlowBalance(balanceView)
        }
        balanceView.visible()
    } else {
        nameView.text = data.name
        iconView.loadAvatar(data.icon)
        addressView.text = data.address.toAddress()
        balanceView.gone()
    }
    emojiIconView.setVisible(isEVMAccount)

    selectedView.setVisible(data.isSelected)
    itemView.setBackgroundResource(if (data.isSelected) R.drawable.bg_account_selected else R.drawable.bg_empty_placeholder)
    findViewById<TextView>(R.id.tv_evm_label)?.setVisible(isEVMAccount)

    copyView.setOnClickListener {
        textToClipboard(data.address)
        toast(msgRes = R.string.copy_address_toast)
    }

    setOnClickListener {
        val newNetwork = WalletManager.selectWalletAddress(data.address)

        if (newNetwork != chainNetWorkString()) {
            // network change
            if (network != chainNetWorkString()) {
                FlowLoadingDialog(context).show()
                updateChainNetworkPreference(networkId(newNetwork))
                ioScope {
                    delay(200)
                    refreshChainNetworkSync()
                    doNetworkChangeTask()
                    clearUserCache()
                    uiScope {
                        MainActivity.relaunch(Env.getApp())
                    }
                }
            }
        } else {
            FlowLoadingDialog(context).show()
            ioScope {
                delay(200)
                doNetworkChangeTask()
                clearCacheDir()
                clearWebViewCache()
                setMeowDomainClaimed(false)
                TokenStateManager.clear()
                NftCollectionStateManager.clear()
                TransactionStateManager.reload()
                FlowCoinListManager.reload()
                BalanceManager.clear()
                StakingManager.clear()
                CryptoProviderManager.clear()
                updateAccountTransferCount(0)
                delay(1000)
                uiScope {
                    MainActivity.relaunch(Env.getApp())
                }
            }
        }
    }
}

@SuppressLint("SetTextI18n")
fun bindFlowBalance(balanceView: TextView, address: String) {
    ioScope {
        val balance = AccountInfoManager.getCurrentFlowBalance() ?: cadenceQueryTokenBalanceWithAddress(
            FlowCoinListManager.getFlowCoin(),
            address
        ) ?: BigDecimal.ZERO
        uiScope {
            balanceView.text = "${balance.formatLargeBalanceNumber(isAbbreviation = true)} FLOW"
        }
    }
}

@SuppressLint("SetTextI18n")
fun bindEVMFlowBalance(balanceView: TextView) {
    ioScope {
        val balance = cadenceQueryCOATokenBalance() ?: BigDecimal.ZERO
        uiScope {
            balanceView.text = "${balance.formatLargeBalanceNumber(isAbbreviation = true)} FLOW"
        }
    }
}
