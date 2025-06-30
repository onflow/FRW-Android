package com.flowfoundation.wallet.page.walletcreate.fragments.warning

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flow.wallet.crypto.BIP39
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.utils.Env.getStorage
import org.onflow.flow.ChainId

class WalletCreateWarningViewModel : ViewModel() {

    val registerCallbackLiveData = MutableLiveData<Boolean>()

    fun register() {
//        viewModelIOScope(this) {
//            try {
//                registerOutblockUser(username().lowercase(Locale.getDefault())) { isSuccess ->
//                    withContext(Dispatchers.Main) {
//                        registerCallbackLiveData.postValue(isSuccess)
//                        if (isSuccess) {
//                            uploadPushToken()
//                            createWalletFromServer()
//                            // Initialize WalletManager with the new wallet
//                            WalletManager.init()
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    registerCallbackLiveData.postValue(false)
//                }
//            }
//        }
    }

    private fun createWalletFromServer() {
        // Generate a new mnemonic using BIP39
        val mnemonic = BIP39.generate(BIP39.SeedPhraseLength.TWELVE)
        
        // Create a seed phrase key
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = mnemonic,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = getStorage()
        )
        
        // Create a new wallet using the seed phrase
        WalletFactory.createKeyWallet(
            seedPhraseKey,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            getStorage()
        )
    }
}