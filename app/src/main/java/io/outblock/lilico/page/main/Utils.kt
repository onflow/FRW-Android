package io.outblock.lilico.page.main

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
import io.outblock.lilico.R
import io.outblock.lilico.databinding.LayoutMainDrawerLayoutBinding
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.app.NETWORK_NAME_MAINNET
import io.outblock.lilico.manager.app.NETWORK_NAME_SANDBOX
import io.outblock.lilico.manager.app.NETWORK_NAME_TESTNET
import io.outblock.lilico.manager.app.chainNetWorkString
import io.outblock.lilico.manager.app.doNetworkChangeTask
import io.outblock.lilico.manager.app.isDeveloperMode
import io.outblock.lilico.manager.app.networkId
import io.outblock.lilico.manager.app.refreshChainNetworkSync
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.clearUserCache
import io.outblock.lilico.network.model.UserInfoData
import io.outblock.lilico.network.model.WalletData
import io.outblock.lilico.utils.Env
import io.outblock.lilico.utils.extensions.capitalizeV2
import io.outblock.lilico.utils.extensions.colorStateList
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.extensions.res2color
import io.outblock.lilico.utils.extensions.setVisible
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.loadAvatar
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.utils.updateChainNetworkPreference
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
            LottieValueCallback(SimpleColorFilter(if (isSelected) activeColor(index) else io.outblock.lilico.R.color.neutrals8.res2color()))
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
                    add(wallets.firstOrNull { it.network() == NETWORK_NAME_SANDBOX })
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
    Boolean = false
) {
    data ?: return
    val itemView = findViewById<View>(R.id.wallet_item)
    val iconView = findViewById<ImageView>(R.id.wallet_icon_view)
    val nameView = findViewById<TextView>(R.id.wallet_name_view)
    val addressView = findViewById<TextView>(R.id.wallet_address_view)
    val selectedView = findViewById<ImageView>(R.id.wallet_selected_view)

    iconView.loadAvatar(data.icon)
    nameView.text = if (isChildAccount) "@${data.name}" else R.string.my_wallet.res2String()
    addressView.text = data.address
    selectedView.setVisible(data.isSelected)
    itemView.setBackgroundResource(if (data.isSelected) R.drawable.bg_wallet_item_selected else R.color.transparent)

    if (network != null) {
        findViewById<TextView>(R.id.wallet_network_view)?.apply {
            text = network.capitalizeV2()
            val color = when (network) {
                "mainnet" -> R.color.mainnet
                "testnet" -> R.color.testnet
                "sandbox" -> R.color.sandbox
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