package io.outblock.lilico.manager.drive

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import io.outblock.lilico.utils.Env
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.logd
import java.util.*


class GoogleDriveAuthActivity : AppCompatActivity() {

    private val password by lazy { intent.getStringExtra(EXTRA_PASSWORD)!! }
    private val isRestore by lazy { intent.getBooleanExtra(EXTRA_RESTORE, false) }
    private val isDeleteBackup by lazy { intent.getBooleanExtra(EXTRA_DELETE_BACKUP, false) }

    private var mClient: GoogleSignInClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))
        UltimateBarX.with(this).color(Color.TRANSPARENT).fitWindow(false).light(false).applyStatusBar()

        val signInOptions = GoogleSignInOptions.Builder()
            .requestEmail()
//            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        mClient = GoogleSignIn.getClient(this, signInOptions)

        mClient?.let {
            logd(TAG, "startActivityForResult")
            // The result of the sign-in Intent is handled in onActivityResult.
            startActivityForResult(it.signInIntent, REQUEST_CODE_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        logd(TAG, "onActivityResult")
        if (resultCode != RESULT_OK) {
            finish()
            return
        }

        if (requestCode == REQUEST_CODE_SIGN_IN) {
            handleSignInResult(data)
        }
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun handleSignInResult(data: Intent?) {
        data ?: return
        logd(TAG, "handleSignInResult")
        GoogleSignIn.getSignedInAccountFromIntent(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val account = task.result
                logd(TAG, "Signed in as " + account.email)
                // Use the authenticated account to sign in to the Drive service.
                val credential: GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(
                    this, Collections.singleton(DriveScopes.DRIVE_APPDATA)
                )
                credential.selectedAccount = account.account
                val googleDriveService: Drive = Drive.Builder(
                    NetHttpTransport(),
                    JacksonFactory.getDefaultInstance(),
                    setHttpTimeout(credential),
                ).setApplicationName("Drive API Migration").build()

                doAction(googleDriveService)
            } else {
                logd(TAG, "signIn fail ${task.exception}")
                signOutAndSignInAgain()
            }
        }
    }

    private fun signOutAndSignInAgain(){
        mClient?.let {
            it.signOut().addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    logd(TAG, "startActivityForResult")
                    // The result of the sign-in Intent is handled in onActivityResult.
                    startActivityForResult(it.signInIntent, REQUEST_CODE_SIGN_IN)
                } else {
                    LocalBroadcastManager.getInstance(Env.getApp()).sendBroadcast(Intent(ACTION_GOOGLE_DRIVE_RESTORE_FINISH).apply {
                        putParcelableArrayListExtra(EXTRA_CONTENT, arrayListOf())
                    })
                }
            }
        }
    }

    private fun doAction(googleDriveService: Drive) {
        ioScope {
            try {
                when {
                    isDeleteBackup -> deleteMnemonicFromGoogleDrive(googleDriveService)
                    isRestore -> restoreMnemonicFromGoogleDrive(googleDriveService)
                    else -> uploadMnemonicToGoogleDrive(googleDriveService, password)
                }
                finish()
            } catch (authIOException: UserRecoverableAuthIOException) {
                signOutAndSignInAgain()
            } catch (e: Exception) {
            }
        }
    }

    private fun setHttpTimeout(requestInitializer: HttpRequestInitializer): HttpRequestInitializer {
        return HttpRequestInitializer { httpRequest ->
            requestInitializer.initialize(httpRequest)
            httpRequest.connectTimeout = 3 * 60000
            httpRequest.readTimeout = 3 * 60000
        }
    }

    companion object {
        private val TAG = GoogleDriveAuthActivity::class.java.simpleName
        private const val REQUEST_CODE_SIGN_IN = 1
        private const val EXTRA_PASSWORD = "extra_password"

        private const val EXTRA_RESTORE = "extra_restore"

        private const val EXTRA_DELETE_BACKUP = "extra_delete_backup"

        fun uploadMnemonic(context: Context, password: String) {
            context.startActivity(Intent(context, GoogleDriveAuthActivity::class.java).apply {
                putExtra(EXTRA_PASSWORD, password)
            })
        }

        fun restoreMnemonic(context: Context) {
            context.startActivity(Intent(context, GoogleDriveAuthActivity::class.java).apply {
                putExtra(EXTRA_RESTORE, true)
            })
        }

        fun deleteBackup(context: Context) {
            context.startActivity(Intent(context, GoogleDriveAuthActivity::class.java).apply {
                putExtra(EXTRA_DELETE_BACKUP, true)
            })
        }
    }
}