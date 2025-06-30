package com.flowfoundation.wallet.page.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel() {

    internal val changeTabLiveData = MutableLiveData<HomeTab>()
    internal val openDrawerLayoutLiveData = MutableLiveData<Boolean>()
    
    fun changeTab(tab: HomeTab) {
        changeTabLiveData.postValue(tab)
    }

    fun openDrawerLayout() {
        openDrawerLayoutLiveData.postValue(true)
    }
    
    companion object {
        private const val TAG = "MainActivityViewModel"
    }
}