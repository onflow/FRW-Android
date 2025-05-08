package com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flow.wallet.crypto.BIP39
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.Env.getStorage
import org.onflow.flow.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WalletCreateMnemonicViewModel : ViewModel() {

    val mnemonicList = MutableLiveData<List<MnemonicModel>>()
    private var currentMnemonic: String? = null

    fun loadMnemonic() {
        viewModelIOScope(this) {
            // Generate a new mnemonic using BIP39
            val mnemonic = BIP39.generate(BIP39.SeedPhraseLength.TWELVE)
            currentMnemonic = mnemonic
            
            withContext(Dispatchers.Main) {
                val list = mnemonic.split(" ").mapIndexed { index, s -> MnemonicModel(index + 1, s) }
                val result = mutableListOf<MnemonicModel>()
                (0 until list.size / 2).forEach { i ->
                    result.add(list[i])
                    result.add(list[i + list.size / 2])
                }
                mnemonicList.value = result
            }

            logd("Mnemonic", mnemonic)
        }
    }

    fun getMnemonic(): String {
        return currentMnemonic ?: throw IllegalStateException("Mnemonic not generated yet")
    }

    fun createWallet() {
        val mnemonic = getMnemonic()
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = mnemonic,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = getStorage()
        )
        
        // Create a new wallet using the seed phrase
        val wallet = WalletFactory.createKeyWallet(
            seedPhraseKey,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            getStorage()
        )
        
        // Initialize WalletManager with the new wallet
        WalletManager.init()
    }
}