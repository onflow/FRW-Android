package com.flowfoundation.wallet.manager.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.reown.android.Core
import com.reown.android.CoreClient
import com.flowfoundation.wallet.page.profile.subpage.wallet.queryStorageInfo
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.safeRun

private const val TAG = "AppLifecycleObserver"

class AppLifecycleObserver : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        onAppToForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        onAppToBackground()
    }

    private fun onAppToForeground() {
        logd(TAG, "onAppToForeground")
        isForeground = true
        queryStorageInfo()
        safeRun {
            CoreClient.Relay.connect { error: Core.Model.Error ->
                loge(TAG, "RelayClient connect error: $error")
            }
        }
    }

    private fun onAppToBackground() {
        isForeground = false
        logd(TAG, "onAppToBackground")
    }

    companion object {

        private var isForeground = false

        fun observe() {
            ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
        }

        fun isForeground() = isForeground
    }
}