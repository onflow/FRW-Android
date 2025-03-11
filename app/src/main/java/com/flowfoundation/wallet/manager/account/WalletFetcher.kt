package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import kotlinx.coroutines.delay
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
//            WalletManager.wallet()?.let { dispatchListeners(it) }
            var dataReceived = false
            var firstAttempt = true
            var timer: Timer? = null
            while (!dataReceived) {
                delay(5000)
                runCatching {
                    val resp = apiService.getWalletList()

                    // request success & wallet list is empty (wallet not create finish)
                    if (resp.status == 200 && !resp.data?.walletAddress().isNullOrBlank()) {
                        AccountManager.updateWalletInfo(resp.data!!)
                        EVMWalletManager.updateEVMAddress()
                        delay(300)
                        dispatchListeners(resp.data)
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