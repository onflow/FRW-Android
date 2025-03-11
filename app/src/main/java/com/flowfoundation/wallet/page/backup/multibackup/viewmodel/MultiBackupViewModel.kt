package com.flowfoundation.wallet.page.backup.multibackup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.LocationInfo
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.backup.BackupListManager
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupCompletedItem
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOptionModel
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge


class MultiBackupViewModel : ViewModel() {

    val optionChangeLiveData = MutableLiveData<BackupOptionModel>()
    val locationInfoLiveData = MutableLiveData<LocationInfo?>()

    private val optionList = mutableListOf<BackupOption>()
    private val completedList = mutableListOf<BackupCompletedItem>()
    private var currentOption = BackupOption.BACKUP_START
    private var currentIndex = -1

    fun getLocationInfo() {
        ioScope {
            try {
                val service = retrofit().create(ApiService::class.java)
                val response = service.getDeviceLocation()
                locationInfoLiveData.postValue(response.data)
            } catch (e: Exception) {
                loge(e)
                locationInfoLiveData.postValue(null)
            }
        }
    }

    fun getCompletedList(): List<BackupCompletedItem> {
        return completedList
    }

    fun getBackupOptionList(): List<BackupOption> {
        return optionList
    }

    fun getCurrentIndex(): Int {
        return currentIndex
    }

    fun changeOption(option: BackupOption, index: Int) {
        this.currentOption = option
        this.currentIndex = index
        optionChangeLiveData.postValue(BackupOptionModel(option, index))
    }

    fun isBackupValid(): Boolean {
        return optionList.size + BackupListManager.backupCount() >= 2
    }

    fun startBackup() {
        addCompleteOption()
        completedList.clear()
        changeOption(optionList[0], 0)
    }

    fun toNext(item: BackupCompletedItem) {
        ++currentIndex
        addCompletedItem(item)
        changeOption(optionList[currentIndex], currentIndex)
    }

    private fun addCompletedItem(item: BackupCompletedItem) {
        val index = completedList.indexOfFirst { it.type == item.type }
        if (index == -1) {
            completedList.add(item)
        } else {
            completedList.removeAt(index)
            completedList.add(index, item)
        }
    }

    private fun addCompleteOption() {
        if (optionList.lastOrNull() != BackupOption.BACKUP_COMPLETED) {
            optionList.remove(BackupOption.BACKUP_COMPLETED)
            optionList.add(BackupOption.BACKUP_COMPLETED)
        }
    }

    fun selectOption(option: BackupOption, callback: (isSelected: Boolean) -> Unit) {
        if (optionList.contains(option)) {
            optionList.remove(option)
            callback.invoke(false)
        } else {
            if (option == BackupOption.BACKUP_WITH_GOOGLE_DRIVE) {
                optionList.add(0, option)
            } else {
                optionList.add(option)
            }
            callback.invoke(true)
        }
    }
}