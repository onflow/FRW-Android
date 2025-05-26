package com.flowfoundation.wallet.manager.wallet

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.childaccount.ChildAccountList
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.utils.updateSelectedWalletAddress
import com.flowfoundation.wallet.wallet.toAddress
import com.flow.wallet.wallet.Wallet
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.ChainId
import com.google.gson.Gson
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.KeyFormat
import com.flowfoundation.wallet.cache.AccountCacheManager
import com.flowfoundation.wallet.manager.account.Accounts
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_MAINNET
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_TESTNET
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.onflow.flow.models.hexToBytes
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.onflow.flow.models.Account

object WalletManager {
    private val TAG = WalletManager::class.java.simpleName
    private var childAccountMap = mutableMapOf<String, ChildAccountList>()
    private val selectedWalletAddressRef = AtomicReference<String>("")
    private var currentWallet: Wallet? = null
    private var lastAddressCheck = 0L
    private val ADDRESS_CACHE_DURATION = 100L // Cache duration in milliseconds
    private var isInitializing = false
    private val initializationLock = Object()
    private var isInitialized = false

    fun init() {
        synchronized(initializationLock) {
            if (isInitializing || isInitialized) return
            isInitializing = true
            try {
                if (initializeWallet()) {
                    isInitialized = true          // mark ready ***after*** success
                }
            } finally { isInitializing = false }
        }
    }

    private fun initializeWallet(): Boolean {
        logd(TAG, "initializeWallet() called")

        val account = AccountManager.get() ?: run {
            logd(TAG, "-- bail: AccountManager.get() == null")
            return false
        }

        val storage = getStorage()

        // Handle keystore-based accounts
        if (!account.keyStoreInfo.isNullOrBlank()) {
            logd(TAG, "Initializing keystore-based wallet")
            
            /* 1. Build the key */
            val ks      = Gson().fromJson(account.keyStoreInfo, KeystoreAddress::class.java)
            val keyHex  = ks.privateKey.removePrefix("0x")
                .also { require(it.length == 64) { "Private key must be 32-byte hex" } }

            val key     = PrivateKey.create(storage).apply {
                importPrivateKey(keyHex.hexToBytes(), KeyFormat.RAW)
            }

            /* 2. Create the wallet */
            val newWallet = WalletFactory.createKeyWallet(
                key,
                setOf(ChainId.Mainnet, ChainId.Testnet),
                storage
            )
            currentWallet = newWallet
            logd(TAG, "Keystore wallet created, waiting for accounts to load...")
        }

        // Handle prefix-based accounts
        else if (!account.prefix.isNullOrBlank()) {
            logd(TAG, "Initializing prefix-based wallet")
            
            // Load the stored private key using the prefix-based ID
            val keyId = "prefix_key_${account.prefix}"
            val privateKey = try {
                PrivateKey.get(keyId, account.prefix!!, storage)
            } catch (e: Exception) {
                logd(TAG, "CRITICAL ERROR: Failed to load stored private key for prefix ${account.prefix}: ${e.message}")
                logd(TAG, "Cannot proceed without the stored key as it would create a different account")
                return false // Fail gracefully instead of creating a new key
            }
            logd(TAG, "Successfully loaded private key for prefix: ${account.prefix}")
            
            /* 2. Create the wallet */
            val newWallet = WalletFactory.createKeyWallet(
                privateKey,
                setOf(ChainId.Mainnet, ChainId.Testnet),
                storage
            )
            currentWallet = newWallet
            logd(TAG, "Prefix wallet created, waiting for accounts to load...")
        }

        else {
            logd(TAG, "-- bail: account has neither keyStoreInfo nor prefix")
            return false
        }

        // Wait for accounts to be loaded
        currentWallet?.let { wallet ->
            try {
                logd(TAG, "Wallet created, checking for existing account addresses...")
                logd(TAG, "Account wallet data: ${account.wallet}")
                
                // Check if we already have account addresses from the server
                if (account.wallet?.wallets?.isNotEmpty() == true) {
                    logd(TAG, "Found existing wallet addresses from server, using them:")
                    account.wallet!!.wallets?.forEach { walletData ->
                        walletData.blockchain?.forEach { blockchain ->
                            logd(TAG, "  - ${blockchain.chainId}: ${blockchain.address}")
                        }
                    }
                    
                    // The wallet should work with these addresses - no need to wait for auto-discovery
                    logd(TAG, "Wallet properly initialized with known addresses")
                    logd(TAG, "Primary wallet address: ${wallet.walletAddress()}")
                } else {
                    logd(TAG, "No existing wallet addresses found, attempting to wait for account discovery...")
                    
                    runBlocking { 
                        // Add timeout to prevent infinite hanging
                        withTimeout(5000) { // 5 second timeout
                            wallet.accountsFlow.first { accounts -> 
                                logd(TAG, "Checking accounts: $accounts (size: ${accounts.size})")
                                accounts.isNotEmpty() 
                            }
                        }
                        logd(TAG, "Accounts discovered successfully!")
                        logd(TAG, "Number of account chains: ${wallet.accounts.size}")
                        wallet.accounts.forEach { (chainId, accounts) ->
                            logd(TAG, "Chain $chainId has ${accounts.size} accounts:")
                            accounts.forEach { account ->
                                logd(TAG, "  - Account address: ${account.address}")
                            }
                        }
                        logd(TAG, "Primary wallet address: ${wallet.walletAddress()}")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                logd(TAG, "TIMEOUT: Account discovery failed, but wallet may still work with server addresses")
                logd(TAG, "Final wallet address: ${wallet.walletAddress()}")
            } catch (e: Exception) {
                logd(TAG, "ERROR during wallet initialization: ${e.message}")
                logd(TAG, "Error type: ${e.javaClass.simpleName}")
                logd(TAG, "Attempting to continue anyway...")
            }
        }

        /* 3. Persist the wallet info back into the Account (optional but handy) */
        if (account.wallet == null) {
            currentWallet?.let { newWallet ->
                val wallets = newWallet.accounts.map { (chainId, accounts) ->
                    WalletData(
                        blockchain = accounts.map {
                            BlockchainData(address = it.address, chainId = chainId.toString())
                        },
                        name = chainId.toString()
                    )
                }
                account.wallet = WalletListData(
                    id       = account.userInfo.username,         // or whatever id you use
                    username = account.userInfo.username,
                    wallets  = wallets
                )
                AccountCacheManager.cache(Accounts().apply { addAll(AccountManager.list()) })
            }
        }

        /* 4. Make sure WalletManager knows which address is selected */
        val address = currentWallet?.walletAddress()
        if (!address.isNullOrBlank()) {
            selectWalletAddress(address)
        }

        return true
    }

    fun walletUpdate() {
        wallet()?.let { refreshChildAccount(it) }
    }

    fun wallet(): Wallet? = synchronized(initializationLock) {
        if (!isInitialized) init()           // first pass

        if (currentWallet == null) {         // still null? retry once
            if (initializeWallet()) isInitialized = true
        }

        val currentNetwork = chainNetWorkString()
        logd(TAG, "Current network: $currentNetwork")

        // Get the account for the current network
        currentWallet?.let { wallet ->
            val networkAccount = wallet.accounts.entries.firstOrNull { (chainId, _) ->
                when (currentNetwork) {
                    NETWORK_NAME_MAINNET -> chainId == ChainId.Mainnet
                    NETWORK_NAME_TESTNET -> chainId == ChainId.Testnet
                    else -> false
                }
            }?.value?.firstOrNull()

            if (networkAccount != null) {
                logd(TAG, "Found account for $currentNetwork: ${networkAccount.address}")
                // Update the selected wallet address to match the current network
                selectWalletAddress(networkAccount.address)
            } else {
                logd(TAG, "No account found for network: $currentNetwork")
            }
        }

        logd(TAG, "Returning wallet: ${currentWallet}")
        currentWallet
    }

    fun isEVMAccountSelected(): Boolean {
        return selectedWalletAddress().toAddress().equals(EVMWalletManager.getEVMAddress()?.toAddress(), ignoreCase = true)
    }

    fun isSelfFlowAddress(address: String): Boolean {
        return wallet()?.accounts?.values?.flatten()?.any { it.address == address } == true
    }

    fun isChildAccountSelected(): Boolean {
        val accounts = wallet()?.accounts?.values?.flatten()
        if (accounts.isNullOrEmpty()) {
            return false
        }
        return accounts.none { it.address.equals(selectedWalletAddress(), ignoreCase = true) }
                && isEVMAccountSelected().not()
    }

    fun haveChildAccount(): Boolean {
        val accountList = childAccountList(wallet()?.accounts?.values?.flatten()?.firstOrNull()?.address)
        return accountList != null && accountList.get().isNotEmpty()
    }

    fun childAccountList(walletAddress: String? = null): ChildAccountList? {
        val address = (walletAddress ?: wallet()?.accounts?.values?.flatten()?.firstOrNull()?.address) ?: return null
        return childAccountMap[address]
    }

    fun childAccount(childAddress: String): ChildAccount? {
        return childAccountMap.toMap().values.flatMap { it.get() }.firstOrNull {
            it.address.equals(childAddress, ignoreCase = true)
        }
    }

    fun isChildAccount(address: String): Boolean {
        return childAccount(address) != null
    }

    fun refreshChildAccount() {
        childAccountMap.values.forEach { it.refresh() }
    }

    fun changeNetwork() {
        val currentNetwork = chainNetWorkString()
        logd(TAG, "Changing network to: $currentNetwork")
        
        // Get the first account for the current network
        val networkAccount = wallet()?.accounts?.entries?.firstOrNull { (chainId, _) ->
            when (currentNetwork) {
                NETWORK_NAME_MAINNET -> chainId == ChainId.Mainnet
                NETWORK_NAME_TESTNET -> chainId == ChainId.Testnet
                else -> false
            }
        }?.value?.firstOrNull()
        
        networkAccount?.address?.let { address ->
            logd(TAG, "Selecting network account: $address")
            selectWalletAddress(address)
        }
    }

    fun selectWalletAddress(address: String): String {
        logd(TAG, "Selecting wallet address: '$address'")
        if (address.isBlank()) {
            logd(TAG, "WARNING: Attempting to select blank address")
        }
        
        if (selectedWalletAddressRef.get().equals(address, ignoreCase = true)) {
            logd(TAG, "Address already selected, returning network string")
            return chainNetWorkString()
        }

        selectedWalletAddressRef.set(address)
        logd(TAG, "Updated selected wallet address to: '$address'")
        updateSelectedWalletAddress(address)
        logd(TAG, "Updated selected wallet address in preferences")

        // Add detailed logging about current wallet state
        logd(TAG, "Current wallet state: ${currentWallet?.walletAddress()}")
        logd(TAG, "Wallet accounts: ${currentWallet?.accounts?.values?.flatten()?.map { it.address }}")
        
        val account = wallet()?.accounts?.values?.flatten()?.firstOrNull { 
            it.address.equals(address, ignoreCase = true) 
        }
        
        logd(TAG, "Found matching account: ${account?.address}")
        logd(TAG, "Account chain ID: ${account?.chainID}")
        
        val networkStr = if (account == null) {
            logd(TAG, "No matching account found in wallet, checking child accounts")
            val walletAddress = childAccountMap.values
                .firstOrNull { child -> child.get().any { it.address.equals(address, true) } }?.address

            logd(TAG, "Found wallet address in child accounts: $walletAddress")
            
            wallet()?.accounts?.values?.flatten()?.firstOrNull { 
                it.address.equals(walletAddress, ignoreCase = true) 
            }?.let { acc ->
                logd(TAG, "Found account in child accounts, chain ID: ${acc.chainID}")
                when (acc.chainID) {
                    ChainId.Mainnet -> "mainnet"
                    ChainId.Testnet -> "testnet"
                    else -> chainNetWorkString()
                }
            }
        } else {
            logd(TAG, "Found matching account in wallet, chain ID: ${account.chainID}")
            when (account.chainID) {
                ChainId.Mainnet -> "mainnet"
                ChainId.Testnet -> "testnet"
                else -> chainNetWorkString()
            }
        }

        logd(TAG, "Selected network string: ${networkStr ?: chainNetWorkString()}")
        return networkStr ?: chainNetWorkString()
    }

    fun selectedWalletAddress(): String {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAddressCheck < ADDRESS_CACHE_DURATION) {
            return selectedWalletAddressRef.get()
        }
        
        lastAddressCheck = currentTime
        logd(TAG, "Getting selected wallet address")
        val pref = selectedWalletAddressRef.get().toAddress()
        logd(TAG, "Current selected address: '$pref'")
        
        if (pref.isBlank()) {
            logd(TAG, "WARNING: Selected address is blank")
        }
        
        val isExist = childAccountMap.keys.contains(pref) || childAccount(pref) != null || EVMWalletManager.isEVMWalletAddress(pref)
        if (isExist) {
            logd(TAG, "Address exists in child accounts or EVM wallet, returning: '$pref'")
            return pref
        }

        // If we have a selected address in preferences, use it even if no wallet exists yet
        if (selectedWalletAddressRef.get().isNotBlank()) {
            logd(TAG, "Using selected address from preferences: '${selectedWalletAddressRef.get()}'")
            return selectedWalletAddressRef.get()
        }

        val defaultAddress = wallet()?.accounts?.values?.flatten()?.firstOrNull()?.address.orEmpty().apply {
            if (isNotBlank()) {
                logd(TAG, "No valid selected address found, using default wallet address: '$this'")
                selectedWalletAddressRef.set(this)
                updateSelectedWalletAddress(this)
            } else {
                logd(TAG, "WARNING: No default wallet address available")
            }
        }
        logd(TAG, "Returning wallet address: '$defaultAddress'")
        return defaultAddress
    }

    fun clear() {
        logd(TAG, "Clearing WalletManager state")
        synchronized(initializationLock) {
            selectedWalletAddressRef.set("")
            childAccountMap.clear()
            currentWallet = null
            lastAddressCheck = 0
            isInitialized = false
            logd(TAG, "WalletManager state cleared")
        }
    }

    private fun refreshChildAccount(wallet: Wallet) {
        wallet.accounts.values.flatten().firstOrNull()?.address?.let {
            if (childAccountMap.contains(it)) {
                childAccountMap[it]?.refresh()
            } else {
                childAccountMap[it] = ChildAccountList(it)
            }
        }
    }

    fun updateWallet(walletData: WalletListData) {
        logd(TAG, "updateWallet called with address: ${walletData.walletAddress()}")
        synchronized(initializationLock) {
            try {
                if (!isInitialized) {
                    logd(TAG, "WalletManager not initialized, calling init()")
                    init()
                }

                val account = AccountManager.get()
                logd(TAG, "Account from AccountManager: ${account?.wallet?.walletAddress()}")
                
                if (account == null) {
                    logd(TAG, "No account found in AccountManager")
                    return@synchronized
                }

                val storage = getStorage()
                val newWallet: Wallet

                // Handle keystore-based accounts
                if (!account.keyStoreInfo.isNullOrBlank()) {
                    logd(TAG, "Updating keystore-based wallet")
                    
                    // Parse the keystore info to get the private key
                    val keystoreAddress = Gson().fromJson(account.keyStoreInfo, KeystoreAddress::class.java)
                    logd(TAG, "Got private key from keystore info")

                    val keyHex = keystoreAddress.privateKey.removePrefix("0x")
                    require(keyHex.length == 64) { "Private key must be 32-byte hex" }

                    val keyBytes = keyHex.hexToBytes()
                    // Create a private key instance
                    val key = PrivateKey.create(storage).apply {
                        importPrivateKey(keyBytes, KeyFormat.RAW)
                    }
                    logd(TAG, "Created and imported private key")

                    // Create a new wallet using the private key
                    newWallet = WalletFactory.createKeyWallet(
                        key,
                        setOf(ChainId.Mainnet, ChainId.Testnet),
                        storage
                    )
                }

                // Handle prefix-based accounts
                else if (!account.prefix.isNullOrBlank()) {
                    logd(TAG, "Updating prefix-based wallet")
                    
                    // Load the stored private key using the prefix-based ID
                    val keyId = "prefix_key_${account.prefix}"
                    val privateKey = try {
                        PrivateKey.get(keyId, account.prefix!!, storage)
                    } catch (e: Exception) {
                        logd(TAG, "CRITICAL ERROR: Failed to load stored private key for prefix ${account.prefix}: ${e.message}")
                        logd(TAG, "Cannot proceed without the stored key as it would create a different account")
                        return@synchronized
                    }
                    logd(TAG, "Successfully loaded private key for prefix: ${account.prefix}")
                    
                    // Create a new wallet using the private key
                    newWallet = WalletFactory.createKeyWallet(
                        privateKey,
                        setOf(ChainId.Mainnet, ChainId.Testnet),
                        storage
                    )
                }

                else {
                    logd(TAG, "Account has neither keystore info nor prefix, cannot update wallet")
                    return@synchronized
                }
                
                // Wait for accounts to be loaded - but handle server addresses like initializeWallet
                logd(TAG, "Wallet created, checking for existing account addresses...")
                logd(TAG, "Account wallet data: ${account.wallet}")
                
                // Check if we already have account addresses from the server
                if (account.wallet?.wallets?.isNotEmpty() == true) {
                    logd(TAG, "Found existing wallet addresses from server, using them:")
                    account.wallet!!.wallets?.forEach { walletData ->
                        walletData.blockchain?.forEach { blockchain ->
                            logd(TAG, "  - ${blockchain.chainId}: ${blockchain.address}")
                        }
                    }
                    
                    // The wallet should work with these addresses - no need to wait for auto-discovery
                    logd(TAG, "Wallet properly initialized with known addresses")
                    logd(TAG, "Primary wallet address: ${newWallet.walletAddress()}")
                } else {
                    logd(TAG, "No existing wallet addresses found, attempting to wait for account discovery...")
                    
                    try {
                        // Wait for accounts to be loaded using flow's first() operation
                        runBlocking { 
                            // Add timeout to prevent infinite hanging
                            withTimeout(5000) { // 5 second timeout
                                newWallet.accountsFlow.first { accounts -> 
                                    logd(TAG, "Checking accounts: $accounts (size: ${accounts.size})")
                                    accounts.isNotEmpty() 
                                }
                            }
                            logd(TAG, "Accounts discovered successfully!")
                            logd(TAG, "Number of account chains: ${newWallet.accounts.size}")
                            newWallet.accounts.forEach { (chainId, accounts) ->
                                logd(TAG, "Chain $chainId has ${accounts.size} accounts:")
                                accounts.forEach { account ->
                                    logd(TAG, "  - Account address: ${account.address}")
                                }
                            }
                            logd(TAG, "Primary wallet address: ${newWallet.walletAddress()}")
                        }
                    } catch (e: TimeoutCancellationException) {
                        logd(TAG, "TIMEOUT: Account discovery failed, but wallet may still work with server addresses")
                        logd(TAG, "Final wallet address: ${newWallet.walletAddress()}")
                    } catch (e: Exception) {
                        logd(TAG, "ERROR during wallet update: ${e.message}")
                        logd(TAG, "Error type: ${e.javaClass.simpleName}")
                        logd(TAG, "Attempting to continue anyway...")
                    }
                }

                logd(TAG, "Created new wallet with address: '${newWallet.walletAddress()}'")

                // Update the current wallet reference
                currentWallet = newWallet
                logd(TAG, "Updated current wallet reference")
                
                // Manually add accounts from server to the wallet
                // This is needed because new accounts may not be indexed yet by the key indexer
                account.wallet?.wallets?.forEach { walletData ->
                    walletData.blockchain?.forEach { blockchain ->
                        try {
                            val chainId = when (blockchain.chainId?.lowercase()) {
                                "mainnet" -> ChainId.Mainnet
                                "testnet" -> ChainId.Testnet
                                else -> null
                            }
                            
                            if (chainId != null && !blockchain.address.isNullOrBlank()) {
                                logd(TAG, "Fetching account ${blockchain.address} from ${blockchain.chainId} using Flow API")
                                
                                // Use the wallet's fetchAccountByAddress method to get the account directly
                                // from the Flow network, bypassing the key indexer
                                newWallet.fetchAccountByAddress(blockchain.address!!, chainId)
                                logd(TAG, "Successfully fetched and added account ${blockchain.address} to wallet")
                            }
                        } catch (e: Exception) {
                            logd(TAG, "Error fetching account ${blockchain.address}: ${e.message}")
                        }
                    }
                }

                // Refresh child accounts
                refreshChildAccount(newWallet)
                logd(TAG, "Refreshed child accounts for address $newWallet")

                // Refresh EVM wallet/account
//                EVMWalletManager.init()
//                logd(TAG, "Refreshed EVM wallet/account")
                
                // Update selected address if needed
                val walletAddress = newWallet.walletAddress()
                if (walletAddress != null && selectedWalletAddressRef.get().isBlank()) {
                    logd(TAG, "Setting initial wallet address to: '$walletAddress'")
                    selectWalletAddress(walletAddress)
                }

                // Force a wallet update
                walletUpdate()
                logd(TAG, "Triggered wallet update")
            } catch (e: Exception) {
                logd(TAG, "Error updating wallet: ${e.message}")
                logd(TAG, "Error stack trace: ${e.stackTraceToString()}")
            }
        }
    }
}

// Extension functions for backward compatibility
fun Wallet?.walletAddress(): String? {
    return this?.accounts?.values?.flatten()?.firstOrNull()?.address
}

fun WalletListData.walletAddress(): String? {
    return wallet()?.address()?.toAddress()
}