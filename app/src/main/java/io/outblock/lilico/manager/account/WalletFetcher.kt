package io.outblock.lilico.manager.account

import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.model.WalletListData
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.uiScope
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.util.Timer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.scheduleAtFixedRate


object WalletFetcher {
    private val TAG = WalletFetcher::class.java.simpleName

    private val listeners = CopyOnWriteArrayList<WeakReference<OnWalletDataUpdate>>()

    private val apiService by lazy { retrofit().create(ApiService::class.java) }

    suspend fun fetch() {
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