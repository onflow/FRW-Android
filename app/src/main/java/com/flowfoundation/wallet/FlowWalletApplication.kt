package com.flowfoundation.wallet

import android.app.Application
import com.flowfoundation.wallet.crowdin.crowdinInitialize
import com.flowfoundation.wallet.manager.LaunchManager
import com.flowfoundation.wallet.utils.Env

class FlowWalletApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Env.init(this)
        crowdinInitialize(this)
        LaunchManager.init(this)
    }
}
