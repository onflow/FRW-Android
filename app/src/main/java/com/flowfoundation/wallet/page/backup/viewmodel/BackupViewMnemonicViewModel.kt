package com.flowfoundation.wallet.page.backup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.backup.model.BackupViewMnemonicModel
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicModel
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class BackupViewMnemonicViewModel: ViewModel() {

    private var mnemonicData = ""
    private var mnemonic = ""
    val viewMnemonicLiveData = MutableLiveData<BackupViewMnemonicModel>()
    val mnemonicListLiveData = MutableLiveData<List<MnemonicModel>>()

    fun getMnemonicData(): String {
        return mnemonicData
    }

    fun loadMnemonic() {
        ioScope {
            val str = mnemonic
            withContext(Dispatchers.Main) {
                val list = str.split(" ").mapIndexed { index, s -> MnemonicModel(index + 1, s) }
                val result = mutableListOf<MnemonicModel>()
                val mid = list.size / 2 + 1
                (0 until mid).forEach { i ->
                    result.add(list[i])
                    val j = i + mid
                    if (j < list.size) {
                        result.add(list[j])
                    }
                }
                mnemonicListLiveData.value = result
            }
        }
    }

    fun copyMnemonic() {
        textToClipboard(mnemonic)
        toast(R.string.copied_to_clipboard)
    }

    fun showRecoveryPhrase(mnemonic: String) {
        this.mnemonic = mnemonic
        viewMnemonicLiveData.postValue(BackupViewMnemonicModel.BACKUP_DETAIL_RECOVERY_PHRASE)

    }

    fun toPinCode(data: String) {
        this.mnemonicData = data
        viewMnemonicLiveData.postValue(BackupViewMnemonicModel.BACKUP_DETAIL_PIN)
    }

    fun toBackupNotFound() {
        viewMnemonicLiveData.postValue(BackupViewMnemonicModel.BACKUP_DETAIL_ERROR_BACKUP)
    }

    fun toBackupDecryptionFailed() {
        viewMnemonicLiveData.postValue(BackupViewMnemonicModel.BACKUP_DETAIL_ERROR_PIN)
    }
}