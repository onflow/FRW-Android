package com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flow.wallet.crypto.BIP39
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
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
        }
    }

    fun getMnemonic(): String {
        return currentMnemonic ?: throw IllegalStateException("Mnemonic not generated yet")
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun createWallet() {
        viewModelIOScope(this) {
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
            
            // Create keystore info for the account
            val privateKeyHex = seedPhraseKey.privateKey(org.onflow.flow.models.SigningAlgorithm.ECDSA_P256)?.toHexString() ?: ""
            val publicKeyHex = seedPhraseKey.publicKey(org.onflow.flow.models.SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04") ?: ""
            val walletAddress = wallet.walletAddress() ?: ""
            
            // Create keystore address info
            val keystoreAddress = com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress(
                address = walletAddress,
                publicKey = publicKeyHex,
                privateKey = privateKeyHex,
                keyId = 0,
                weight = 1000,
                hashAlgo = org.onflow.flow.models.HashingAlgorithm.SHA2_256.cadenceIndex,
                signAlgo = org.onflow.flow.models.SigningAlgorithm.ECDSA_P256.cadenceIndex
            )
            
            // Create user info for local wallet
            val userInfo = com.flowfoundation.wallet.network.model.UserInfoData(
                nickname = "Local User",
                username = "local_${System.currentTimeMillis()}",
                avatar = "",
                address = walletAddress,
                isPrivate = 0,
                created = java.time.Instant.now().toString()
            )
            
            // Create and add account to AccountManager with keystore info
            val account = com.flowfoundation.wallet.manager.account.Account(
                userInfo = userInfo,
                keyStoreInfo = com.google.gson.Gson().toJson(keystoreAddress)
            )
            
            com.flowfoundation.wallet.manager.account.AccountManager.add(account)
            
            // Mark as registered immediately after wallet creation (like server registration)
            // This allows users to proceed even if they exit backup early
            com.flowfoundation.wallet.utils.setRegistered()
        }
    }
}