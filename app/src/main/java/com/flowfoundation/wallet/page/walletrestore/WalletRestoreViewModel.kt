package com.flowfoundation.wallet.page.walletrestore

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WalletRestoreViewModel : ViewModel() {

    val onStepChangeLiveData = MutableLiveData<Pair<Int, Any?>>()

    private var step = WALLET_RESTORE_STEP_GUIDE

    init {
        changeStep(step)
    }

    fun changeStep(step: Int, arguments: Any? = null) {
        this.step = step
        onStepChangeLiveData.postValue(Pair(step, arguments))
    }
}