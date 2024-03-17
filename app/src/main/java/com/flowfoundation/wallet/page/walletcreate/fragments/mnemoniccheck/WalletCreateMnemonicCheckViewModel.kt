package com.flowfoundation.wallet.page.walletcreate.fragments.mnemoniccheck

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wallet.core.jni.Mnemonic

class WalletCreateMnemonicCheckViewModel : ViewModel() {

    val mnemonicQuestionLiveData = MutableLiveData<List<MnemonicQuestionModel>>()

    fun generateMnemonicQuestion() {
        viewModelIOScope(this) {
            val questionList = mutableListOf<MnemonicQuestionModel>()
            val mnemonics = Wallet.store().mnemonic().split(" ").toMutableList()

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
        val suggest = Mnemonic.suggest(mnemonic.take(1)).split(" ").shuffled()
        return MnemonicQuestionModel(
            index = index,
            mnemonic = mnemonic,
            listOf(mnemonics[index], suggest[0], suggest[1]).shuffled(),
        )
    }
}