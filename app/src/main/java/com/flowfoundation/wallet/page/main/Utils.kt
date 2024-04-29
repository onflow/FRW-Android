package com.flowfoundation.wallet.page.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.LayoutMainDrawerLayoutBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_MAINNET
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_PREVIEWNET
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_TESTNET
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.isDeveloperMode
import com.flowfoundation.wallet.manager.app.networkId
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.capitalizeV2
import com.flowfoundation.wallet.utils.extensions.colorStateList
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateChainNetworkPreference
import kotlinx.coroutines.delay


enum class HomeTab(val index: Int) {
    WALLET(0),
    NFT(1),
    EXPLORE(2),
    PROFILE(3),
}

private val lottieMenu by lazy {
    listOf(
        R.raw.lottie_coinhover,
        R.raw.lottie_grid,
        R.raw.lottie_category,
        R.raw.lottie_avatar,
    )
}

private val menuColor by lazy {
    listOf(
        R.color.bottom_navigation_color_wallet,
        R.color.bottom_navigation_color_nft,
        R.color.bottom_navigation_color_explore,
        R.color.bottom_navigation_color_profile,
    )
}

fun BottomNavigationView.activeColor(index: Int): Int {
    return menuColor[index].colorStateList(context)
        ?.getColorForState(intArrayOf(android.R.attr.state_checked), 0)!!
}

fun BottomNavigationView.setLottieDrawable(
    index: Int,
    isSelected: Boolean,
    playAnimation: Boolean = false
) {
    menu.getItem(index).icon = LottieDrawable().apply {
        callback = this
        composition = LottieCompositionFactory.fromRawResSync(context, lottieMenu[index]).value
        addValueCallback(
            KeyPath("**"),
            LottieProperty.COLOR_FILTER,
            LottieValueCallback(SimpleColorFilter(if (isSelected) activeColor(index) else com.flowfoundation.wallet.R.color.neutrals8.res2color()))
        )
        if (playAnimation) playAnimation()
    }
}

fun LayoutMainDrawerLayoutBinding.refreshWalletList() {
    ioScope {
        val userInfo = AccountManager.userInfo() ?: return@ioScope
        uiScope {
            walletListWrapper.removeAllViews()

            val wallets = WalletManager.wallet()?.wallets ?: return@uiScope
            val list = mutableListOf<WalletData?>().apply {
                add(wallets.firstOrNull { it.network() == NETWORK_NAME_MAINNET })
                if (isDeveloperMode()) {
                    add(wallets.firstOrNull { it.network() == NETWORK_NAME_TESTNET })
                    add(wallets.firstOrNull { it.network() == NETWORK_NAME_PREVIEWNET })
                }
            }.filterNotNull()

            if (list.isEmpty()) {
                return@uiScope
            }

            list.forEach { wallet ->
                val itemView = LayoutInflater.from(root.context)
                    .inflate(R.layout.item_wallet_list, walletListWrapper, false)
                (itemView as ViewGroup).setupWallet(wallet, userInfo)
                walletListWrapper.addView(itemView)
            }
        }
    }
}

private fun ViewGroup.setupWallet(wallet: WalletData, userInfo: UserInfoData) {
    setupWalletItem(wallet.address()?.walletData(userInfo), wallet.network(), isChildAccount = false)
    val wrapper = findViewById<ViewGroup>(R.id.wallet_wrapper)
    WalletManager.childAccountList(wallet.address())?.get()?.forEach { childAccount ->
        val childView = LayoutInflater.from(context)
            .inflate(R.layout.item_wallet_list_child_account, this, false)
        childAccount.address.walletData(userInfo)?.let { data ->
            childView.setupWalletItem(data)
            wrapper.addView(childView)
        }
    }
    if (EVMWalletManager.showEVMAccount(wallet.network())) {
        EVMWalletManager.getEVMAccount()?.let {
            val childView = LayoutInflater.from(context)
                .inflate(R.layout.item_wallet_list_child_account, this, false)
            childView.setupWalletItem(
                WalletItemData(
                    address = it.address,
                    name = it.name,
                    icon = it.icon,
                    isSelected = WalletManager.selectedWalletAddress() == it.address

                ),
                isEVMAccount = true
            )
            wrapper.addView(childView)
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
    data: WalletItemData?, network: String? = null, isChildAccount:
    Boolean = false, isEVMAccount: Boolean = false
) {
    data ?: return
    val itemView = findViewById<View>(R.id.wallet_item)
    val iconView = findViewById<ImageView>(R.id.wallet_icon_view)
    val nameView = findViewById<TextView>(R.id.wallet_name_view)
    val addressView = findViewById<TextView>(R.id.wallet_address_view)
    val selectedView = findViewById<ImageView>(R.id.wallet_selected_view)

    iconView.loadAvatar(data.icon)
    nameView.text =
        if (isChildAccount) "@${data.name}" else if (isEVMAccount) data.name else R.string.my_wallet.res2String()
    addressView.text = data.address
    selectedView.setVisible(data.isSelected)
    itemView.setBackgroundResource(if (data.isSelected) R.drawable.bg_wallet_item_selected else R.color.transparent)
    findViewById<TextView>(R.id.tv_evm_label)?.setVisible(isEVMAccount)

    if (network != null) {
        findViewById<TextView>(R.id.wallet_network_view)?.apply {
            text = network.capitalizeV2()
            val color = when (network) {
                "mainnet" -> R.color.mainnet
                "testnet" -> R.color.testnet
                "previewnet" -> R.color.previewnet
                else -> R.color.text
            }
            setTextColor(color.res2color())
            backgroundTintList = ColorStateList.valueOf(color.res2color()).withAlpha(16)
            setVisible(true)
        }
    }

    setOnClickListener {
        val newNetwork = WalletManager.selectWalletAddress(data.address)
        if (newNetwork != chainNetWorkString()) {
            // network change
            if (network != chainNetWorkString()) {
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
            MainActivity.relaunch(Env.getApp())
        }
    }
}