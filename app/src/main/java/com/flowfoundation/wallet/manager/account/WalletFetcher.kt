package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.WalletError
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flow.wallet.Network
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import kotlinx.coroutines.delay
import org.onflow.flow.ChainId
import java.lang.ref.WeakReference
import java.util.Timer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.scheduleAtFixedRate


object WalletFetcher {
    private val TAG = WalletFetcher::class.java.simpleName

    private val listeners = CopyOnWriteArrayList<WeakReference<OnWalletDataUpdate>>()

    private val apiService by lazy { retrofit().create(ApiService::class.java) }

    fun fetch() {
        ioScope {
            var dataReceived = false
            var firstAttempt = true
            var timer: Timer? = null
            
            // Helper function to trigger manual address creation
            fun triggerManualAddressIfNeeded() {
                if (firstAttempt) {
                    timer = Timer()
                    timer!!.scheduleAtFixedRate(0, 20000) {
                        ioScope {
                            apiService.manualAddress()
                        }
                    }
                    firstAttempt = false
                }
            }
            
            while (!dataReceived) {
                delay(5000)
                runCatching {
                    // Get current user's public key from crypto provider
                    val currentAccount = AccountManager.get()
                    val cryptoProvider = currentAccount?.let { 
                        CryptoProviderManager.generateAccountCryptoProvider(it)
                    }
                    
                    if (cryptoProvider == null) {
                        logd(TAG, "No crypto provider available, cannot fetch wallet using key indexer")
                        return@runCatching
                    }
                    
                    val publicKey = cryptoProvider.getPublicKey()
                    val chainId = when (chainNetWorkString()) {
                        "mainnet" -> ChainId.Mainnet
                        "testnet" -> ChainId.Testnet
                        else -> ChainId.Mainnet
                    }
                    
                    logd(TAG, "Fetching wallet using key indexer for public key: $publicKey")
                    
                    // Use key indexer to find accounts
                    val keyIndexerResponse = Network.findAccount(publicKey, chainId)
                    
                    if (keyIndexerResponse.accounts.isNotEmpty()) {
                        // Convert key indexer response to WalletListData format for compatibility
                        val account = keyIndexerResponse.accounts.first()
                        val blockchainData = BlockchainData(
                            address = account.address,
                            chainId = chainNetWorkString()
                        )
                        val walletData = WalletData(
                            blockchain = listOf(blockchainData),
                            name = "Flow Wallet"
                        )
                        val walletListData = WalletListData(
                            id = currentAccount?.userInfo?.username ?: "user",
                            username = currentAccount?.userInfo?.username ?: "user",
                            wallets = listOf(walletData)
                        )
                        
                        AccountManager.updateWalletInfo(walletListData)
                        EVMWalletManager.updateEVMAddress()
                        delay(300)
                        dispatchListeners(walletListData)
                        dataReceived = true
                        timer?.cancel()
                        timer = null
                    } else {
                        logd(TAG, "Key indexer returned empty accounts, trying API fallback")
                        
                        // Fallback: Try to get wallet data from API if key indexer doesn't have it yet
                        try {
                            val walletListData = apiService.getWalletList().data
                            if (walletListData != null && !walletListData.wallets.isNullOrEmpty()) {
                                // Check if we have actual blockchain addresses
                                val hasAddresses = walletListData.wallets?.any { wallet ->
                                    wallet.blockchain?.any { blockchain ->
                                        !blockchain.address.isNullOrBlank()
                                    } == true
                                } == true
                                
                                if (hasAddresses) {
                                    logd(TAG, "Successfully got wallet data from API fallback")
                                    AccountManager.updateWalletInfo(walletListData)
                                    EVMWalletManager.updateEVMAddress()
                                    delay(300)
                                    dispatchListeners(walletListData)
                                    dataReceived = true
                                    timer?.cancel()
                                    timer = null
                                } else {
                                    logd(TAG, "API returned wallet data but without addresses, continuing to manual address approach")
                                    triggerManualAddressIfNeeded()
                                }
                            } else {
                                logd(TAG, "API returned empty wallet data, continuing to manual address approach")
                                triggerManualAddressIfNeeded()
                            }
                        } catch (e: Exception) {
                            logd(TAG, "API fallback failed: ${e.message}, continuing to manual address approach")
                            triggerManualAddressIfNeeded()
                        }
                    }
                }.onFailure {
                    logd(TAG, "Key indexer fetch failed: ${it.message}")
                    ErrorReporter.reportWithMixpanel(WalletError.FETCH_FAILED, it)
                }
            }
        }
    }

    fun addListener(callback: OnWalletDataUpdate) {
        uiScope { this.listeners.add(WeakReference(callback)) }
    }

    private fun dispatchListeners(wallet: WalletListData) {
        logd(TAG, "dispatchListeners:$wallet")
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onWalletDataUpdate(wallet) }
        }
    }
}

interface OnWalletDataUpdate {
    fun onWalletDataUpdate(wallet: WalletListData)
}