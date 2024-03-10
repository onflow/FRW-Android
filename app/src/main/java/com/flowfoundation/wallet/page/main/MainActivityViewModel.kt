package com.flowfoundation.wallet.page.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel() {

    val walletRegisterSuccessLiveData = MutableLiveData<Boolean>()

    internal val changeTabLiveData = MutableLiveData<HomeTab>()
    internal val openDrawerLayoutLiveData = MutableLiveData<Boolean>()

    fun changeTab(tab: HomeTab) {
        changeTabLiveData.postValue(tab)
    }

    fun openDrawerLayout() {
        openDrawerLayoutLiveData.postValue(true)
    }
}