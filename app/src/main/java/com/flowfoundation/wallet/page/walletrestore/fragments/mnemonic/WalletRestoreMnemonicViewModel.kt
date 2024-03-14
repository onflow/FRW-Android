package com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.utils.extensions.indexOfAll
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.safeRun
import wallet.core.jni.Mnemonic
import kotlin.math.max

class WalletRestoreMnemonicViewModel : ViewModel() {

    val mnemonicSuggestListLiveData = MutableLiveData<List<String>>()

    val selectSuggestLiveData = MutableLiveData<String>()

    val invalidWordListLiveData = MutableLiveData<List<Pair<Int, String>>>()

    private var text: String = ""

    fun suggestMnemonic(text: String) {
        if (this.text == text) {
            return
        }
        val prefix = text.split(" ").last()
        if (prefix.isBlank()) {
            return
        }

        safeRun {
            val list = Mnemonic.suggest(prefix).split(" ")

            if (list.size == 1 && list.first() == prefix) {
                mnemonicSuggestListLiveData.postValue(emptyList())
            } else {
                mnemonicSuggestListLiveData.postValue(list)
            }
        }
    }

    fun invalidMnemonicCheck(text: String) {
        if (this.text == text) {
            return
        }
        this.text = text
        val words = text.split(" ")
        val invalidWords = words.filter { it.isNotBlank() && !Mnemonic.isValidWord(it) }
        if (invalidWords.isEmpty()) {
            invalidWordListLiveData.postValue(emptyList())
            return
        }

        val result = mutableListOf<Pair<Int, String>>()
        invalidWords.forEach { word ->
            text.indexOfAll(word).forEach { index ->
                logd("invalidMnemonicCheck", "index:$index, word:$word")
                val isOneWord = word.length == text.length
                val isStartWord = index == 0 && (index + word.length == text.length || text[index + word.length] == ' ')
                val isEndWord = !isStartWord && index + word.length == text.length && text[index - 1] == ' '
                val isSpaceAround = !isStartWord && !isEndWord && text[max(0, index - 1)] == ' ' && text[index + word.length] == ' '
                if (isOneWord ||
                    isStartWord ||
                    isEndWord ||
                    isSpaceAround
                ) {
                    result.add(Pair(index, word))
                }
            }
        }

        logd("invalidMnemonicCheck", "result:$result")

        invalidWordListLiveData.postValue(result)
    }

    fun selectSuggest(word: String) {
        selectSuggestLiveData.postValue(word)
    }
}