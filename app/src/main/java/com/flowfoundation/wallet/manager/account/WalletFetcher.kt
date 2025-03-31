package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.OtherHostService
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.network.retrofitWithHost
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.util.Timer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.scheduleAtFixedRate


object WalletFetcher {
    private val TAG = WalletFetcher::class.java.simpleName

    private val listeners = CopyOnWriteArrayList<WeakReference<OnWalletDataUpdate>>()

    private val apiService by lazy { retrofit().create(ApiService::class.java) }

    private val queryMainnetService by lazy {
        retrofitWithHost("https://production.key-indexer.flow.com").create(OtherHostService::class.java)
    }

    private val queryTestnetService by lazy {
        retrofitWithHost("https://staging.key-indexer.flow.com").create(OtherHostService::class.java)
    }

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

    suspend fun fetchAddressFromChain() {
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return
        val mainnetAccounts = queryMainnetService.queryAddress(cryptoProvider.getPublicKey())
        val mainnetAccount = mainnetAccounts.accounts.firstOrNull {
            it.hashAlgo == cryptoProvider.getHashAlgorithm().cadenceIndex && it.signAlgo == cryptoProvider.getSignatureAlgorithm().index //to-do add field
        }
        val testnetAccounts = queryTestnetService.queryAddress(cryptoProvider.getPublicKey())
        val testnetAccount = testnetAccounts.accounts.firstOrNull {
            it.hashAlgo == cryptoProvider.getHashAlgorithm().cadenceIndex && it.signAlgo == cryptoProvider.getSignatureAlgorithm().index //to-do add field
        }
        val data = WalletListData(
            id = Firebase.auth.currentUser?.uid.orEmpty(),
            username = AccountManager.userInfo()?.username.orEmpty(),
            wallets = listOf(
                WalletData(
                    name = "flow",
                    blockchain = if (testnetAccount == null) null else listOf(
                        BlockchainData(
                            chainId = "testnet",
                            address = testnetAccount.address
                        )
                    )
                ),
                WalletData(
                   name = "flow",
                   blockchain = if (mainnetAccount == null) null else listOf(
                        BlockchainData(
                            chainId = "mainnet",
                            address = mainnetAccount.address
                        )
                   )
                )
            )
        )
        AccountManager.updateWalletInfo(data)
        EVMWalletManager.updateEVMAddress()

        dispatchListeners(data)
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