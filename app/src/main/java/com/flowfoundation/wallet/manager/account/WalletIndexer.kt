package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.network.OtherHostService
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.retrofitWithHost
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAccount
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import io.outblock.wallet.CryptoProvider
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList


object WalletIndexer {

    private val TAG = WalletIndexer::class.java.simpleName
    private val listeners = CopyOnWriteArrayList<WeakReference<OnWalletUpdate>>()
    private val queryMainnetService by lazy {
        retrofitWithHost("https://production.key-indexer.flow.com").create(OtherHostService::class.java)
    }

    private val queryTestnetService by lazy {
        retrofitWithHost("https://staging.key-indexer.flow.com").create(OtherHostService::class.java)
    }

    suspend fun fetchAddressFromChain() {
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: run {
            logd(TAG, "No crypto provider available")
            return
        }

        try {
            val walletData = buildWalletData(cryptoProvider)
            updateWalletInfo(walletData)
        } catch (e: Exception) {
            logd(TAG, "Failed to fetch address: ${e.message}")
        }
    }

    private suspend fun buildWalletData(cryptoProvider: CryptoProvider): WalletListData {
        val (mainnetAccount, testnetAccount) = fetchAccounts(cryptoProvider)

        return WalletListData(
            id = Firebase.auth.currentUser?.uid.orEmpty(),
            username = AccountManager.userInfo()?.username.orEmpty(),
            wallets = buildWalletList(mainnetAccount, testnetAccount)
        )
    }

    private suspend fun fetchAccounts(cryptoProvider: CryptoProvider): Pair<KeystoreAccount?, KeystoreAccount?> {
        val publicKey = cryptoProvider.getPublicKey()
        val hashAlgo = cryptoProvider.getHashAlgorithm().index
        val signAlgo = cryptoProvider.getSignatureAlgorithm().index

        val mainnetAccounts = queryMainnetService.queryAddress(publicKey)
        val testnetAccounts = queryTestnetService.queryAddress(publicKey)

        val mainnetAccount = mainnetAccounts.accounts.firstOrNull {
            it.hashAlgo == hashAlgo && it.signAlgo == signAlgo
        }
        val testnetAccount = testnetAccounts.accounts.firstOrNull {
            it.hashAlgo == hashAlgo && it.signAlgo == signAlgo
        }

        return Pair(mainnetAccount, testnetAccount)
    }

    private fun buildWalletList(mainnetAccount: KeystoreAccount?, testnetAccount: KeystoreAccount?):
            List<WalletData> {
        return listOf(
            WalletData(
                name = "flow",
                blockchain = testnetAccount?.let {
                    listOf(BlockchainData(chainId = "testnet", address = it.address))
                }
            ),
            WalletData(
                name = "flow",
                blockchain = mainnetAccount?.let {
                    listOf(BlockchainData(chainId = "mainnet", address = it.address))
                }
            )
        )
    }

    private fun updateWalletInfo(data: WalletListData) {
        AccountManager.updateWalletInfo(data)
        EVMWalletManager.updateEVMAddress()
        dispatchListeners(data)
    }

    fun addListener(callback: OnWalletUpdate) {
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.add(WeakReference(callback))
        }
    }

    private fun dispatchListeners(wallet: WalletListData) {
        logd(TAG, "dispatchListeners:$wallet")
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onWalletUpdate(wallet) }
        }
    }
}

interface OnWalletUpdate {
    fun onWalletUpdate(wallet: WalletListData)
}