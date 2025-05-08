package com.flowfoundation.wallet.page.restore.multirestore.viewmodel

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.manager.flowjvm.CadenceArgumentsBuilder
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.addPlatformInfo
import com.flowfoundation.wallet.manager.flowjvm.ufix64Safe
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.RestoreType
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.AccountKeySignature
import com.flowfoundation.wallet.network.model.AccountSignRequest
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreDropboxOption
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreGoogleDriveOption
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreOption
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreOptionModel
import com.flowfoundation.wallet.page.walletrestore.firebaseLogin
import com.flowfoundation.wallet.page.walletrestore.getFirebaseUid
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.error.BackupError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.InvalidKeyException
import com.flowfoundation.wallet.utils.error.WalletError
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.reportCadenceErrorToDebugView
import com.flowfoundation.wallet.utils.setMultiBackupCreated
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.instabug.library.Instabug
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.manager.flowjvm.transaction.sendTransactionWithMultiSignature
import com.flowfoundation.wallet.utils.Env
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.TransactionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.onflow.flow.models.bytesToHex
import java.io.File
import com.flow.wallet.wallet.KeyWallet
import com.flow.wallet.wallet.WalletFactory
import org.onflow.flow.ChainId

class MultiRestoreViewModel : ViewModel(), OnTransactionStateChange {

    val optionChangeLiveData = MutableLiveData<RestoreOptionModel>()

    val googleDriveOptionChangeLiveData = MutableLiveData<RestoreGoogleDriveOption>()
    val dropboxOptionChangeLiveData = MutableLiveData<RestoreDropboxOption>()

    private val optionList = mutableListOf<RestoreOption>()
    private var currentOption = RestoreOption.RESTORE_START
    private var currentIndex = -1
    private var restoreUserName = ""
    private var restoreAddress = ""
    private val mnemonicList = mutableListOf<String>()
    private var currentTxId: String? = null
    private var mnemonicData = ""

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun getRestoreOptionList(): List<RestoreOption> {
        return optionList
    }

    fun changeOption(option: RestoreOption, index: Int) {
        this.currentOption = option
        this.currentIndex = index
        optionChangeLiveData.postValue(RestoreOptionModel(option, index))
    }

    private fun changeGoogleDriveOption(option: RestoreGoogleDriveOption) {
        googleDriveOptionChangeLiveData.postValue(option)
    }

    private fun changeDropboxOption(option: RestoreDropboxOption) {
        dropboxOptionChangeLiveData.postValue(option)
    }

    fun getMnemonicData(): String {
        return mnemonicData
    }

    fun toPinCode(data: String) {
        this.mnemonicData = data
        changeGoogleDriveOption(RestoreGoogleDriveOption.RESTORE_PIN)
    }

    fun toBackupNotFound() {
        if (currentOption == RestoreOption.RESTORE_FROM_GOOGLE_DRIVE) {
            changeGoogleDriveOption(RestoreGoogleDriveOption.RESTORE_ERROR_BACKUP)
        } else if (currentOption == RestoreOption.RESTORE_FROM_DROPBOX) {
            changeDropboxOption(RestoreDropboxOption.RESTORE_ERROR_BACKUP)
        }
    }

    fun toBackupDecryptionFailed() {
        if (currentOption == RestoreOption.RESTORE_FROM_GOOGLE_DRIVE) {
            changeGoogleDriveOption(RestoreGoogleDriveOption.RESTORE_ERROR_PIN)
        } else if (currentOption == RestoreOption.RESTORE_FROM_DROPBOX) {
            changeDropboxOption(RestoreDropboxOption.RESTORE_ERROR_PIN)
        }
    }

    fun toGoogleDrive() {
        changeGoogleDriveOption(RestoreGoogleDriveOption.RESTORE_GOOGLE_DRIVE)
    }

    fun toDropboxPinCode(data: String) {
        this.mnemonicData = data
        changeDropboxOption(RestoreDropboxOption.RESTORE_PIN)
    }

    fun toDropbox() {
        changeDropboxOption(RestoreDropboxOption.RESTORE_DROPBOX)
    }

    fun isRestoreValid(): Boolean {
        return optionList.size >= 2
    }

    fun startRestore() {
        addCompleteOption()
        changeOption(optionList[0], 0)
    }

    private fun restoreFailed() {
        changeOption(RestoreOption.RESTORE_FAILED, -1)
    }

    private fun restoreNoAccount() {
        changeOption(RestoreOption.RESTORE_FAILED_NO_ACCOUNT, -1)
    }

    private fun toNext() {
        ++currentIndex
        changeOption(optionList[currentIndex], currentIndex)
    }

    private fun addCompleteOption() {
        if (optionList.lastOrNull() != RestoreOption.RESTORE_COMPLETED) {
            optionList.remove(RestoreOption.RESTORE_COMPLETED)
            optionList.add(RestoreOption.RESTORE_COMPLETED)
        }
    }

    fun selectOption(option: RestoreOption, callback: (isSelected: Boolean) -> Unit) {
        if (optionList.contains(option)) {
            optionList.remove(option)
            callback.invoke(false)
        } else {
            optionList.add(option)
            callback.invoke(true)
        }
    }

    fun addMnemonicToTransaction(mnemonic: String) {
        mnemonicList.add(currentIndex, mnemonic)
        toNext()
    }

    fun restoreWallet() {
        if (WalletManager.wallet()?.walletAddress() == restoreAddress) {
            toast(msgRes = R.string.wallet_already_logged_in, duration = Toast.LENGTH_LONG)
            val activity = BaseActivity.getCurrentActivity() ?: return
            activity.finish()
            return
        }
        val account = AccountManager.list().firstOrNull { it.wallet?.walletAddress() == restoreAddress }
        if (account != null) {
            AccountManager.switch(account){}
            return
        }
        ioScope {
            try {
                val baseDir = File(Env.getApp().filesDir, "wallet")
                val privateKey = PrivateKey.create(FileSystemStorage(baseDir))
                val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.executeTransactionWithMultiKey {
                    arg { string(privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.let { String(it) } ?: "") }
                    arg { uint8(SigningAlgorithm.ECDSA_P256.ordinal) }
                    arg { uint8(HashingAlgorithm.SHA2_256.ordinal) }
                    arg { ufix64Safe(1000) }
                }
                val transactionState = TransactionState(
                    transactionId = txId!!,
                    time = System.currentTimeMillis(),
                    state = TransactionStatus.PENDING.ordinal,
                    type = TransactionState.TYPE_ADD_PUBLIC_KEY,
                    data = ""
                )
                currentTxId = txId
                TransactionStateManager.newTransaction(transactionState)
                pushBubbleStack(transactionState)
            } catch (e: Exception) {
                loge(e)
                if (e is IllegalStateException) {
                    ErrorReporter.reportCriticalWithMixpanel(WalletError.KEY_STORE_FAILED, e)
                } else {
                    ErrorReporter.reportWithMixpanel(BackupError.MULTI_RESTORE_FAILED, e)
                }
                if (e is NoSuchElementException) {
                    restoreNoAccount()
                } else {
                    restoreFailed()
                }
            }
        }
    }

    private fun syncAccountInfo() {
        ioScope {
            try {
                val baseDir = File(Env.getApp().filesDir, "wallet")
                val privateKey = PrivateKey.create(FileSystemStorage(baseDir))
                val service = retrofit().create(ApiService::class.java)
                val providers = mnemonicList.map {
                    val words = it.split(" ")
                    val seedPhraseKey = SeedPhraseKey(
                        mnemonicString = it,
                        passphrase = "",
                        derivationPath = "m/44'/539'/0'/0/0",
                        keyPair = null,
                        storage = FileSystemStorage(baseDir)
                    )
                    if (words.size == 15) {
                        BackupCryptoProvider(seedPhraseKey)
                    } else {
                        HDWalletCryptoProvider(seedPhraseKey)
                    }
                }
                val resp = service.signAccount(
                    AccountSignRequest(
                        AccountKey(publicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.let { String(it) } ?: ""),
                        providers.map {
                            val jwt = getFirebaseJwt()
                            AccountKeySignature(
                                publicKey = it.getPublicKey(),
                                signMessage = jwt,
                                signature = it.getUserSignature(jwt),
                                weight = it.getKeyWeight(),
                                hashAlgo = it.getHashAlgorithm().ordinal,
                                signAlgo = it.getSignatureAlgorithm().ordinal
                            )
                        }.toList()
                    )
                )
                if (resp.status == 200) {
                    val activity = BaseActivity.getCurrentActivity() ?: return@ioScope
                    login(privateKey) { isSuccess ->
                        uiScope {
                            if (isSuccess) {
                                MixpanelManager.accountRestore(restoreAddress, RestoreType.MULTI_BACKUP)
                                delay(200)
                                MainActivity.relaunch(activity, clearTop = true)
                            } else {
                                toast(msgRes = R.string.login_failure)
                                activity.finish()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, e)
                loge(e)
            }
        }
    }

    private fun login(privateKey: PrivateKey, callback: (isSuccess: Boolean) -> Unit) {
        ioScope {
            getFirebaseUid { uid ->
                if (uid.isNullOrBlank()) {
                    callback.invoke(false)
                    return@getFirebaseUid
                }
                runBlocking {
                    val catching = runCatching {
                        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                        val service = retrofit().create(ApiService::class.java)
                        val jwt = getFirebaseJwt()
                        val resp = service.login(
                            LoginRequest(
                                signature = privateKey.sign(jwt.encodeToByteArray(), SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256).bytesToHex(),
                                accountKey = AccountKey(
                                    publicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.let { String(it) } ?: "",
                                    hashAlgo = HashingAlgorithm.SHA3_256.ordinal,
                                    signAlgo = SigningAlgorithm.ECDSA_P256.ordinal
                                ),
                                deviceInfo = deviceInfoRequest
                            )
                        )
                        if (resp.data?.customToken.isNullOrBlank()) {
                            callback.invoke(false)
                        } else {
                            firebaseLogin(resp.data?.customToken!!) { isSuccess ->
                                if (isSuccess) {
                                    setRegistered()
                                    setMultiBackupCreated()
                                    ioScope {
                                        AccountManager.add(
                                            Account(
                                                userInfo = service.userInfo().data,
                                                prefix = privateKey.id
                                            ),
                                            firebaseUid()
                                        )
                                        clearUserCache()
                                        callback.invoke(true)
                                    }
                                } else {
                                    callback.invoke(false)
                                }
                            }
                        }
                    }

                    if (catching.isFailure) {
                        loge(catching.exceptionOrNull())
                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, catching.exceptionOrNull())
                        callback.invoke(false)
                    }
                }
            }
        }
    }

    fun addWalletInfo(userName: String, address: String) {
        restoreUserName = userName
        restoreAddress = address
    }

    private suspend fun CadenceScript.executeTransactionWithMultiKey(arguments: CadenceArgumentsBuilder.() -> Unit): String? {
        val args = CadenceArgumentsBuilder().apply { arguments(this) }
        val baseDir = File(Env.getApp().filesDir, "wallet")
        val providers = mnemonicList.map {
            val words = it.split(" ")
            val seedPhraseKey = SeedPhraseKey(
                mnemonicString = it,
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = FileSystemStorage(baseDir)
            )
            if (words.size == 15) {
                BackupCryptoProvider(seedPhraseKey)
            } else {
                HDWalletCryptoProvider(seedPhraseKey)
            }
        }
        return try {
            sendTransactionWithMultiSignature(providers = providers, builder = {
                args.build().forEach { arg(it) }
                walletAddress(restoreAddress)
                script(this@executeTransactionWithMultiKey.getScript().addPlatformInfo())
            })
        } catch (e: Exception) {
            loge(e)
            reportCadenceErrorToDebugView(scriptId, e)
            if (e is InvalidKeyException) {
                ErrorReporter.reportCriticalWithMixpanel(WalletError.QUERY_ACCOUNT_KEY_FAILED, e)
                Instabug.show()
            }
            null
        }
    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_ADD_PUBLIC_KEY }
        transaction?.let { state ->
            if (currentTxId == state.transactionId && state.isSuccess()) {
                currentTxId = null
                syncAccountInfo()
            }
        }
    }

    private fun createBackupCryptoProvider(seedPhraseKey: SeedPhraseKey): BackupCryptoProvider {
        // Create a proper KeyWallet
        val wallet = WalletFactory.createKeyWallet(
            seedPhraseKey,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            getStorage()
        )
        return BackupCryptoProvider(seedPhraseKey, wallet as KeyWallet)
    }

    private fun restoreFromMnemonic(mnemonic: String) {
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = mnemonic,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = getStorage()
        )
        val backupProvider = createBackupCryptoProvider(seedPhraseKey)
        // ... existing code ...
    }

    private fun restoreFromKeystore(keystore: String) {
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = keystore,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = getStorage()
        )
        val backupProvider = createBackupCryptoProvider(seedPhraseKey)
        // ... existing code ...
    }
}