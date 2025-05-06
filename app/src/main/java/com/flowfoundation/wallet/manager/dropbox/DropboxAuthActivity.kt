package com.flowfoundation.wallet.manager.dropbox

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.DbxClientV2
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import wallet.core.jni.HDWallet
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import java.io.File

class DropboxAuthActivity : AppCompatActivity() {
    private val isLogin by lazy { intent.getBooleanExtra(EXTRA_LOGIN, false) }
    private val isCheckBackup by lazy { intent.getBooleanExtra(EXTRA_CHECK_BACKUP, false) }
    private val isViewBackup by lazy { intent.getBooleanExtra(EXTRA_VIEW_BACKUP, false) }
    private val isMultiBackup by lazy { intent.getBooleanExtra(EXTRA_MULTI_BACKUP, false) }
    private val isMultiRestoreWithSignOut by lazy {
        intent.getBooleanExtra(EXTRA_MULTI_RESTORE_WITH_SIGN_OUT, false)
    }
    private val mnemonic by lazy { intent.getStringExtra(EXTRA_MNEMONIC) }
    private val clientIdentifier: String = "db-${BuildConfig.DROPBOX_APP_KEY}"

    private var isAwaitingResult = false
    private var dbxClient: DbxClientV2? = null
    private var isCreateActivity = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))
        UltimateBarX.with(this).color(Color.TRANSPARENT).fitWindow(false).light(false).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(false).applyNavigationBar()

        if (isLogin || isMultiRestoreWithSignOut || isViewBackup || Auth.getDbxCredential() == null) {
            performDropboxLogin()
        } else {
            handleAuthResult()
        }
    }

    private fun performDropboxLogin() {
        logd(TAG, "Performing Dropbox login")
        dbxClient?.auth()?.tokenRevoke()
        Auth.startOAuth2PKCE(this, BuildConfig.DROPBOX_APP_KEY, DbxRequestConfig(clientIdentifier))
        isAwaitingResult = true
    }

    override fun onResume() {
        super.onResume()
        if (isCreateActivity) {
            isCreateActivity = false
            return
        }
        if (isAwaitingResult) {
            isAwaitingResult = false
            logw("Dropbox", "auth activity onResume getResult")
            if (Auth.getDbxCredential() != null) {
                handleAuthResult()
            } else if (isLogin) {
                loge(TAG, "Dropbox login failed or cancelled")
                notifyLoginFinished(success = false)
                finish()
            }
        }
    }

    private fun handleAuthResult() {
        logw("Dropbox", "auth activity handle result")
        val credential = Auth.getDbxCredential() ?: return
        logd(TAG, "Dropbox authenticated successfully")
        doAction(DbxClientV2(
            DbxRequestConfig(clientIdentifier),
            credential.accessToken
        ))
    }

    private fun doAction(dbxClient: DbxClientV2) {
        this.dbxClient = dbxClient
        ioScope {
            try {
                when {
                    isViewBackup -> viewFromDropbox(dbxClient)
                    isMultiBackup -> {
                        val baseDir = File(Env.getApp().filesDir, "wallet")
                        val storage = FileSystemStorage(baseDir)
                        val seedPhraseKey = SeedPhraseKey(
                            mnemonicString = mnemonic ?: "",
                            passphrase = "",
                            derivationPath = "m/44'/539'/0'/0/0",
                            keyPair = null,
                            storage = storage
                        )
                        uploadDropboxBackup(dbxClient, BackupCryptoProvider(seedPhraseKey))
                    }
                    isMultiRestoreWithSignOut -> restoreFromDropbox(dbxClient)
                    isCheckBackup -> {
                        val baseDir = File(Env.getApp().filesDir, "wallet")
                        val storage = FileSystemStorage(baseDir)
                        val seedPhraseKey = SeedPhraseKey(
                            mnemonicString = mnemonic ?: "",
                            passphrase = "",
                            derivationPath = "m/44'/539'/0'/0/0",
                            keyPair = null,
                            storage = storage
                        )
                        checkDropboxBackup(dbxClient, BackupCryptoProvider(seedPhraseKey))
                    }
                    else -> notifyLoginFinished(true)
                }
                finish()
            } catch (e: Exception) {
                loge(TAG, "Dropbox action failed: $e")
                finish()
            }
        }
    }

    private fun notifyLoginFinished(success: Boolean) {
        LocalBroadcastManager.getInstance(Env.getApp()).sendBroadcast(
            Intent(ACTION_DROPBOX_LOGIN_FINISH).apply {
                putExtra(EXTRA_SUCCESS, success)
            }
        )
    }

    companion object {
        private val TAG = DropboxAuthActivity::class.java.simpleName
        private const val EXTRA_LOGIN = "extra_login"
        private const val EXTRA_MULTI_BACKUP = "extra_multi_backup"
        private const val EXTRA_MULTI_RESTORE_WITH_SIGN_OUT = "extra_multi_restore_with_sign_out"
        private const val EXTRA_MNEMONIC = "extra_mnemonic"
        private const val EXTRA_VIEW_BACKUP = "extra_view_backup"
        private const val EXTRA_CHECK_BACKUP = "extra_check_backup"
        private const val EXTRA_SUCCESS = "extra_success"

        fun loginDropboxAccount(context: Context) {
            context.startActivity(Intent(context, DropboxAuthActivity::class.java).apply {
                putExtra(EXTRA_LOGIN, true)
            })
        }

        fun checkMultiBackup(context: Context, mnemonic: String) {
            context.startActivity(Intent(context, DropboxAuthActivity::class.java).apply {
                putExtra(EXTRA_CHECK_BACKUP, true)
                putExtra(EXTRA_MNEMONIC, mnemonic)
            })
        }

        fun viewMnemonic(context: Context) {
            context.startActivity(Intent(context, DropboxAuthActivity::class.java).apply {
                putExtra(EXTRA_VIEW_BACKUP, true)
            })
        }

        fun multiBackupMnemonic(context: Context, mnemonic: String) {
            context.startActivity(Intent(context, DropboxAuthActivity::class.java).apply {
                putExtra(EXTRA_MULTI_BACKUP, true)
                putExtra(EXTRA_MNEMONIC, mnemonic)
            })
        }

        fun multiRestoreMnemonicWithSignOut(context: Context) {
            context.startActivity(Intent(context, DropboxAuthActivity::class.java).apply {
                putExtra(EXTRA_MULTI_RESTORE_WITH_SIGN_OUT, true)
            })
        }
    }
}