package com.flowfoundation.wallet.page.walletcreate

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WalletCreateViewModel : ViewModel() {

    val onStepChangeLiveData = MutableLiveData<Int>()

    private var step = WALLET_CREATE_STEP_LEGAL

    fun nextStep() {
        step++
        onStepChangeLiveData.postValue(step)
    }

    fun changeStep(step: Int) {
        this.step = step
        onStepChangeLiveData.postValue(step)
    }

    fun handleBackPressed():Boolean{
        if(step == WALLET_CREATE_STEP_MNEMONIC_CHECK || step == WALLET_CREATE_STEP_CLOUD_PWD){
            changeStep(WALLET_CREATE_STEP_MNEMONIC)
            return true
        }
        return false
    }
}