package io.outblock.lilico.page.restore.multirestore.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.outblock.lilico.page.restore.multirestore.model.RestoreGoogleDriveOption


class RestoreGoogleDriveWithPinViewModel: ViewModel() {

    val optionChangeLiveData = MutableLiveData<RestoreGoogleDriveOption>()

    fun changeOption(option: RestoreGoogleDriveOption) {
        optionChangeLiveData.postValue(option)
    }

    fun restoreGoogleDrive() {
        changeOption(RestoreGoogleDriveOption.RESTORE_GOOGLE_DRIVE)
    }

    fun backToPinCode() {
        changeOption(RestoreGoogleDriveOption.RESTORE_PIN)
    }
}