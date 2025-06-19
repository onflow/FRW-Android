package com.flowfoundation.wallet.page.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.size
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.LayoutMainDrawerLayoutBinding
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_MAINNET
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_TESTNET
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.networkId
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalanceWithAddress
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.clearWebViewCache
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.clearCacheDir
import com.flowfoundation.wallet.utils.extensions.colorStateList
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
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
import org.onflow.flow.ChainId
import java.math.BigDecimal

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
        val userInfo = AccountManager.userInfo()
        Log.d("DrawerLayoutPresenter", "Refreshing wallet list - User info: ${userInfo?.username}, avatar: ${userInfo?.avatar}")
        
        // Try to get wallet multiple times if it's null
        var wallet = WalletManager.wallet()
        var retryCount = 0
        while (wallet == null && retryCount < 5) {
            Log.d("DrawerLayoutPresenter", "Wallet is null, retry attempt ${retryCount + 1}")
            kotlinx.coroutines.delay(200)
            wallet = WalletManager.wallet()
            retryCount++
        }
        
        Log.d("DrawerLayoutPresenter", "Wallet after retries: ${wallet?.walletAddress()}")
        
        if (userInfo == null || wallet == null) {
            Log.d("DrawerLayoutPresenter", "User info or wallet is null after retries, skipping refresh")
            return@ioScope
        }

        val currentNetwork = chainNetWorkString()
        Log.d("DrawerLayoutPresenter", "Current network: $currentNetwork")

        // Filter accounts for current network
        var networkAccounts = wallet.accounts.entries.firstOrNull { (chainId, _) ->
            when (currentNetwork) {
                NETWORK_NAME_MAINNET -> chainId == ChainId.Mainnet
                NETWORK_NAME_TESTNET -> chainId == ChainId.Testnet
                else -> false
            }
        }?.value ?: emptyList()

        if (networkAccounts.isEmpty()) {
            Log.d("DrawerLayoutPresenter", "No network accounts found, waiting for account discovery...")
            kotlinx.coroutines.delay(1000)
            wallet = WalletManager.wallet()
            networkAccounts = wallet?.accounts?.entries?.firstOrNull { (chainId, _) ->
                when (currentNetwork) {
                    NETWORK_NAME_MAINNET -> chainId == ChainId.Mainnet
                    NETWORK_NAME_TESTNET -> chainId == ChainId.Testnet
                    else -> false
                }
            }?.value ?: emptyList()
        }

        Log.d("DrawerLayoutPresenter", "Found ${networkAccounts.size} accounts for network: $currentNetwork")

        if (networkAccounts.isEmpty()) {
            Log.d("DrawerLayoutPresenter", "No accounts found in wallet, checking server data...")
            val account = AccountManager.get()
            val serverWallets = account?.wallet?.wallets?.filter { walletData ->
                walletData.blockchain?.any { blockchain ->
                    blockchain.chainId.equals(currentNetwork, true) && blockchain.address.isNotBlank()
                } == true
            }
            
            if (!serverWallets.isNullOrEmpty()) {
                Log.d("DrawerLayoutPresenter", "Using server wallet data as fallback")
                val list = serverWallets.map { walletData ->
                    WalletData(
                        blockchain = walletData.blockchain?.filter { 
                            it.chainId.equals(currentNetwork, true) 
                        },
                        name = userInfo.username
                    )
                }.filterNotNull()

                if (list.isNotEmpty()) {
                    uiScope {
                        Log.d("DrawerLayoutPresenter", "Updating UI with ${list.size} server wallet accounts")
                        llMainAccount.removeAllViews()

                        list.forEach { walletItem ->
                            val itemView = LayoutInflater.from(root.context)
                                .inflate(R.layout.item_wallet_list_main_account, llMainAccount, false)
                            (itemView as ViewGroup).setupWallet(walletItem, userInfo)
                            llMainAccount.addView(itemView)
                        }
                    }
                    return@ioScope
                }
            }
        }

        val list = mutableListOf<WalletData?>().apply {
            val mainWalletAddress = networkAccounts.firstOrNull()?.address
            Log.d("DrawerLayoutPresenter", "Adding main wallet: $mainWalletAddress")
            
            if (mainWalletAddress != null) {
                add(WalletData(
                    blockchain = listOf(
                        BlockchainData(
                            address = mainWalletAddress,
                            chainId = currentNetwork
                        )
                    ),
                    name = userInfo.username
                ))
            }
        }.filterNotNull()

        if (list.isEmpty()) {
            Log.d("DrawerLayoutPresenter", "Wallet list is empty after filtering - accounts may still be loading")
            return@ioScope
        }

        val addressList = mutableListOf<String>()
        list.forEach { walletItem ->
            walletItem.address()?.let {
                addressList.add(it)
                Log.d("DrawerLayoutPresenter", "Added main wallet address to list: $it")
            }
        }

        // Only get child accounts for the current network's main account
        val mainAccountAddress = networkAccounts.firstOrNull()?.address
        val childAccounts = if (mainAccountAddress != null) {
            WalletManager.childAccountList(mainAccountAddress)?.get()
        } else null
        
        Log.d("DrawerLayoutPresenter", "Child accounts found: ${childAccounts?.size ?: 0}")
        
        childAccounts?.forEach { childAccount ->
            addressList.add(childAccount.address)
            Log.d("DrawerLayoutPresenter", "Added child account address: ${childAccount.address}")
        }

        // Only show EVM account if it's for the current network
        if (EVMWalletManager.showEVMAccount(currentNetwork)) {
            EVMWalletManager.getEVMAddress()?.let {
                addressList.add(it)
                Log.d("DrawerLayoutPresenter", "Added EVM address: $it")
            }
        }

        if (refreshBalance && llMainAccount.childCount > 0) {
            Log.d("DrawerLayoutPresenter", "Refreshing balances only")
            fetchAllBalancesAndUpdateUI(addressList)
            return@ioScope
        }

        uiScope {
            Log.d("DrawerLayoutPresenter", "Updating UI with ${list.size} main accounts")
            llMainAccount.removeAllViews()

            list.forEach { walletItem ->
                val itemView = LayoutInflater.from(root.context)
                    .inflate(R.layout.item_wallet_list_main_account, llMainAccount, false)
                (itemView as ViewGroup).setupWallet(walletItem, userInfo)
                llMainAccount.addView(itemView)
            }
            
            Log.d("DrawerLayoutPresenter", "Setting up linked accounts")
            wallet?.let { this.setupLinkedAccount(it, userInfo) }
        }

        Log.d("DrawerLayoutPresenter", "Fetching balances for ${addressList.size} addresses")
        this.fetchAllBalancesAndUpdateUI(addressList)
    }
}

private fun LayoutMainDrawerLayoutBinding.setupLinkedAccount(
    wallet: com.flow.wallet.wallet.Wallet,
    userInfo: UserInfoData
) {
    Log.d("DrawerLayoutPresenter", "Setting up linked accounts")
    llLinkedAccount.removeAllViews()
    
    // Check EVM account
    val showEVMAccount = EVMWalletManager.showEVMAccount(chainNetWorkString())
    Log.d("DrawerLayoutPresenter", "Show EVM account: $showEVMAccount")
    
    if (showEVMAccount) {
        val evmAccount = EVMWalletManager.getEVMAccount()
        Log.d("DrawerLayoutPresenter", "EVM account: ${evmAccount?.address}")
        
        evmAccount?.let {
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
    
    // Get main wallet address with fallbacks
    var mainWalletAddress = wallet.walletAddress()
    Log.d("DrawerLayoutPresenter", "Initial wallet address from wallet.walletAddress(): $mainWalletAddress")
    
    if (mainWalletAddress.isNullOrBlank()) {
        Log.d("DrawerLayoutPresenter", "wallet.walletAddress() returned null, trying fallbacks...")
        
        // Try to get from current network accounts
        val currentNetwork = chainNetWorkString()
        val networkAccount = wallet.accounts.entries.firstOrNull { (chainId, _) ->
            when (currentNetwork) {
                NETWORK_NAME_MAINNET -> chainId == ChainId.Mainnet
                NETWORK_NAME_TESTNET -> chainId == ChainId.Testnet
                else -> false
            }
        }?.value?.firstOrNull()
        
        if (networkAccount != null) {
            mainWalletAddress = networkAccount.address
            Log.d("DrawerLayoutPresenter", "Using network account address: $mainWalletAddress")
        } else {
            // Try to get from server data
            val account = AccountManager.get()
            val serverAddress = account?.wallet?.wallets?.firstOrNull { walletData ->
                walletData.blockchain?.any { blockchain ->
                    blockchain.chainId.equals(currentNetwork, true) && blockchain.address.isNotBlank()
                } == true
            }?.blockchain?.firstOrNull { it.chainId.equals(currentNetwork, true) }?.address
            
            if (!serverAddress.isNullOrBlank()) {
                mainWalletAddress = serverAddress
                Log.d("DrawerLayoutPresenter", "Using server address: $mainWalletAddress")
            } else {
                // Final fallback - any account
                val anyAccount = wallet.accounts.values.flatten().firstOrNull()
                if (anyAccount != null) {
                    mainWalletAddress = anyAccount.address
                    Log.d("DrawerLayoutPresenter", "Using fallback account: $mainWalletAddress")
                }
            }
        }
    }
    
    // Check child accounts
    val childAccounts = if (!mainWalletAddress.isNullOrBlank()) {
        WalletManager.childAccountList(mainWalletAddress)?.get()
    } else {
        null
    }
    Log.d("DrawerLayoutPresenter", "Child accounts count: ${childAccounts?.size ?: 0} for address: $mainWalletAddress")
    
    childAccounts?.forEach { childAccount ->
        Log.d("DrawerLayoutPresenter", "Processing child account: ${childAccount.address}, name: ${childAccount.name}")
        val childView = LayoutInflater.from(root.context)
            .inflate(R.layout.item_wallet_list_child_account, llLinkedAccount, false)
        childAccount.address.walletData(userInfo)?.let { data ->
            Log.d("DrawerLayoutPresenter", "Adding child account to UI: ${data.address}, name: ${data.name}")
            childView.setupWalletItem(data)
            llLinkedAccount.addView(childView)
        }
    }
    
    val hasLinkedAccounts = llLinkedAccount.childCount > 0
    Log.d("DrawerLayoutPresenter", "Has linked accounts: $hasLinkedAccounts")
    tvLinkedAccount.setVisible(hasLinkedAccounts)
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
        
        // Refresh balances immediately after selecting wallet address
        Log.d("Utils", "Triggering BalanceManager.refresh() after main wallet address selection")
        BalanceManager.refresh()
        
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
    val wallet = WalletManager.wallet()
    Log.d("Utils", "walletData called with address: '$this'")
    Log.d("Utils", "Main wallet address: '${wallet?.walletAddress()}'")
    
    // Check if this is the main wallet address
    val isMainWallet = if (wallet?.walletAddress() == this) {
        true
    } else {
        // Additional check: see if this address matches any account in the wallet
        val matchingAccount = wallet?.accounts?.values?.flatten()?.find { account ->
            account.address.equals(this, ignoreCase = true) ||
            account.address.equals("0x$this", ignoreCase = true) ||
            account.address.removePrefix("0x").equals(this.removePrefix("0x"), ignoreCase = true)
        }
        matchingAccount != null
    }
    
    return if (isMainWallet) {
        Log.d("Utils", "Creating main wallet data for: '$this'")
        val selectedAddress = WalletManager.selectedWalletAddress()
        Log.d("Utils", "Main account - selected address: '$selectedAddress', current address: '$this'")
        WalletItemData(
            address = this,
            name = userInfo.username,
            icon = userInfo.avatar,
            isSelected = selectedAddress.equals(this, ignoreCase = true) ||
                        selectedAddress.equals("0x$this", ignoreCase = true) ||
                        selectedAddress.removePrefix("0x").equals(this.removePrefix("0x"), ignoreCase = true)
        )
    } else {
        // child account
        Log.d("Utils", "Looking for child account with address: '$this'")
        val childAccount = WalletManager.childAccount(this)
        Log.d("Utils", "Found child account: ${childAccount?.address}, name: ${childAccount?.name}")
        
        if (childAccount == null) {
            Log.d("Utils", "No child account found for address: '$this'")
            return null
        }
        
        val selectedAddress = WalletManager.selectedWalletAddress()
        Log.d("Utils", "Child account - selected address: '$selectedAddress', child address: '${childAccount.address}'")
        
        // Normalize addresses for comparison (remove 0x prefix for comparison)
        val normalizedSelected = selectedAddress.removePrefix("0x")
        val normalizedChild = childAccount.address.removePrefix("0x")
        val isSelected = normalizedSelected.equals(normalizedChild, ignoreCase = true)
        
        Log.d("Utils", "Child account selection comparison - normalized selected: '$normalizedSelected', normalized child: '$normalizedChild', isSelected: $isSelected")
        
        Log.d("Utils", "Creating child account data - address: '${childAccount.address}', name: '${childAccount.name}', isSelected: $isSelected")
        WalletItemData(
            address = childAccount.address,
            name = childAccount.name,
            icon = childAccount.icon,
            isSelected = isSelected
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
        Log.d("Utils", "Child account clicked - data.address: '${data.address}'")
        Log.d("Utils", "Child account clicked - data.address.toAddress(): '${data.address.toAddress()}'")
        Log.d("Utils", "Child account clicked - data.name: '${data.name}'")
        Log.d("Utils", "Child account clicked - isEVMAccount: $isEVMAccount")
        
        val newNetwork = WalletManager.selectWalletAddress(data.address.toAddress())
        
        // Refresh balances immediately after selecting wallet address
        Log.d("Utils", "Triggering BalanceManager.refresh() after wallet address selection")
        BalanceManager.refresh()

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
                delay(1000)
                uiScope {
                    MainActivity.relaunch(Env.getApp())
                }
            }
        }
    }
}
