package com.flowfoundation.wallet.page.backup.multibackup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupDropboxOption


class BackupDropboxWithPinViewModel : ViewModel() {

    val optionChangeLiveData = MutableLiveData<BackupDropboxOption>()
    val backupFinishLiveData = MutableLiveData<String>()
    private var currentIndex = -1

    fun setCurrentIndex(index: Int) {
        currentIndex = index
    }

    fun changeOption(option: BackupDropboxOption) {
        optionChangeLiveData.postValue(option)
    }

    fun startBackup() {
        changeOption(BackupDropboxOption.BACKUP_DROPBOX)
    }

    fun backToPinCode() {
        changeOption(BackupDropboxOption.BACKUP_PIN)
    }

    fun backupFinish(mnemonic: String) {
        backupFinishLiveData.postValue(mnemonic)
    }
}