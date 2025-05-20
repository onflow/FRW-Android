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

    fun init() {
        logd(TAG, "init() called. Stack trace: ${Thread.currentThread().stackTrace.joinToString("\n") { it.toString() }}")
        if (isInitializing) {
            logd(TAG, "Already initializing, skipping")
            return
        }
        isInitializing = true
        try {
            ioScope {
                logd(TAG, "Initializing WalletManager")
                val address = getSelectedWalletAddress().orEmpty()
                logd(TAG, "Retrieved selected wallet address from preferences: '$address'")
                selectedWalletAddressRef.set(address)
                initializeWallet()
            }
        } catch (e: Exception) {
            logd(TAG, "Error during initialization: ${e.message}")
            logd(TAG, "Error stack trace: ${e.stackTraceToString()}")
        } finally {
            isInitializing = false
            logd(TAG, "Initialization completed")
        }
    }

    private fun initializeWallet() {
        logd(TAG, "initializeWallet() called. Stack trace: ${Thread.currentThread().stackTrace.joinToString("\n") { it.toString() }}")
        val account = AccountManager.get()
        logd(TAG, "Got account from AccountManager: $account")
        if (account != null) {
            logd(TAG, "Found account in AccountManager: ${account.wallet?.walletAddress()}")
            logd(TAG, "Account details - userInfo: ${account.userInfo}, keyStoreInfo: ${account.keyStoreInfo}")
            
            val walletAddress = account.wallet?.walletAddress()
            logd(TAG, "Wallet address from account: '$walletAddress'")
            
            if (walletAddress.isNullOrBlank()) {
                logd(TAG, "WARNING: Wallet address is null or blank")
                return
            }

            try {
                // Get the private key from the keystore info
                val keystoreInfo = account.keyStoreInfo
                if (keystoreInfo.isNullOrBlank()) {
                    logd(TAG, "WARNING: No keystore info found in account")
                    return
                }

                // Parse the keystore info to get the private key
                val keystoreAddress = Gson().fromJson(keystoreInfo, KeystoreAddress::class.java)
                val privateKey = keystoreAddress.privateKey
                logd(TAG, "Got private key from keystore info")

                // Create a private key instance
                val key = PrivateKey.create(getStorage()).apply {
                    importPrivateKey(privateKey.hexToBytes(), KeyFormat.RAW)
                }
                logd(TAG, "Created and imported private key")

                // Create a new wallet using the private key
                currentWallet = WalletFactory.createKeyWallet(
                    key,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    getStorage()
                )
                logd(TAG, "Created wallet with address: '${currentWallet?.walletAddress()}'")

                // Set the wallet address if it's not already set
                if (selectedWalletAddressRef.get().isBlank() && !walletAddress.isNullOrBlank()) {
                    logd(TAG, "Setting initial wallet address to: '$walletAddress'")
                    selectWalletAddress(walletAddress)
                }
            } catch (e: Exception) {
                logd(TAG, "Error initializing wallet: ${e.message}")
                logd(TAG, "Error stack trace: ${e.stackTraceToString()}")
            }
        } else {
            logd(TAG, "No account found in AccountManager")
            // If we have a selected address but no account, try to use that address
            if (selectedWalletAddressRef.get().isNotBlank()) {
                logd(TAG, "Found selected address in preferences: '${selectedWalletAddressRef.get()}'")
                // Keep the selected address in preferences but don't create a wallet yet
                // The wallet will be created when an account is added
            } else {
                logd(TAG, "No selected address found in preferences")
            }
        }
    }

    fun walletUpdate() {
        wallet()?.let { refreshChildAccount(it) }
    }

    fun wallet(): Wallet? = currentWallet

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

        val account = wallet()?.accounts?.values?.flatten()?.firstOrNull { 
            it.address.equals(address, ignoreCase = true) 
        }
        
        val networkStr = if (account == null) {
            logd(TAG, "No matching account found in wallet, checking child accounts")
            val walletAddress = childAccountMap.values
                .firstOrNull { child -> child.get().any { it.address.equals(address, true) } }?.address

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
        selectedWalletAddressRef.set("")
        childAccountMap.clear()
        currentWallet = null
        lastAddressCheck = 0
        logd(TAG, "WalletManager state cleared")
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
}

// Extension functions for backward compatibility
fun Wallet?.walletAddress(): String? {
    return this?.accounts?.values?.flatten()?.firstOrNull()?.address
}

fun WalletListData.walletAddress(): String? {
    return wallet()?.address()?.toAddress()
}