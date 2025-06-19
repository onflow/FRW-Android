package com.flowfoundation.wallet.page.profile.subpage.developer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flow.wallet.Network
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.utils.isRegistered
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.viewModelIOScope
import org.onflow.flow.ChainId

class DeveloperModeViewModel : ViewModel() {
    val progressVisibleLiveData = MutableLiveData<Boolean>()

    val resultLiveData = MutableLiveData<Boolean>()

    fun changeNetwork() {
        viewModelIOScope(this) {
            FlowCadenceApi.refreshConfig()
            val cacheExist = WalletManager.wallet() != null && !WalletManager.wallet()?.walletAddress().isNullOrBlank()
            if (!cacheExist && isRegistered()) {
                progressVisibleLiveData.postValue(true)
                try {
                    // Get current user's public key from crypto provider
                    val currentAccount = AccountManager.get()
                    val cryptoProvider = currentAccount?.let { 
                        CryptoProviderManager.generateAccountCryptoProvider(it)
                    }
                    
                    if (cryptoProvider == null) {
                        logd("DeveloperModeViewModel", "No crypto provider available, cannot fetch wallet using key indexer")
                        resultLiveData.postValue(false)
                        progressVisibleLiveData.postValue(false)
                        return@viewModelIOScope
                    }
                    
                    val publicKey = cryptoProvider.getPublicKey()
                    val chainId = when (chainNetWorkString()) {
                        "mainnet" -> ChainId.Mainnet
                        "testnet" -> ChainId.Testnet
                        else -> ChainId.Mainnet
                    }
                    
                    logd("DeveloperModeViewModel", "Using key indexer to find wallet for public key: $publicKey")
                    
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
                            id = currentAccount.userInfo.username,
                            username = currentAccount.userInfo.username,
                            wallets = listOf(walletData)
                        )
                        
                        AccountManager.updateWalletInfo(walletListData)
                        resultLiveData.postValue(true)
                    } else {
                        logd("DeveloperModeViewModel", "No accounts found in key indexer response")
                        resultLiveData.postValue(false)
                    }
                } catch (e: Exception) {
                    loge(e)
                    resultLiveData.postValue(false)
                }
                progressVisibleLiveData.postValue(false)
            }
        }
    }
}