package com.flowfoundation.wallet.page.backup.multibackup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupStartOption


class BackupStartWithAboutViewModel: ViewModel() {
    val optionChangeLiveData = MutableLiveData<BackupStartOption>()

    fun changeOption(option: BackupStartOption) {
        optionChangeLiveData.postValue(option)
    }
}