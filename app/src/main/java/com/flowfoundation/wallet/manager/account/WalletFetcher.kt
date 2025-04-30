package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.WalletError
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.wallet.AccountManager as FlowAccountManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.lang.ref.WeakReference
import java.util.Timer
import java.util.concurrent.CopyOnWriteArrayList

object WalletFetcher {
    private val TAG = WalletFetcher::class.java.simpleName

    private val listeners = CopyOnWriteArrayList<WeakReference<OnWalletDataUpdate>>()

    // New Flow Wallet Kit SDK instances
    private val wallet = Wallet()
    private val accountManager = FlowAccountManager(wallet)

    fun fetch() {
        ioScope {
            var dataReceived = false
            var firstAttempt = true
            var timer: Timer? = null
            
            while (!dataReceived) {
                try {
                    delay(5000)
                    val accounts = accountManager.accounts.first()
                    val currentAccount = accounts.values.flatten().firstOrNull()
                    
                    if (currentAccount != null) {
                        // Convert current account to WalletListData format
                        val walletData = WalletListData(
                            status = 200,
                            data = WalletListData.Data(
                                walletAddress = currentAccount.address,
                                walletId = currentAccount.id,
                                username = currentAccount.username
                            )
                        )
                        
                        AccountManager.updateWalletInfo(walletData.data)
                        EVMWalletManager.updateEVMAddress()
                        delay(300)
                        dispatchListeners(walletData)
                        dataReceived = true
                        timer?.cancel()
                        timer = null
                    } else if (firstAttempt) {
                        // Only log on first attempt
                        logd(TAG, "No current account found, retrying...")
                        firstAttempt = false
                    }
                } catch (e: Exception) {
                    ErrorReporter.report(WalletError.FETCH_WALLET_ERROR, e)
                    logd(TAG, "Error fetching wallet data: ${e.message}")
                }
            }
        }
    }

    fun addListener(callback: OnWalletDataUpdate) {
        if (listeners.firstOrNull { it.get() == callback } != null) {
            return
        }
        uiScope { listeners.add(WeakReference(callback)) }
    }

    private fun dispatchListeners(wallet: WalletListData) {
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onWalletDataUpdate(wallet) }
        }
    }

    fun clear() {
        listeners.clear()
    }
}

interface OnWalletDataUpdate {
    fun onWalletDataUpdate(wallet: WalletListData)
}