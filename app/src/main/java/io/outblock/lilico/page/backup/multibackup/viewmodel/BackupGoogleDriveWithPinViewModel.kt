package io.outblock.lilico.page.backup.multibackup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.outblock.lilico.page.backup.multibackup.model.BackupGoogleDriveOption


class BackupGoogleDriveWithPinViewModel : ViewModel() {

    val optionChangeLiveData = MutableLiveData<BackupGoogleDriveOption>()
    val backupFinishLiveData = MutableLiveData<Boolean>()
    private var currentIndex = -1

    fun setCurrentIndex(index: Int) {
        currentIndex = index
    }

    fun getCurrentIndex(): Int {
        return currentIndex
    }

    fun changeOption(option: BackupGoogleDriveOption) {
        optionChangeLiveData.postValue(option)
    }

    fun startBackup() {
        changeOption(BackupGoogleDriveOption.BACKUP_GOOGLE_DRIVE)
    }

    fun backToPinCode() {
        changeOption(BackupGoogleDriveOption.BACKUP_PIN)
    }

    fun backupFinish() {
        backupFinishLiveData.postValue(true)
    }
}