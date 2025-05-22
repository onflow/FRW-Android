package com.flowfoundation.wallet.manager.wallet

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.childaccount.ChildAccountList
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.utils.getSelectedWalletAddress
import com.flowfoundation.wallet.utils.ioScope
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
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import org.onflow.flow.models.hexToBytes
import java.util.concurrent.atomic.AtomicReference

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

        val keystoreInfo = account.keyStoreInfo ?: run {
            logd(TAG, "-- bail: account.keyStoreInfo == null")
            return false
        }

        /* 1. Build the key */
        val ks      = Gson().fromJson(keystoreInfo, KeystoreAddress::class.java)
        val keyHex  = ks.privateKey.removePrefix("0x")
            .also { require(it.length == 64) { "Private key must be 32-byte hex" } }

        val key     = PrivateKey.create(getStorage()).apply {
            importPrivateKey(keyHex.hexToBytes(), KeyFormat.RAW)
        }

        /* 2. Create the wallet */
        val newWallet = WalletFactory.createKeyWallet(
            key,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            getStorage()
        )
        currentWallet = newWallet
        logd(TAG, "Wallet restored, address = ${newWallet.walletAddress()}")

        /* 3. Persist the wallet info back into the Account (optional but handy) */
        if (account.wallet == null) {
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

        /* 4. Make sure WalletManager knows which address is selected */
        val address = newWallet.walletAddress()
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
        wallet()?.accounts?.values?.flatten()?.firstOrNull()?.address?.let { selectWalletAddress(it) }
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

                val keystoreInfo = account.keyStoreInfo
                logd(TAG, "Keystore info present: ${!keystoreInfo.isNullOrBlank()}")
                
                if (keystoreInfo.isNullOrBlank()) {
                    logd(TAG, "No keystore info found in account")
                    return@synchronized
                }

                // Parse the keystore info to get the private key
                val keystoreAddress = Gson().fromJson(keystoreInfo, KeystoreAddress::class.java)
                val privateKey = keystoreAddress.privateKey
                logd(TAG, "Got private key from keystore info")

                val keyHex   = keystoreAddress.privateKey.removePrefix("0x")
                require(keyHex.length == 64) { "Private key must be 32-byte hex" }

                val keyBytes = keyHex.hexToBytes()
                // Create a private key instance
                val key = PrivateKey.create(getStorage()).apply {
                    importPrivateKey(keyBytes, KeyFormat.RAW)
                }
                logd(TAG, "Created and imported private key")

                // Create a new wallet using the private key
                val newWallet = WalletFactory.createKeyWallet(
                    key,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    getStorage()
                )
                logd(TAG, "Created new wallet with address: '${newWallet.walletAddress()}'")

                // Update the current wallet reference
                currentWallet = newWallet
                logd(TAG, "Updated current wallet reference")

                // Refresh child accounts
                refreshChildAccount(newWallet)
                logd(TAG, "Refreshed child accounts")
                
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