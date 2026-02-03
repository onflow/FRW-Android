package com.flowfoundation.wallet.page.restore.mnemonic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.page.restore.mnemonic.presenter.RestoreMnemonicScreen
import com.flowfoundation.wallet.utils.isNightMode
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX

class RestoreMnemonicActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background)
            .light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()

        setContent {
            RestoreMnemonicScreen(
                onClose = { finish() },
                onRestoreSuccess = {
                    setResult(RESULT_OK)
                    finish()
                }
            )
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, RestoreMnemonicActivity::class.java)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}