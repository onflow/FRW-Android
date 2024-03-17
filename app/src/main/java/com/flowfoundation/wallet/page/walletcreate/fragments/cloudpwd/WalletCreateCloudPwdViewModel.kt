package com.flowfoundation.wallet.page.walletcreate.fragments.cloudpwd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowfoundation.wallet.manager.drive.ACTION_GOOGLE_DRIVE_UPLOAD_FINISH
import com.flowfoundation.wallet.manager.drive.EXTRA_SUCCESS
import com.flowfoundation.wallet.manager.drive.GoogleDriveAuthActivity
import com.flowfoundation.wallet.utils.Env

class WalletCreateCloudPwdViewModel : ViewModel() {

    val backupCallbackLiveData = MutableLiveData<Boolean>()

    private val uploadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val isSuccess = intent?.getBooleanExtra(EXTRA_SUCCESS, false) ?: return
            backupCallbackLiveData.postValue(isSuccess)
        }
    }

    init {
        LocalBroadcastManager.getInstance(Env.getApp()).registerReceiver(uploadReceiver, IntentFilter(ACTION_GOOGLE_DRIVE_UPLOAD_FINISH))
    }


    fun backup(context: Context, pwd: String) {
        GoogleDriveAuthActivity.uploadMnemonic(context, pwd)
    }

    override fun onCleared() {
        LocalBroadcastManager.getInstance(Env.getApp()).unregisterReceiver(uploadReceiver)
        super.onCleared()
    }
}