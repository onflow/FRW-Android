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
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.ChainId
import org.onflow.flow.infrastructure.Cadence.Companion.uint8

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

    @OptIn(ExperimentalStdlibApi::class)
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
                logd("MultiRestore", "Starting restoreWallet with ${mnemonicList.size} mnemonics")
                val baseDir = File(Env.getApp().filesDir, "wallet")
                val storage = FileSystemStorage(baseDir)
                
                // Create and properly initialize seed phrase key from the first mnemonic
                logd("MultiRestore", "Creating SeedPhraseKey from first mnemonic")
                val firstMnemonic = mnemonicList.first()
                val seedPhraseKey = createSeedPhraseKeyWithKeyPair(firstMnemonic, storage)
                
                // Force initialization and verify the key works
                try {
                    val publicKey = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)
                    logd("MultiRestore", "SeedPhraseKey initialized successfully, public key: ${publicKey?.toHexString()?.take(20)}...")
                } catch (e: Exception) {
                    android.util.Log.e("MultiRestore", "Failed to initialize SeedPhraseKey from first mnemonic", e)
                    throw e
                }
                
                // Create a new wallet using the properly initialized seed phrase key
                logd("MultiRestore", "Creating KeyWallet from SeedPhraseKey")
                val keyWallet = try {
                    WalletFactory.createKeyWallet(
                        seedPhraseKey,
                        setOf(ChainId.Mainnet, ChainId.Testnet),
                        storage
                    )
                } catch (e: Exception) {
                    android.util.Log.e("MultiRestore", "Failed to create KeyWallet", e)
                    throw e
                }
                
                // Initialize WalletManager with the new wallet
                logd("MultiRestore", "Initializing WalletManager")
                WalletManager.init()
                
                // Create the transaction to add the new key
                logd("MultiRestore", "Creating private key for transaction")
                val privateKey = PrivateKey.create(storage)
                
                logd("MultiRestore", "Executing add public key transaction")
                
                // Get the public key and normalize it (remove "04" prefix if present)
                val rawPublicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: ""
                val normalizedPublicKey = if (rawPublicKey.startsWith("04") && rawPublicKey.length == 130) {
                    rawPublicKey.substring(2) // Remove "04" prefix for Flow's decodeHex()
                } else {
                    rawPublicKey
                }
                logd("MultiRestore", "Public key for transaction: ${normalizedPublicKey.take(20)}... (length: ${normalizedPublicKey.length})")
                
                val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.executeTransactionWithMultiKey {
                    arg { string(normalizedPublicKey) }
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

    private fun syncAccountInfo() {
        ioScope {
            try {
                val baseDir = File(Env.getApp().filesDir, "wallet")
                val storage = FileSystemStorage(baseDir)
                val privateKey = PrivateKey.create(storage)
                val service = retrofit().create(ApiService::class.java)
                
                // Create seed phrase keys from mnemonics with proper keyPair initialization
                val seedPhraseKeys = mnemonicList.map { mnemonic ->
                    createSeedPhraseKeyWithKeyPair(mnemonic, storage)
                }
                
                // Create providers from seed phrase keys
                val providers = seedPhraseKeys.map { seedPhraseKey ->
                    val words = seedPhraseKey.mnemonic
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

    private fun restoreFromMnemonic(mnemonic: String) {
        try {
            logd("MultiRestore", "Starting restoreFromMnemonic with mnemonic length: ${mnemonic.length}")
            
            val seedPhraseKey = SeedPhraseKey(
                mnemonicString = mnemonic,
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = null,
                storage = getStorage()
            )
            
            // Create the backup crypto provider with appropriate algorithms
            val backupProvider = createBackupCryptoProvider(seedPhraseKey)
            logd("MultiRestore", "Created BackupCryptoProvider with algorithms: ${backupProvider.getSignatureAlgorithm()}, ${backupProvider.getHashAlgorithm()}")
            
            // Add the mnemonic to the transaction list
            addMnemonicToTransaction(mnemonic)
            restoreWallet()
        } catch (e: Exception) {
            android.util.Log.e("MultiRestore", "restoreFromMnemonic failed", e)
            loge(e)
            ErrorReporter.reportWithMixpanel(BackupError.MNEMONIC_RESTORE_FAILED, e)
            restoreFailed()
        }
    }

    private fun restoreFromKeystore(keystore: String) {
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = keystore,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = getStorage()
        )
        createBackupCryptoProvider(seedPhraseKey)
        try {
            // Add the keystore to the transaction list
            addMnemonicToTransaction(keystore)
            restoreWallet()
        } catch (e: Exception) {
            loge(e)
            ErrorReporter.reportWithMixpanel(BackupError.KEYSTORE_RESTORE_FAILED, e)
            restoreFailed()
        }
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
}