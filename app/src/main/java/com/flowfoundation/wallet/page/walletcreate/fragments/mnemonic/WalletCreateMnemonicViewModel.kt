package com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.wallet.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WalletCreateMnemonicViewModel : ViewModel() {

    val mnemonicList = MutableLiveData<List<MnemonicModel>>()

    fun loadMnemonic() {
        viewModelIOScope(this) {
            val str = Wallet.store().mnemonic()
            withContext(Dispatchers.Main) {
                val list = str.split(" ").mapIndexed { index, s -> MnemonicModel(index + 1, s) }
                val result = mutableListOf<MnemonicModel>()
                (0 until list.size / 2).forEach { i ->
                    result.add(list[i])
                    result.add(list[i + list.size / 2])
                }
                mnemonicList.value = result
            }

            logd("Mnemonic", str)
        }
    }
}