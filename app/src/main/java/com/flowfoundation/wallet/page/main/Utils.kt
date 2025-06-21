package com.flowfoundation.wallet.page.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.LayoutMainDrawerLayoutBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_MAINNET
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_TESTNET
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.networkId
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMAccount
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
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
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.logd
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
        logd("DrawerLayoutPresenter", "Refreshing wallet list - User info: ${userInfo?.username}, avatar: ${userInfo?.avatar}")
        
        // Try to get wallet multiple times if it's null
        var wallet = WalletManager.wallet()
        var retryCount = 0
        while (wallet == null && retryCount < 5) {
            logd("DrawerLayoutPresenter", "Wallet is null, retry attempt ${retryCount + 1}")
            delay(200)
            wallet = WalletManager.wallet()
            retryCount++
        }
        
        logd("DrawerLayoutPresenter", "Wallet after retries: ${wallet?.walletAddress()}")
        
        if (userInfo == null || wallet == null) {
            logd("DrawerLayoutPresenter", "User info or wallet is null after retries, skipping refresh")
            return@ioScope
        }

        val currentNetwork = chainNetWorkString()
        logd("DrawerLayoutPresenter", "Current network: $currentNetwork")

        // Filter accounts for current network
        var networkAccounts = wallet.accounts.entries.firstOrNull { (chainId, _) ->
            when (currentNetwork) {
                NETWORK_NAME_MAINNET -> chainId == ChainId.Mainnet
                NETWORK_NAME_TESTNET -> chainId == ChainId.Testnet
                else -> false
            }
        }?.value ?: emptyList()

        if (networkAccounts.isEmpty()) {
            logd("DrawerLayoutPresenter", "No network accounts found, waiting for account discovery...")
            delay(1000)
            wallet = WalletManager.wallet()
            networkAccounts = wallet?.accounts?.entries?.firstOrNull { (chainId, _) ->
                when (currentNetwork) {
                    NETWORK_NAME_MAINNET -> chainId == ChainId.Mainnet
                    NETWORK_NAME_TESTNET -> chainId == ChainId.Testnet
                    else -> false
                }
            }?.value ?: emptyList()
        }

        logd("DrawerLayoutPresenter", "Found ${networkAccounts.size} accounts for network: $currentNetwork")

        if (networkAccounts.isEmpty()) {
            logd("DrawerLayoutPresenter", "No accounts found in wallet, checking server data...")
            val account = AccountManager.get()
            val serverWallets = account?.wallet?.wallets?.filter { walletData ->
                walletData.blockchain?.any { blockchain ->
                    blockchain.chainId.equals(currentNetwork, true) && blockchain.address.isNotBlank()
                } == true
            }
            
            if (!serverWallets.isNullOrEmpty()) {
                logd("DrawerLayoutPresenter", "Using server wallet data as fallback")
                val list = serverWallets.map { walletData ->
                    WalletData(
                        blockchain = walletData.blockchain?.filter { 
                            it.chainId.equals(currentNetwork, true) 
                        },
                        name = userInfo.username
                    )
                }

                if (list.isNotEmpty()) {
                    uiScope {
                        logd("DrawerLayoutPresenter", "Updating UI with ${list.size} server wallet accounts")
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
            logd("DrawerLayoutPresenter", "Adding main wallet: $mainWalletAddress")
            
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
            logd("DrawerLayoutPresenter", "Wallet list is empty after filtering - accounts may still be loading")
            return@ioScope
        }

        val addressList = mutableListOf<String>()
        list.forEach { walletItem ->
            walletItem.address()?.let {
                addressList.add(it)
                logd("DrawerLayoutPresenter", "Added main wallet address to list: $it")
            }
        }

        // FIXED: Add retry mechanism for child accounts loading - optimized
        val mainAccountAddress = networkAccounts.firstOrNull()?.address
        var childAccounts: List<ChildAccount>? = null
        if (mainAccountAddress != null) {
            // Try multiple times to get child accounts with smart timing
            var childRetryCount = 0
            while (childAccounts == null && childRetryCount < 3) {
                try {
                    childAccounts = WalletManager.childAccountList(mainAccountAddress)?.get()
                    if (childAccounts.isNullOrEmpty()) {
                        logd("DrawerLayoutPresenter", "Child accounts empty, retry attempt ${childRetryCount + 1}")
                        
                        // Force refresh child accounts if they're empty on first attempt
                        if (childRetryCount == 0) {
                            logd("DrawerLayoutPresenter", "Force refreshing child accounts in main refresh")
                            WalletManager.childAccountList(mainAccountAddress)?.refresh()
                        }
                        
                        // Progressive delay - start fast, get slower
                        val delayTime = when (childRetryCount) {
                            0 -> 300L  // Quick first retry
                            1 -> 600L  // Medium second retry  
                            else -> 1000L // Longer final retry
                        }
                        delay(delayTime)
                        childRetryCount++
                        childAccounts = null // Reset to try again
                    } else {
                        logd("DrawerLayoutPresenter", "Successfully loaded ${childAccounts.size} child accounts")
                        break // Successfully got child accounts
                    }
                } catch (e: Exception) {
                    logd("DrawerLayoutPresenter", "Error getting child accounts, retry attempt ${childRetryCount + 1}: ${e.message}")
                    
                    // Only force refresh on first error to avoid excessive calls
                    if (childRetryCount == 0) {
                        try {
                            WalletManager.childAccountList(mainAccountAddress)?.refresh()
                        } catch (refreshError: Exception) {
                            logd("DrawerLayoutPresenter", "Error force refreshing child accounts in main: ${refreshError.message}")
                        }
                    }
                    
                    delay(500L) // Fixed moderate delay for errors
                    childRetryCount++
                }
            }
            
            // Simplified fallback - only try direct fetch if we have no accounts and it's worth it
            if (childAccounts.isNullOrEmpty() && childRetryCount >= 3) {
                logd("DrawerLayoutPresenter", "Attempting direct child account fetch as final fallback")
                try {
                    val directWallet = WalletManager.wallet()
                    val directAccount = directWallet?.accounts?.values?.flatten()?.firstOrNull { it.address == mainAccountAddress }
                    directAccount?.let { account ->
                        // Use withTimeoutOrNull to prevent hanging
                        val directChildAccounts = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                            account.fetchChild()
                        }
                        if (!directChildAccounts.isNullOrEmpty()) {
                            childAccounts = directChildAccounts.map { childAccount ->
                                ChildAccount(
                                    address = childAccount.address.base16Value,
                                    name = childAccount.name ?: "Child Account",
                                    icon = childAccount.icon.orEmpty().ifBlank { "https://lilico.app/placeholder-2.0.png" },
                                    description = childAccount.description
                                )
                            }
                            logd("DrawerLayoutPresenter", "Direct fetch found ${childAccounts?.size} child accounts")
                        }
                    }
                } catch (e: Exception) {
                    logd("DrawerLayoutPresenter", "Direct child account fetch failed: ${e.message}")
                }
            }
        }
        
        logd("DrawerLayoutPresenter", "Child accounts found: ${childAccounts?.size ?: 0}")
        
        childAccounts?.forEach { childAccount ->
            addressList.add(childAccount.address)
            logd("DrawerLayoutPresenter", "Added child account address: ${childAccount.address}")
        }

        // FIXED: Add retry mechanism for EVM account loading
        var evmAddress: String? = null
        var evmRetryCount = 0
        while (evmAddress == null && evmRetryCount < 3) {
            try {
                if (EVMWalletManager.showEVMAccount(currentNetwork)) {
                    evmAddress = EVMWalletManager.getEVMAddress()
                    if (evmAddress == null) {
                        logd("DrawerLayoutPresenter", "EVM address null, retry attempt ${evmRetryCount + 1}")
                        delay(300) // Wait for EVM address to load
                        evmRetryCount++
                    } else {
                        addressList.add(evmAddress)
                        logd("DrawerLayoutPresenter", "Added EVM address: $evmAddress")
                        break
                    }
                } else {
                    break // EVM not enabled for this network
                }
            } catch (e: Exception) {
                logd("DrawerLayoutPresenter", "Error getting EVM address, retry attempt ${evmRetryCount + 1}: ${e.message}")
                delay(300)
                evmRetryCount++
            }
        }

        if (refreshBalance && llMainAccount.childCount > 0) {
            logd("DrawerLayoutPresenter", "Refreshing balances only")
            fetchAllBalancesAndUpdateUI(addressList)
            return@ioScope
        }

        uiScope {
            logd("DrawerLayoutPresenter", "Updating UI with ${list.size} main accounts")
            llMainAccount.removeAllViews()

            list.forEach { walletItem ->
                val itemView = LayoutInflater.from(root.context)
                    .inflate(R.layout.item_wallet_list_main_account, llMainAccount, false)
                (itemView as ViewGroup).setupWallet(walletItem, userInfo)
                llMainAccount.addView(itemView)
            }
            
            logd("DrawerLayoutPresenter", "Setting up linked accounts")
            wallet?.let { this.setupLinkedAccount(it, userInfo) }
            
            // FIXED: Also setup linked accounts with async retry in background
            wallet?.let { 
                ioScope {
                    // Attempt to load any missing accounts asynchronously
                    this@refreshWalletList.setupLinkedAccountAsync(it, userInfo)
                }
            }
        }

        logd("DrawerLayoutPresenter", "Fetching balances for ${addressList.size} addresses")
        this.fetchAllBalancesAndUpdateUI(addressList)
    }
}

private fun LayoutMainDrawerLayoutBinding.setupLinkedAccount(
    wallet: com.flow.wallet.wallet.Wallet,
    userInfo: UserInfoData
) {
    logd("DrawerLayoutPresenter", "Setting up linked accounts")
    llLinkedAccount.removeAllViews()
    
    // Check EVM account - simplified without blocking retries
    val showEVMAccount = EVMWalletManager.showEVMAccount(chainNetWorkString())
    logd("DrawerLayoutPresenter", "Show EVM account: $showEVMAccount")
    
    if (showEVMAccount) {
        try {
            val evmAccount = EVMWalletManager.getEVMAccount()
            logd("DrawerLayoutPresenter", "EVM account: ${evmAccount?.address}")
            
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
        } catch (e: Exception) {
            logd("DrawerLayoutPresenter", "Error getting EVM account: ${e.message}")
        }
    }
    
    // Get main wallet address with fallbacks
    var mainWalletAddress = wallet.walletAddress()
    logd("DrawerLayoutPresenter", "Initial wallet address from wallet.walletAddress(): $mainWalletAddress")
    
    if (mainWalletAddress.isNullOrBlank()) {
        logd("DrawerLayoutPresenter", "wallet.walletAddress() returned null, trying fallbacks...")
        
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
            logd("DrawerLayoutPresenter", "Using network account address: $mainWalletAddress")
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
                logd("DrawerLayoutPresenter", "Using server address: $mainWalletAddress")
            } else {
                // Final fallback - any account
                val anyAccount = wallet.accounts.values.flatten().firstOrNull()
                if (anyAccount != null) {
                    mainWalletAddress = anyAccount.address
                    logd("DrawerLayoutPresenter", "Using fallback account: $mainWalletAddress")
                }
            }
        }
    }
    
    // Check child accounts - simplified without blocking retries
    if (!mainWalletAddress.isNullOrBlank()) {
        try {
            val childAccounts = WalletManager.childAccountList(mainWalletAddress)?.get()
            logd("DrawerLayoutPresenter", "Child accounts count: ${childAccounts?.size ?: 0} for address: $mainWalletAddress")
            
            childAccounts?.forEach { childAccount ->
                logd("DrawerLayoutPresenter", "Processing child account: ${childAccount.address}, name: ${childAccount.name}")
                val childView = LayoutInflater.from(root.context)
                    .inflate(R.layout.item_wallet_list_child_account, llLinkedAccount, false)
                childAccount.address.walletData(userInfo)?.let { data ->
                    logd("DrawerLayoutPresenter", "Adding child account to UI: ${data.address}, name: ${data.name}")
                    childView.setupWalletItem(data)
                    llLinkedAccount.addView(childView)
                }
            }
        } catch (e: Exception) {
            logd("DrawerLayoutPresenter", "Error getting child accounts: ${e.message}")
        }
    }
    
    val hasLinkedAccounts = llLinkedAccount.childCount > 0
    logd("DrawerLayoutPresenter", "Has linked accounts: $hasLinkedAccounts")
    tvLinkedAccount.setVisible(hasLinkedAccounts)
}

// Add async version that can safely handle retries in background - optimized for performance
private suspend fun LayoutMainDrawerLayoutBinding.setupLinkedAccountAsync(
    wallet: com.flow.wallet.wallet.Wallet,
    userInfo: UserInfoData
) {
    logd("DrawerLayoutPresenter", "Setting up linked accounts asynchronously")
    
    llLinkedAccount.childCount
    var hasNewAccounts = false
    
    // Check EVM account with quick retry mechanism
    val showEVMAccount = EVMWalletManager.showEVMAccount(chainNetWorkString())
    if (showEVMAccount) {
        var evmAccount: EVMAccount? = null
        var evmRetryCount = 0
        
        // Quick retry mechanism for EVM account
        while (evmAccount == null && evmRetryCount < 2) { // Reduced from 3 to 2 retries
            try {
                evmAccount = EVMWalletManager.getEVMAccount()
                if (evmAccount == null) {
                    logd("DrawerLayoutPresenter", "EVM account null in async, retry attempt ${evmRetryCount + 1}")
                    delay(200) // Reduced delay
                    evmRetryCount++
                } else {
                    logd("DrawerLayoutPresenter", "EVM account successfully loaded in async: ${evmAccount.address}")
                    break
                }
            } catch (e: Exception) {
                logd("DrawerLayoutPresenter", "Error getting EVM account in async, retry attempt ${evmRetryCount + 1}: ${e.message}")
                delay(200) // Reduced delay
                evmRetryCount++
            }
        }
        
        // Check if we need to add EVM account to UI
        if (evmAccount != null) {
            val evmAlreadyExists = (0 until llLinkedAccount.childCount).any { index ->
                val childView = llLinkedAccount.getChildAt(index)
                val addressView = childView.findViewById<TextView>(R.id.wallet_address_view)
                addressView?.text?.toString()?.contains(shortenEVMString(evmAccount.address.toAddress())) == true
            }
            
            if (!evmAlreadyExists) {
                hasNewAccounts = true
                uiScope {
                    val childView = LayoutInflater.from(root.context)
                        .inflate(R.layout.item_wallet_list_child_account, llLinkedAccount, false)
                    childView.setupWalletItem(
                        WalletItemData(
                            address = evmAccount.address,
                            name = evmAccount.name,
                            icon = evmAccount.icon,
                            isSelected = WalletManager.selectedWalletAddress() == evmAccount.address
                        ),
                        isEVMAccount = true
                    )
                    llLinkedAccount.addView(childView)
                    clEvmLayout.gone()
                    logd("DrawerLayoutPresenter", "Added EVM account to UI asynchronously")
                }
            }
        }
    }
    
    // Get main wallet address for child accounts
    val mainWalletAddress = wallet.walletAddress() ?: run {
        val currentNetwork = chainNetWorkString()
        wallet.accounts.entries.firstOrNull { (chainId, _) ->
            when (currentNetwork) {
                NETWORK_NAME_MAINNET -> chainId == ChainId.Mainnet
                NETWORK_NAME_TESTNET -> chainId == ChainId.Testnet
                else -> false
            }
        }?.value?.firstOrNull()?.address
    }
    
    // Check child accounts with optimized retry mechanism
    if (!mainWalletAddress.isNullOrBlank()) {
        var childAccounts: List<ChildAccount>? = null
        var childRetryCount = 0
        
        while (childAccounts == null && childRetryCount < 2) { // Reduced from 3 to 2 retries
            try {
                childAccounts = WalletManager.childAccountList(mainWalletAddress)?.get()
                if (childAccounts.isNullOrEmpty()) {
                    logd("DrawerLayoutPresenter", "Child accounts empty in async, retry attempt ${childRetryCount + 1}")
                    
                    // Force refresh child accounts if they're empty on first attempt only
                    if (childRetryCount == 0) {
                        logd("DrawerLayoutPresenter", "Force refreshing child accounts on first retry")
                        WalletManager.childAccountList(mainWalletAddress)?.refresh()
                    }
                    
                    delay(500) // Reduced delay
                    childRetryCount++
                    childAccounts = null // Reset to try again
                } else {
                    logd("DrawerLayoutPresenter", "Child accounts successfully loaded in async: ${childAccounts.size}")
                    break
                }
            } catch (e: Exception) {
                logd("DrawerLayoutPresenter", "Error getting child accounts in async, retry attempt ${childRetryCount + 1}: ${e.message}")
                
                // Only refresh on first error
                if (childRetryCount == 0) {
                    try {
                        WalletManager.childAccountList(mainWalletAddress)?.refresh()
                    } catch (refreshError: Exception) {
                        logd("DrawerLayoutPresenter", "Error force refreshing child accounts: ${refreshError.message}")
                    }
                }
                
                delay(500) // Reduced delay
                childRetryCount++
            }
        }
        
        // Check if we need to add child accounts to UI
        childAccounts?.forEach { childAccount ->
            // Better duplicate detection - normalize addresses for comparison
            val normalizedChildAddress = childAccount.address.removePrefix("0x").lowercase()
            val childAlreadyExists = (0 until llLinkedAccount.childCount).any { index ->
                val childView = llLinkedAccount.getChildAt(index)
                val addressView = childView.findViewById<TextView>(R.id.wallet_address_view)
                val existingAddress = addressView?.text?.toString()?.removePrefix("0x")?.lowercase() ?: ""
                
                // Check both full address and shortened address formats
                existingAddress.contains(normalizedChildAddress) || 
                normalizedChildAddress.contains(existingAddress.take(8)) // Check first 8 chars for shortened addresses
            }
            
            logd("DrawerLayoutPresenter", "Child account ${childAccount.address} already exists: $childAlreadyExists")
            
            if (!childAlreadyExists) {
                hasNewAccounts = true
                uiScope {
                    val childView = LayoutInflater.from(root.context)
                        .inflate(R.layout.item_wallet_list_child_account, llLinkedAccount, false)
                    childAccount.address.walletData(userInfo)?.let { data ->
                        logd("DrawerLayoutPresenter", "Adding child account to UI asynchronously: ${data.address}, name: ${data.name}")
                        childView.setupWalletItem(data)
                        llLinkedAccount.addView(childView)
                    } ?: run {
                        logd("DrawerLayoutPresenter", "Failed to create wallet data for child account: ${childAccount.address}")
                    }
                }
            }
        }
    }
    
    // Update visibility if we added new accounts
    if (hasNewAccounts) {
        uiScope {
            val hasLinkedAccounts = llLinkedAccount.childCount > 0
            logd("DrawerLayoutPresenter", "Updated linked accounts visibility asynchronously: $hasLinkedAccounts")
            tvLinkedAccount.setVisible(hasLinkedAccounts)
        }
    }
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
        
        // Refresh tokens immediately after selecting wallet address
        logd("Utils", "Triggering FungibleTokenListManager.reload() after main wallet address selection")
        FungibleTokenListManager.reload()
        
        ioScope {
            delay(200)
            doNetworkChangeTask()
            clearCacheDir()
            clearWebViewCache()
            setMeowDomainClaimed(false)
            FungibleTokenListManager.clear()
            NftCollectionStateManager.clear()
            TransactionStateManager.reload()
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
    logd("Utils", "walletData called with address: '$this'")
    logd("Utils", "Main wallet address: '${wallet?.walletAddress()}'")
    
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
        logd("Utils", "Creating main wallet data for: '$this'")
        val selectedAddress = WalletManager.selectedWalletAddress()
        logd("Utils", "Main account - selected address: '$selectedAddress', current address: '$this'")
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
        logd("Utils", "Looking for child account with address: '$this'")
        val childAccount = WalletManager.childAccount(this)
        logd("Utils", "Found child account: ${childAccount?.address}, name: ${childAccount?.name}")
        
        if (childAccount == null) {
            logd("Utils", "No child account found for address: '$this'")
            return null
        }
        
        val selectedAddress = WalletManager.selectedWalletAddress()
        logd("Utils", "Child account - selected address: '$selectedAddress', child address: '${childAccount.address}'")
        
        // Normalize addresses for comparison (remove 0x prefix for comparison)
        val normalizedSelected = selectedAddress.removePrefix("0x")
        val normalizedChild = childAccount.address.removePrefix("0x")
        val isSelected = normalizedSelected.equals(normalizedChild, ignoreCase = true)
        
        logd("Utils", "Child account selection comparison - normalized selected: '$normalizedSelected', normalized child: '$normalizedChild', isSelected: $isSelected")
        
        logd("Utils", "Creating child account data - address: '${childAccount.address}', name: '${childAccount.name}', isSelected: $isSelected")
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
        logd("Utils", "Child account clicked - data.address: '${data.address}'")
        logd("Utils", "Child account clicked - data.address.toAddress(): '${data.address.toAddress()}'")
        logd("Utils", "Child account clicked - data.name: '${data.name}'")
        logd("Utils", "Child account clicked - isEVMAccount: $isEVMAccount")
        
        val newNetwork = WalletManager.selectWalletAddress(data.address.toAddress())
        
        // Refresh tokens immediately after selecting wallet address
        logd("Utils", "Triggering FungibleTokenListManager.reload() after wallet address selection")
        FungibleTokenListManager.reload()

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
                FungibleTokenListManager.clear()
                NftCollectionStateManager.clear()
                TransactionStateManager.reload()
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
