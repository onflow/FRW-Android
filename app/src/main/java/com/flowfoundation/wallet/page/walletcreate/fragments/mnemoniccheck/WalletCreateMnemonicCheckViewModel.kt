package com.flowfoundation.wallet.page.walletcreate.fragments.mnemoniccheck

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flow.wallet.crypto.BIP39
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wallet.core.jni.Mnemonic

class WalletCreateMnemonicCheckViewModel : ViewModel() {

    val mnemonicQuestionLiveData = MutableLiveData<List<MnemonicQuestionModel>>()

    fun generateMnemonicQuestion() {
        viewModelIOScope(this) {
            val questionList = mutableListOf<MnemonicQuestionModel>()
            val currentWallet = WalletManager.wallet()
                ?: throw IllegalStateException("No wallet available")
            
            val walletAddress = currentWallet.accounts.values.flatten().firstOrNull()?.address
                ?: throw IllegalStateException("No accounts available in wallet")
            
            val cryptoProvider = AccountWalletManager.getHDWalletByUID(walletAddress)
                ?: throw IllegalStateException("Failed to get crypto provider for wallet")
            
            val mnemonics = (cryptoProvider as BackupCryptoProvider).getMnemonic().split(" ").toMutableList()

            questionList.add(generateMnemonicItem(mnemonics, listOf(0, 1, 2)))
            questionList.add(generateMnemonicItem(mnemonics, listOf(3, 4, 5)))
            questionList.add(generateMnemonicItem(mnemonics, listOf(6, 7, 8)))
            questionList.add(generateMnemonicItem(mnemonics, listOf(9, 10, 11)))
            withContext(Dispatchers.Main) {
                mnemonicQuestionLiveData.value = questionList
            }
        }
    }

    private fun generateMnemonicItem(mnemonics: List<String>, indexRange: List<Int>): MnemonicQuestionModel {
        val index = indexRange.shuffled().first()
        val mnemonic = mnemonics[index]
        val suggest = BIP39.suggest(mnemonic.take(1)).split(" ").shuffled()
        return MnemonicQuestionModel(
            index = index,
            mnemonic = mnemonic,
            listOf(mnemonics[index], suggest[0], suggest[1]).shuffled(),
        )
    }
}