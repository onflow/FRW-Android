package com.flowfoundation.wallet.page.restore.multirestore.viewmodel

import android.content.Intent
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
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.RestoreType
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.generatePrefix
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
import java.io.File
import com.flow.wallet.wallet.KeyWallet
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.ChainId
import org.onflow.flow.infrastructure.Cadence.Companion.uint8
import org.onflow.flow.models.DomainTag
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.HashMap
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.utils.storeWalletPassword

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
        logd("MultiRestore", "MultiRestoreViewModel init - registering transaction state listener")
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

    @OptIn(ExperimentalStdlibApi::class)
    fun restoreWallet() {
        if (WalletManager.wallet()?.walletAddress() == restoreAddress) {
            logd("MultiRestore", "Wallet already logged in for address: $restoreAddress")
            toast(msgRes = R.string.wallet_already_logged_in, duration = Toast.LENGTH_LONG)
            val activity = BaseActivity.getCurrentActivity()
            logd("MultiRestore", "Current activity: ${activity?.javaClass?.simpleName}")
            if (activity == null) {
                logd("MultiRestore", "ERROR: Current activity is null, cannot navigate to dashboard")
                return
            }
            uiScope {
                logd("MultiRestore", "Navigating to wallet dashboard after 1 second delay")
                delay(1000)
                try {
                    MainActivity.relaunch(activity, clearTop = true)
                    logd("MultiRestore", "Successfully called MainActivity.relaunch()")
                } catch (e: Exception) {
                    logd("MultiRestore", "ERROR in MainActivity.relaunch(): ${e.message}")
                    android.util.Log.e("MultiRestore", "MainActivity.relaunch failed", e)
                    // Fallback: try to finish current activity and start MainActivity directly
                    try {
                        val intent = Intent(activity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        activity.startActivity(intent)
                        activity.finish()
                        logd("MultiRestore", "Fallback navigation successful")
                    } catch (fallbackException: Exception) {
                        logd("MultiRestore", "Fallback navigation also failed: ${fallbackException.message}")
                        android.util.Log.e("MultiRestore", "Fallback navigation failed", fallbackException)
                    }
                }
            }
            return
        }
        val account = AccountManager.list().firstOrNull { it.wallet?.walletAddress() == restoreAddress }
        if (account != null) {
            logd("MultiRestore", "Found existing account: ${account.userInfo.username} for address: $restoreAddress")
            val activity = BaseActivity.getCurrentActivity()
            logd("MultiRestore", "Current activity for account switch: ${activity?.javaClass?.simpleName}")
            AccountManager.switch(account) {
                uiScope {
                    activity?.let { act ->
                        logd("MultiRestore", "Account switch successful, navigating to dashboard")
                        delay(500)
                        try {
                            MainActivity.relaunch(act, clearTop = true)
                            logd("MultiRestore", "Successfully called MainActivity.relaunch() after account switch")
                        } catch (e: Exception) {
                            logd("MultiRestore", "ERROR in MainActivity.relaunch() after account switch: ${e.message}")
                            android.util.Log.e("MultiRestore", "MainActivity.relaunch failed after account switch", e)
                            // Fallback: try to finish current activity and start MainActivity directly
                            try {
                                val intent = Intent(act, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                act.startActivity(intent)
                                act.finish()
                                logd("MultiRestore", "Fallback navigation successful after account switch")
                            } catch (fallbackException: Exception) {
                                logd("MultiRestore", "Fallback navigation also failed after account switch: ${fallbackException.message}")
                                android.util.Log.e("MultiRestore", "Fallback navigation failed after account switch", fallbackException)
                            }
                        }
                    } ?: logd("MultiRestore", "ERROR: Activity is null after account switch")
                }
            }
            return
        }
        ioScope {
            try {
                logd("MultiRestore", "Starting restoreWallet with ${mnemonicList.size} mnemonics")
                val baseDir = File(Env.getApp().filesDir, "wallet")
                val storage = FileSystemStorage(baseDir)
                
                // Create backup crypto providers from mnemonics for transaction authorization
                val providers = mnemonicList.map { mnemonic ->
                    val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, storage)
                    val words = mnemonic.split(" ")
                    if (words.size == 15) {
                        BackupCryptoProvider(seedPhraseKey)
                    } else {
                        HDWalletCryptoProvider(seedPhraseKey)
                    }
                }
                
                // Create NEW key for wallet registration (not from backup)
                val newPrivateKey = PrivateKey.create(storage)
                val newPublicKey = newPrivateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04") ?: ""
                logd("MultiRestore", "Created new key for wallet registration: ${newPublicKey.take(20)}... (length: ${newPublicKey.length})")
                
                // Add the NEW key via transaction, using backup providers for authorization
                val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.executeTransactionWithMultiKey {
                    arg { string(newPublicKey) }
                    arg { uint8(SigningAlgorithm.ECDSA_P256.cadenceIndex.toUByte()) }
                    arg { uint8(HashingAlgorithm.SHA2_256.cadenceIndex.toUByte()) }
                    arg { ufix64Safe(1000) }
                }
                
                if (txId != null) {
                    logd("MultiRestore", "Transaction created successfully: $txId")
                    val transactionState = TransactionState(
                        transactionId = txId,
                        time = System.currentTimeMillis(),
                        state = TransactionStatus.PENDING.ordinal,
                        type = TransactionState.TYPE_ADD_PUBLIC_KEY,
                        data = ""
                    )
                    currentTxId = txId
                    TransactionStateManager.newTransaction(transactionState)
                    pushBubbleStack(transactionState)
                    
                    // Start polling as backup mechanism
                    logd("MultiRestore", "Starting transaction polling backup mechanism")
                    startTransactionPolling(txId)
                } else {
                    android.util.Log.e("MultiRestore", "Failed to create transaction - txId is null")
                    throw RuntimeException("Failed to create add public key transaction")
                }
            } catch (e: Exception) {
                android.util.Log.e("MultiRestore", "restoreWallet failed", e)
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

    override fun onTransactionStateChange() {
        logd("MultiRestore", "onTransactionStateChange() called")
        val transactionList = TransactionStateManager.getTransactionStateList()
        logd("MultiRestore", "Transaction list size: ${transactionList.size}")
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_ADD_PUBLIC_KEY }
        logd("MultiRestore", "Found ADD_PUBLIC_KEY transaction: ${transaction?.transactionId}")
        transaction?.let { state ->
            logd("MultiRestore", "Checking transaction ${state.transactionId} against currentTxId $currentTxId, isSuccess: ${state.isSuccess()}")
            if (currentTxId == state.transactionId && state.isSuccess()) {
                logd("MultiRestore", "Transaction succeeded, calling syncAccountInfo()")
                currentTxId = null
                syncAccountInfo()
            } else {
                logd("MultiRestore", "Transaction not ready: currentTxId match=${currentTxId == state.transactionId}, isSuccess=${state.isSuccess()}")
            }
        } ?: logd("MultiRestore", "No ADD_PUBLIC_KEY transaction found")
    }

    private fun startTransactionPolling(txId: String) {
        logd("MultiRestore", "Starting polling for transaction: $txId")
        ioScope {
            // First, check if the transaction is already completed before starting polling
            delay(2000) // Wait 2 seconds for initial transaction processing
            
            val initialCheck = TransactionStateManager.getTransactionStateList().find { 
                it.transactionId == txId && it.type == TransactionState.TYPE_ADD_PUBLIC_KEY 
            }
            
            if (initialCheck != null) {
                logd("MultiRestore", "Initial check found transaction: ${initialCheck.transactionId}, state: ${initialCheck.state}, isSuccess: ${initialCheck.isSuccess()}")
                if (initialCheck.isSuccess()) {
                    logd("MultiRestore", "Transaction already completed, calling syncAccountInfo immediately")
                    if (currentTxId == txId) {
                        currentTxId = null
                        syncAccountInfo()
                    }
                    return@ioScope
                }
            } else {
                logd("MultiRestore", "Initial check: transaction not found in TransactionStateManager list")
                logd("MultiRestore", "Current TransactionStateManager list size: ${TransactionStateManager.getTransactionStateList().size}")
                TransactionStateManager.getTransactionStateList().forEach { tx ->
                    logd("MultiRestore", "  Transaction in list: ${tx.transactionId}, type: ${tx.type}, state: ${tx.state}")
                }
            }
            
            var attempts = 0
            val maxAttempts = 6 // Reduce to 6 attempts (1 minute total: 6 × 10 seconds)
            
            while (attempts < maxAttempts) {
                try {
                    logd("MultiRestore", "Polling attempt ${attempts + 1}/$maxAttempts for transaction $txId")
                    delay(10000) // Wait 10 seconds between checks
                    
                    val transactionList = TransactionStateManager.getTransactionStateList()
                    logd("MultiRestore", "Polling: TransactionStateManager list size: ${transactionList.size}")
                    
                    val transaction = transactionList.find { 
                        it.transactionId == txId && it.type == TransactionState.TYPE_ADD_PUBLIC_KEY 
                    }
                    
                    if (transaction != null) {
                        logd("MultiRestore", "Polling found transaction: ${transaction.transactionId}, state: ${transaction.state}, isSuccess: ${transaction.isSuccess()}")
                        if (transaction.isSuccess()) {
                            logd("MultiRestore", "Polling detected successful transaction, triggering syncAccountInfo")
                            if (currentTxId == txId) {
                                currentTxId = null
                                syncAccountInfo()
                            }
                            break
                        } else if (transaction.isFailed()) {
                            logd("MultiRestore", "Polling detected failed transaction")
                            break
                        }
                    } else {
                        logd("MultiRestore", "Polling: transaction $txId not found in list")
                        // Log all transactions for debugging
                        if (transactionList.isEmpty()) {
                            logd("MultiRestore", "  Transaction list is empty")
                        } else {
                            transactionList.forEachIndexed { index, tx ->
                                logd("MultiRestore", "  [$index] txId: ${tx.transactionId}, type: ${tx.type}, state: ${tx.state}")
                            }
                        }
                    }
                    
                    attempts++
                } catch (e: Exception) {
                    logd("MultiRestore", "Polling error: ${e.message}")
                    attempts++
                }
            }
            
            if (attempts >= maxAttempts) {
                logd("MultiRestore", "Polling timeout reached for transaction $txId after ${maxAttempts} attempts")
                logd("MultiRestore", "onTransactionStateChange() may not be working properly")
                // Don't fail silently - this indicates a problem with transaction state management
                uiScope {
                    restoreFailed()
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun syncAccountInfo() {
        ioScope {
            try {
                val service = retrofit().create(ApiService::class.java)
                val baseDir = File(Env.getApp().filesDir, "wallet")
                val storage = FileSystemStorage(baseDir)
                
                // Create the NEW key that was added via transaction
                val newPrivateKey = PrivateKey.create(storage)
                val newPublicKey = newPrivateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04") ?: ""
                
                // Create backup crypto providers for backup signatures
                val providers = mnemonicList.map { mnemonic ->
                    val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, storage)
                    val words = mnemonic.split(" ")
                    if (words.size == 15) {
                        BackupCryptoProvider(seedPhraseKey)
                    } else {
                        HDWalletCryptoProvider(seedPhraseKey)
                    }
                }
                
                val resp = service.signAccount(
                    AccountSignRequest(
                        AccountKey(
                            publicKey = newPublicKey,
                            hashAlgo = HashingAlgorithm.SHA2_256.cadenceIndex,
                            signAlgo = SigningAlgorithm.ECDSA_P256.cadenceIndex
                        ),
                        providers.map {
                            val jwt = getFirebaseJwt()
                            AccountKeySignature(
                                publicKey = it.getPublicKey(),
                                signMessage = jwt,
                                signature = it.getUserSignature(jwt),
                                weight = it.getKeyWeight(),
                                hashAlgo = it.getHashAlgorithm().cadenceIndex,
                                signAlgo = it.getSignatureAlgorithm().cadenceIndex
                            )
                        }.toList()
                    )
                )
                if (resp.status == 200) {
                    val activity = BaseActivity.getCurrentActivity() ?: return@ioScope
                    // Add small delay to allow server database update to propagate
                    delay(2000)
                    // Use the NEW key for login
                    login(newPrivateKey) { isSuccess ->
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
                android.util.Log.e("MultiRestore", "syncAccountInfo failed", e)
                loge(e)
                ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, e)
            }
        }
    }

    fun addWalletInfo(userName: String, address: String) {
        restoreUserName = userName
        restoreAddress = address
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun CadenceScript.executeTransactionWithMultiKey(arguments: CadenceArgumentsBuilder.() -> Unit): String? {
        val args = CadenceArgumentsBuilder().apply { arguments(this) }
        val baseDir = File(Env.getApp().filesDir, "wallet")
        val storage = FileSystemStorage(baseDir)
        
        try {
            logd("MultiRestore", "Creating seed phrase keys from ${mnemonicList.size} mnemonics")
            
            // Create and properly initialize seed phrase keys from mnemonics with keyPair
            val seedPhraseKeys = mnemonicList.mapIndexed { index, mnemonic ->
                logd("MultiRestore", "Creating SeedPhraseKey $index from mnemonic")
                logd("MultiRestore", "Mnemonic $index word count: ${mnemonic.split(" ").size}")
                
                val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, storage)
                
                // Verify the key works by testing key generation
                try {
                    val publicKeySecp256k1 = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)
                    val privateKeySecp256k1 = seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_secp256k1)
                    val publicKeyP256 = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_P256)
                    val privateKeyP256 = seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_P256)
                    
                    logd("MultiRestore", "SeedPhraseKey $index initialized successfully")
                    logd("MultiRestore", "  ECDSA_secp256k1 - public: ${publicKeySecp256k1?.toHexString()?.take(20)}..., private: ${if (privateKeySecp256k1 != null) "available" else "null"}")
                    logd("MultiRestore", "  ECDSA_P256 - public: ${publicKeyP256?.toHexString()?.take(20)}..., private: ${if (privateKeyP256 != null) "available" else "null"}")
                    
                    if (publicKeySecp256k1 == null || privateKeySecp256k1 == null) {
                        throw RuntimeException("SeedPhraseKey $index failed to generate ECDSA_secp256k1 keys")
                    }
                    if (publicKeyP256 == null || privateKeyP256 == null) {
                        throw RuntimeException("SeedPhraseKey $index failed to generate ECDSA_P256 keys")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MultiRestore", "Failed to initialize SeedPhraseKey $index", e)
                    throw RuntimeException("SeedPhraseKey $index initialization failed: ${e.message}", e)
                }
                
                seedPhraseKey
            }
            
            // Create providers from properly initialized seed phrase keys
            val providers = seedPhraseKeys.mapIndexed { index, seedPhraseKey ->
                logd("MultiRestore", "Creating crypto provider $index")
                val words = seedPhraseKey.mnemonic
                val provider = if (words.size == 15) {
                    BackupCryptoProvider(seedPhraseKey)
                } else {
                    HDWalletCryptoProvider(seedPhraseKey)
                }
                
                // Verify the provider can generate a public key
                try {
                    val providerPublicKey = provider.getPublicKey()
                    logd("MultiRestore", "Provider $index public key: ${providerPublicKey.take(20)}...")
                    if (providerPublicKey.isEmpty()) {
                        throw RuntimeException("Provider $index generated empty public key")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MultiRestore", "Provider $index failed to generate public key", e)
                    throw RuntimeException("Provider $index public key generation failed: ${e.message}", e)
                }
                
                provider
            }
            
            logd("MultiRestore", "Sending transaction with ${providers.size} providers")
            
            return sendTransactionWithMultiSignature(providers = providers, builder = {
                args.build().forEach { arg(it) }
                walletAddress(restoreAddress)
                script(this@executeTransactionWithMultiKey.getScript().addPlatformInfo())
            })
        } catch (e: Exception) {
            android.util.Log.e("MultiRestore", "executeTransactionWithMultiKey failed", e)
            loge(e)
            reportCadenceErrorToDebugView(scriptId, e)
            if (e is InvalidKeyException) {
                ErrorReporter.reportCriticalWithMixpanel(WalletError.QUERY_ACCOUNT_KEY_FAILED, e)
                Instabug.show()
            }
            return null
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun createBackupCryptoProvider(seedPhraseKey: SeedPhraseKey): BackupCryptoProvider {
        logd("MultiRestore", "Creating BackupCryptoProvider")
        
        // Verify the seed phrase key is properly initialized
        try {
            val publicKey = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)
            logd("MultiRestore", "SeedPhraseKey verified, public key: ${publicKey?.toHexString()?.take(20)}...")
        } catch (e: Exception) {
            android.util.Log.e("MultiRestore", "SeedPhraseKey verification failed", e)
            throw RuntimeException("SeedPhraseKey is not properly initialized", e)
        }
        
        val wallet = WalletFactory.createKeyWallet(
            seedPhraseKey,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            getStorage()
        )
        
        // Use default algorithms - algorithm determination will happen in the transaction layer
        logd("MultiRestore", "Using default algorithms for BackupCryptoProvider")
        return BackupCryptoProvider(seedPhraseKey, wallet as KeyWallet)
    }

    /**
     * Create a SeedPhraseKey with properly initialized keyPair
     * This fixes the "Signing key is empty or not available" error
     */
    @OptIn(ExperimentalStdlibApi::class)
    private fun createSeedPhraseKeyWithKeyPair(mnemonic: String, storage: FileSystemStorage): SeedPhraseKey {
        logd("MultiRestore", "Creating SeedPhraseKey with proper keyPair initialization")
        
        try {
            // Create a simple dummy KeyPair to pass the null check in sign()
            // The actual signing uses hdWallet.getKeyByCurve() internally, not this keyPair
            val keyGenerator = java.security.KeyPairGenerator.getInstance("EC")
            keyGenerator.initialize(256)
            val dummyKeyPair = keyGenerator.generateKeyPair()
            
            logd("MultiRestore", "Created dummy KeyPair for null check")
            
            // Create SeedPhraseKey with the dummy keyPair
            val seedPhraseKey = SeedPhraseKey(
                mnemonicString = mnemonic,
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = dummyKeyPair,
                storage = storage
            )
            
            // Verify that the SeedPhraseKey can generate keys using its internal hdWallet
            try {
                val publicKey = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)
                if (publicKey == null) {
                    throw RuntimeException("SeedPhraseKey failed to generate public key")
                }
                logd("MultiRestore", "SeedPhraseKey successfully verified with public key: ${publicKey.toHexString().take(20)}...")
            } catch (e: Exception) {
                android.util.Log.e("MultiRestore", "SeedPhraseKey verification failed", e)
                throw RuntimeException("SeedPhraseKey verification failed", e)
            }
            
            return seedPhraseKey
            
        } catch (e: Exception) {
            android.util.Log.e("MultiRestore", "Failed to create SeedPhraseKey", e)
            throw RuntimeException("Failed to create SeedPhraseKey with proper keyPair", e)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
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
                        
                        // Create account key from the new private key
                        val publicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04") ?: ""
                        
                        // Sign JWT with the new private key
                        val jwt = getFirebaseJwt()
                        val domainTagBytes = DomainTag.User.bytes
                        val jwtBytes = jwt.encodeToByteArray()
                        val dataToSign = domainTagBytes + jwtBytes
                        val signatureBytes = privateKey.sign(dataToSign, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
                        val signature = signatureBytes.joinToString("") { "%02x".format(it) }
                        
                        val resp = service.login(
                            LoginRequest(
                                signature = signature,
                                accountKey = AccountKey(
                                    publicKey = publicKey,
                                    hashAlgo = HashingAlgorithm.SHA2_256.cadenceIndex,
                                    signAlgo = SigningAlgorithm.ECDSA_P256.cadenceIndex
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
                                        // Store the private key with the prefix for proper wallet loading
                                        val prefix = generatePrefix(restoreUserName)
                                        val keyId = "prefix_key_$prefix"
                                        privateKey.store(keyId, prefix)
                                        logd("MultiRestore", "Stored private key with ID: $keyId and prefix: $prefix")
                                        
                                        // Store the mnemonics globally for backup access and mark as multi-restore
                                        val passwordMap = try {
                                            val pref = readWalletPassword()
                                            if (pref.isBlank()) {
                                                HashMap<String, String>()
                                            } else {
                                                Gson().fromJson(pref, object : TypeToken<HashMap<String, String>>() {}.type)
                                            }
                                        } catch (e: Exception) {
                                            HashMap<String, String>()
                                        }
                                        
                                        // Store the first mnemonic as the global one for multi-restore support
                                        val primaryMnemonic = mnemonicList.firstOrNull() ?: ""
                                        passwordMap["global"] = primaryMnemonic
                                        
                                        // Store all mnemonics for multi-restore identification
                                        mnemonicList.forEachIndexed { index, mnemonic ->
                                            passwordMap["multi_restore_$index"] = mnemonic
                                        }
                                        passwordMap["multi_restore_count"] = mnemonicList.size.toString()
                                        passwordMap["multi_restore_address"] = restoreAddress
                                        
                                        storeWalletPassword(Gson().toJson(passwordMap))
                                        logd("MultiRestore", "Stored ${mnemonicList.size} mnemonics for multi-restore backup access")
                                        
                                        AccountManager.add(
                                            Account(
                                                userInfo = service.userInfo().data,
                                                prefix = prefix
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
}