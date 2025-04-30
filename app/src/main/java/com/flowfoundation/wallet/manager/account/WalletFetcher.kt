package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.retrofit
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
import kotlin.concurrent.scheduleAtFixedRate

object WalletFetcher {
    private val TAG = WalletFetcher::class.java.simpleName

    private val listeners = CopyOnWriteArrayList<WeakReference<OnWalletDataUpdate>>()
    private val apiService by lazy { retrofit().create(ApiService::class.java) }

    // New Flow Wallet Kit SDK instances
    private val wallet = Wallet()
    private val accountManager = FlowAccountManager(wallet)

    fun fetch() {
        ioScope {
            var dataReceived = false
            var firstAttempt = true
            var timer: Timer? = null
            
            while (!dataReceived) {
                delay(5000)
                runCatching {
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
                        timer = Timer()
                        timer!!.scheduleAtFixedRate(0, 20000) {
                            ioScope {
                                apiService.manualAddress()
                            }
                        }
                        firstAttempt = false
                    }
                }.onFailure {
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