package com.flowfoundation.wallet.page.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.size
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.LayoutMainDrawerLayoutBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.networkId
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
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
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.setMeowDomainClaimed
import com.flowfoundation.wallet.utils.shortenEVMString
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateChainNetworkPreference
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.FlowLoadingDialog
import kotlinx.coroutines.delay

enum class HomeTab(val index: Int) {
    WALLET(0),
    NFT(1),
}

private val svgMenu by lazy {
    listOf(
        R.drawable.ic_home,
        R.drawable.ic_nfts,
        R.drawable.ic_explore,
        R.drawable.ic_activity,
        R.drawable.ic_settings
    )
}

private val svgMenuSelected by lazy {
    listOf(
        R.drawable.ic_home_filled,
        R.drawable.ic_nfts_filled,
        R.drawable.ic_explore_filled,
        R.drawable.ic_activity_filled,
        R.drawable.ic_settings_filled
    )
}


fun BottomNavigationView.activeColor(): Int {
    return R.color.bottom_navigation_color_wallet.colorStateList(context)
        ?.getColorForState(intArrayOf(android.R.attr.state_checked), 0)!!
}

fun BottomNavigationView.setSvgDrawable(index: Int) {
    if (index !in svgMenu.indices) {
        return
    }
    menu.getItem(index).setIcon(svgMenu[index])
}


fun LayoutMainDrawerLayoutBinding.refreshWalletList(refreshBalance: Boolean = false) {
    ioScope {
        val userInfo = AccountManager.userInfo() ?: return@ioScope
        // todo multi account
        val wallet = WalletManager.wallet()?.wallet() ?: return@ioScope
        val list = mutableListOf<WalletData?>().apply {
            add(wallet)
        }.filterNotNull()

        if (list.isEmpty()) {
            return@ioScope
        }

        val addressList = mutableListOf<String>()
        list.forEach { walletItem ->
            walletItem.address()?.let {
                addressList.add(it)
            }
        }

        WalletManager.childAccountList(wallet.address())?.get()?.forEach { childAccount ->
            addressList.add(childAccount.address)
        }

        EVMWalletManager.getEVMAddress()?.let {
            addressList.add(it)
        }

        if (refreshBalance && llMainAccount.childCount > 0) {
            fetchAllBalancesAndUpdateUI(addressList)
            return@ioScope
        }

        uiScope {
            llMainAccount.removeAllViews()

            list.forEach { walletItem ->
                val itemView = LayoutInflater.from(root.context)
                    .inflate(R.layout.item_wallet_list_main_account, llMainAccount, false)
                (itemView as ViewGroup).setupWallet(walletItem, userInfo)
                llMainAccount.addView(itemView)
            }
            this.setupLinkedAccount(wallet, userInfo)
        }

        this.fetchAllBalancesAndUpdateUI(addressList)
    }
}

private fun LayoutMainDrawerLayoutBinding.setupLinkedAccount(
    wallet: WalletData,
    userInfo: UserInfoData
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
                isEVMAccount = true
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
    userInfo: UserInfoData
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
    balanceView.tag = data.address.toAddress()
    itemView.setBackgroundResource(if (data.isSelected) R.drawable.bg_account_selected else R.drawable.bg_empty_placeholder)
    selectedView.setVisible(data.isSelected)
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
            NftCollectionStateManager.clear()
            TransactionStateManager.reload()
            FungibleTokenListManager.clear()
            StakingManager.clear()
            CryptoProviderManager.clear()
            delay(1000)
            uiScope {
                MainActivity.relaunch(Env.getApp())
            }
        }
    }
}

@SuppressLint("SetTextI18n")
private fun LayoutMainDrawerLayoutBinding.fetchAllBalancesAndUpdateUI(addressList: List<String>) {
    ioScope {
        val balanceMap = cadenceGetAllFlowBalance(addressList) ?: return@ioScope
        uiScope {
            for (i in 0 until llMainAccount.childCount) {
                val itemView = llMainAccount.getChildAt(i) as? ViewGroup ?: continue
                val balanceView = itemView.findViewById<TextView>(R.id.wallet_balance_view) ?: continue
                val address = balanceView.tag as? String ?: continue

                balanceMap[address]?.let { balance ->
                    balanceView.text = "${balance.formatLargeBalanceNumber(isAbbreviation = true)} FLOW"
                }
            }

            for (i in 0 until llLinkedAccount.childCount) {
                val itemView = llLinkedAccount.getChildAt(i) as? ViewGroup ?: continue
                val balanceView = itemView.findViewById<TextView>(R.id.wallet_balance_view) ?: continue

                val fullAddress = balanceView.tag as? String ?: continue
                balanceMap[fullAddress]?.let { balance ->
                    balanceView.text = "${balance.formatLargeBalanceNumber(isAbbreviation = true)} FLOW"
                }
            }
        }
    }
}

fun BottomNavigationView.updateIcons() {
    for (i in 0 until menu.size()) {
        val menuItem = menu.getItem(i)
        val isSelected = menuItem.itemId == selectedItemId
        val iconRes = if (isSelected) {
            svgMenuSelected[i]
        } else {
            svgMenu[i]
        }
        menuItem.setIcon(iconRes)
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
    data: WalletItemData?, network: String? = null, isEVMAccount: Boolean = false
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
    } else {
        nameView.text = data.name
        iconView.loadAvatar(data.icon)
        addressView.text = data.address.toAddress()
    }
    balanceView.tag = data.address.toAddress()
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
                NftCollectionStateManager.clear()
                TransactionStateManager.reload()
                FungibleTokenListManager.clear()
                StakingManager.clear()
                CryptoProviderManager.clear()
                delay(1000)
                uiScope {
                    MainActivity.relaunch(Env.getApp())
                }
            }
        }
    }
}
