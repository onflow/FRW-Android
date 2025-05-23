package com.flowfoundation.wallet.page.splash

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.flowfoundation.wallet.manager.account.isAccountV1DataExist
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.others.AccountMigrateActivity
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope

@SuppressLint("CustomSplashScreen")
open class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        logd("startup", "SplashActivity onCreate")
        uiScope {
            logd("startup", isAccountV1DataExist())
            if (isAccountV1DataExist()) {
                logd("startup", "Account data exists")
                AccountMigrateActivity.launch(this)
            } else {
                logd("startup", "Launching main activity")
                MainActivity.launch(this)
            }
            overridePendingTransition(0, 0)
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
        overridePendingTransition(0, 0)
    }
}