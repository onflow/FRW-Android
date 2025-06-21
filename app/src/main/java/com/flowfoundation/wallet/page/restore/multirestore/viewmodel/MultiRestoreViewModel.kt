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
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.logd
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
    private var storedKeyId = ""
    private var storedPrefix = ""
    private var storedSigningAlgorithm = SigningAlgorithm.ECDSA_P256
    private var storedHashingAlgorithm = HashingAlgorithm.SHA2_256

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
            MainActivity.relaunch(activity, clearTop = true)
            return
        }

        ioScope {
            try {
                logd("MultiRestore", "Starting restoreWallet with ${mnemonicList.size} mnemonics")
                val baseDir = File(Env.getApp().filesDir, "wallet")
                val storage = FileSystemStorage(baseDir)

                // Detect algorithms from the first mnemonic to determine what to use for the new key
                val firstMnemonic = mnemonicList.firstOrNull()
                    ?: throw RuntimeException("No mnemonics available for algorithm detection")
                val firstSeedPhraseKey = createSeedPhraseKeyWithKeyPair(firstMnemonic, storage)
                val detectedAlgorithms = detectAlgorithms(firstSeedPhraseKey, restoreAddress)

                logd("MultiRestore", "Detected algorithms for new key creation: signing=${detectedAlgorithms.signingAlgorithm}, hashing=${detectedAlgorithms.hashingAlgorithm}")

                // Create backup crypto providers from mnemonics for transaction authorization
                // Use detected algorithms for each provider
                mnemonicList.map { mnemonic ->
                    val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, storage)
                    val words = mnemonic.split(" ")
                    val algorithmPair = detectAlgorithms(seedPhraseKey, restoreAddress)

                    if (words.size == 15) {
                        BackupCryptoProvider(seedPhraseKey, null, algorithmPair.signingAlgorithm, algorithmPair.hashingAlgorithm)
                    } else {
                        HDWalletCryptoProvider(seedPhraseKey, algorithmPair.signingAlgorithm, algorithmPair.hashingAlgorithm)
                    }
                }

                // Create ONE new key for wallet registration and store it immediately for reuse
                val newPrivateKey = PrivateKey.create(storage)
                val prefix = generatePrefix(restoreUserName)
                val keyId = "prefix_key_$prefix"
                newPrivateKey.store(keyId, prefix)
                logd("MultiRestore", "Created and stored new key with ID: $keyId and prefix: $prefix")

                val newPublicKey = newPrivateKey.publicKey(detectedAlgorithms.signingAlgorithm)?.toHexString()?.removePrefix("04") ?: ""
                logd("MultiRestore", "Created new key for wallet registration: ${newPublicKey.take(20)}... (length: ${newPublicKey.length})")
                logd("MultiRestore", "Using algorithms for new key: signing=${detectedAlgorithms.signingAlgorithm}, hashing=${detectedAlgorithms.hashingAlgorithm}")

                // Store key information for later retrieval
                storedKeyId = keyId
                storedPrefix = prefix
                storedSigningAlgorithm = detectedAlgorithms.signingAlgorithm
                storedHashingAlgorithm = detectedAlgorithms.hashingAlgorithm

                // Add the NEW key via transaction, using backup providers for authorization
                val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.executeTransactionWithMultiKey {
                    arg { string(newPublicKey) }
                    arg { uint8(detectedAlgorithms.signingAlgorithm.cadenceIndex.toUByte()) }
                    arg { uint8(detectedAlgorithms.hashingAlgorithm.cadenceIndex.toUByte()) }
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
                    logd("MultiRestore", "Failed to create transaction - txId is null")
                    throw RuntimeException("Failed to create add public key transaction")
                }
            } catch (e: Exception) {
                logd("MultiRestore", "restoreWallet failed")
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
                logd("MultiRestore", "Polling timeout reached for transaction $txId after $maxAttempts attempts")
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

                // Use the stored key that was created and added to the account in restoreWallet
                if (storedKeyId.isEmpty() || storedPrefix.isEmpty()) {
                    throw RuntimeException("Stored key information is missing - cannot proceed with syncAccountInfo")
                }

                val newPrivateKey = try {
                    PrivateKey.get(storedKeyId, storedPrefix, storage)
                } catch (e: Exception) {
                    throw RuntimeException("Failed to load the stored key that was added to the account: ${e.message}", e)
                }

                val newPublicKey = newPrivateKey.publicKey(storedSigningAlgorithm)?.toHexString()?.removePrefix("04") ?: ""
                logd("MultiRestore", "Using stored key for syncAccountInfo: ${newPublicKey.take(20)}... (algorithms: signing=${storedSigningAlgorithm}, hashing=${storedHashingAlgorithm})")

                // Create backup crypto providers for backup signatures (detect algorithms for each)
                val providers = mnemonicList.map { mnemonic ->
                    val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, storage)
                    val words = mnemonic.split(" ")
                    val algorithmPair = detectAlgorithms(seedPhraseKey, restoreAddress)

                    if (words.size == 15) {
                        BackupCryptoProvider(seedPhraseKey, null, algorithmPair.signingAlgorithm, algorithmPair.hashingAlgorithm)
                    } else {
                        HDWalletCryptoProvider(seedPhraseKey, algorithmPair.signingAlgorithm, algorithmPair.hashingAlgorithm)
                    }
                }

                val resp = service.signAccount(
                    AccountSignRequest(
                        AccountKey(
                            publicKey = newPublicKey,
                            hashAlgo = storedHashingAlgorithm.cadenceIndex,
                            signAlgo = storedSigningAlgorithm.cadenceIndex
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
                    BaseActivity.getCurrentActivity() ?: return@ioScope
                    // Add small delay to allow server database update to propagate
                    delay(2000)
                    logd("MultiRestore", "Server sync successful, proceeding with login using stored key")
                    loginWithStoredKey(newPrivateKey) { isSuccess ->
                        if (isSuccess) {
                            logd("MultiRestore", "Login successful")
                        } else {
                            logd("MultiRestore", "Login failed")
                            uiScope { restoreFailed() }
                        }
                    }
                } else {
                    logd("MultiRestore", "Server sync failed with status: ${resp.status}")
                    uiScope { restoreFailed() }
                }
            } catch (e: Exception) {
                logd("MultiRestore", "syncAccountInfo failed: ${e.message}")
                uiScope { restoreFailed() }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun loginWithStoredKey(privateKey: PrivateKey, callback: (isSuccess: Boolean) -> Unit) {
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

                        // Create account key from the new private key using detected algorithms
                        val publicKey = privateKey.publicKey(storedSigningAlgorithm)?.toHexString()?.removePrefix("04") ?: ""

                        // Sign JWT with the new private key using detected algorithms
                        val jwt = getFirebaseJwt()
                        val domainTagBytes = DomainTag.User.bytes
                        val jwtBytes = jwt.encodeToByteArray()
                        val dataToSign = domainTagBytes + jwtBytes
                        val signatureBytes = privateKey.sign(dataToSign, storedSigningAlgorithm, storedHashingAlgorithm)
                        val signature = signatureBytes.joinToString("") { "%02x".format(it) }

                        val resp = service.login(
                            LoginRequest(
                                signature = signature,
                                accountKey = AccountKey(
                                    publicKey = publicKey,
                                    hashAlgo = storedHashingAlgorithm.cadenceIndex,
                                    signAlgo = storedSigningAlgorithm.cadenceIndex
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
                                        // The key is already stored, now just set up the multi-restore metadata
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

                                        // Store multi-restore metadata
                                        passwordMap["multi_restore_count"] = mnemonicList.size.toString()
                                        passwordMap["multi_restore_address"] = restoreAddress
                                        passwordMap["multi_restore_completed_time"] = System.currentTimeMillis().toString()

                                        mnemonicList.forEachIndexed { index, mnemonic ->
                                            passwordMap["multi_restore_$index"] = mnemonic
                                        }

                                        storeWalletPassword(Gson().toJson(passwordMap))
                                        logd("MultiRestore", "Stored multi-restore metadata for ${mnemonicList.size} mnemonics with completion time")

                                        // Add the account to AccountManager
                                        AccountManager.add(
                                            Account(
                                                userInfo = service.userInfo().data,
                                                prefix = storedPrefix
                                            ),
                                            firebaseUid()
                                        )
                                        clearUserCache()
                                        logd("MultiRestore", "Added account to AccountManager with prefix: $storedPrefix")

                                        // Complete the login process
                                        uiScope {
                                            MixpanelManager.accountRestore(restoreAddress, RestoreType.MULTI_BACKUP)
                                            delay(200)
                                            val activity = BaseActivity.getCurrentActivity()
                                            if (activity != null) {
                                                try {
                                                    MainActivity.relaunch(activity, clearTop = true)
                                                    logd("MultiRestore", "Successfully launched MainActivity")
                                                } catch (e: Exception) {
                                                    logd("MultiRestore", "MainActivity launch failed: ${e.message}")
                                                    MainActivity.relaunch(Env.getApp(), clearTop = true)
                                                }
                                            } else {
                                                logd("MultiRestore", "Activity is null, using Env.getApp() fallback")
                                                MainActivity.relaunch(Env.getApp(), clearTop = true)
                                            }
                                            callback.invoke(true)
                                        }
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
                    throw RuntimeException("SeedPhraseKey $index initialization failed: ${e.message}", e)
                }

                seedPhraseKey
            }

            // Create providers from properly initialized seed phrase keys
            val providers = seedPhraseKeys.mapIndexed { index, seedPhraseKey ->
                logd("MultiRestore", "Creating crypto provider $index")
                val words = seedPhraseKey.mnemonic
                val algorithmPair = detectAlgorithms(seedPhraseKey, restoreAddress)
                val provider = if (words.size == 15) {
                    BackupCryptoProvider(seedPhraseKey, null, algorithmPair.signingAlgorithm, algorithmPair.hashingAlgorithm)
                } else {
                    HDWalletCryptoProvider(seedPhraseKey, algorithmPair.signingAlgorithm, algorithmPair.hashingAlgorithm)
                }

                // Verify the provider can generate a public key
                try {
                    val providerPublicKey = provider.getPublicKey()
                    logd("MultiRestore", "Provider $index public key: ${providerPublicKey.take(20)}...")
                    logd("MultiRestore", "Provider $index algorithms: signing=${algorithmPair.signingAlgorithm}, hashing=${algorithmPair.hashingAlgorithm}")
                    if (providerPublicKey.isEmpty()) {
                        throw RuntimeException("Provider $index generated empty public key")
                    }
                } catch (e: Exception) {
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
            loge(e)
            reportCadenceErrorToDebugView(scriptId, e)
            if (e is InvalidKeyException) {
                ErrorReporter.reportCriticalWithMixpanel(WalletError.QUERY_ACCOUNT_KEY_FAILED, e)
                Instabug.show()
            }
            return null
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
                    ?: throw RuntimeException("SeedPhraseKey failed to generate public key")
                logd("MultiRestore", "SeedPhraseKey successfully verified with public key: ${publicKey.toHexString().take(20)}...")
            } catch (e: Exception) {
                throw RuntimeException("SeedPhraseKey verification failed", e)
            }

            return seedPhraseKey

        } catch (e: Exception) {
            throw RuntimeException("Failed to create SeedPhraseKey with proper keyPair", e)
        }
    }

    /**
     * Algorithm detection result containing both signing and hashing algorithms
     */
    private data class AlgorithmPair(
        val signingAlgorithm: SigningAlgorithm,
        val hashingAlgorithm: HashingAlgorithm
    )

    /**
     * Detect the correct signing and hashing algorithms by testing combinations against on-chain keys
     * This ensures backward compatibility with different backup versions and prioritizes keys with sufficient weight
     */
    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun detectAlgorithms(seedPhraseKey: SeedPhraseKey, address: String): AlgorithmPair {
        logd("MultiRestore", "Detecting signing and hashing algorithms for address: $address")

        val signingAlgorithms = listOf(SigningAlgorithm.ECDSA_secp256k1, SigningAlgorithm.ECDSA_P256)

        try {
            // Get on-chain keys for comparison
            val flowAccount = com.flowfoundation.wallet.manager.flow.FlowCadenceApi.getAccount(address)
            val onChainKeys = flowAccount.keys?.toList() ?: run {
                logd("MultiRestore", "No on-chain keys found, defaulting to ECDSA_P256 + SHA2_256")
                return AlgorithmPair(SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
            }

            logd("MultiRestore", "Found ${onChainKeys.size} on-chain keys to test against")

            // First pass: Look for keys with sufficient weight (1000+) and not revoked
            for (signingAlgorithm in signingAlgorithms) {
                try {
                    val publicKey = seedPhraseKey.publicKey(signingAlgorithm)?.toHexString() ?: continue

                    logd("MultiRestore", "Testing $signingAlgorithm: generated key = ${publicKey.take(20)}...")

                    // Check if this key matches any on-chain key using robust matching logic
                    val matchedKey = onChainKeys.find { onChainKey ->
                        !onChainKey.revoked && onChainKey.weight.toIntOrNull()?.let { it >= 1000 } == true && isKeyMatchRobust(publicKey, onChainKey.publicKey)
                    }

                    if (matchedKey != null) {
                        logd("MultiRestore", "✓ HIGH-WEIGHT KEY MATCH found with $signingAlgorithm! On-chain key index: ${matchedKey.index}")
                        logd("MultiRestore", "  Generated: $publicKey")
                        logd("MultiRestore", "  On-chain:  ${matchedKey.publicKey}")
                        logd("MultiRestore", "  Key weight: ${matchedKey.weight} (sufficient for authorization)")
                        logd("MultiRestore", "  On-chain hashing algorithm: ${matchedKey.hashingAlgorithm}")

                        val detectedPair = AlgorithmPair(signingAlgorithm, matchedKey.hashingAlgorithm)
                        logd("MultiRestore", "Using detected algorithms: signing=${detectedPair.signingAlgorithm}, hashing=${detectedPair.hashingAlgorithm}")
                        return detectedPair
                    }
                } catch (e: Exception) {
                    logd("MultiRestore", "Error testing signing algorithm $signingAlgorithm: ${e.message}")
                }
            }

            // Second pass: Look for any matching key (including lower weight ones)
            for (signingAlgorithm in signingAlgorithms) {
                try {
                    val publicKey = seedPhraseKey.publicKey(signingAlgorithm)?.toHexString() ?: continue

                    logd("MultiRestore", "Testing $signingAlgorithm (second pass): generated key = ${publicKey.take(20)}...")

                    // Check if this key matches any on-chain key using robust matching logic
                    val matchedKey = onChainKeys.find { onChainKey ->
                        !onChainKey.revoked && isKeyMatchRobust(publicKey, onChainKey.publicKey)
                    }

                    if (matchedKey != null) {
                        logd("MultiRestore", "✓ KEY MATCH found with $signingAlgorithm! On-chain key index: ${matchedKey.index}")
                        logd("MultiRestore", "  Generated: $publicKey")
                        logd("MultiRestore", "  On-chain:  ${matchedKey.publicKey}")
                        logd("MultiRestore", "  Key weight: ${matchedKey.weight} ${if (matchedKey.weight.toIntOrNull()?.let { it >= 1000 } == true) "(sufficient)" else "(insufficient - may need additional signatures)"}")
                        logd("MultiRestore", "  On-chain hashing algorithm: ${matchedKey.hashingAlgorithm}")

                        val detectedPair = AlgorithmPair(signingAlgorithm, matchedKey.hashingAlgorithm)
                        logd("MultiRestore", "Using detected algorithms: signing=${detectedPair.signingAlgorithm}, hashing=${detectedPair.hashingAlgorithm}")
                        return detectedPair
                    }
                } catch (e: Exception) {
                    logd("MultiRestore", "Error testing signing algorithm $signingAlgorithm: ${e.message}")
                }
            }

            logd("MultiRestore", "No matching algorithm found, defaulting to ECDSA_P256 + SHA2_256")
            return AlgorithmPair(SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)

        } catch (e: Exception) {
            logd("MultiRestore", "Error in algorithm detection: ${e.message}, defaulting to ECDSA_P256 + SHA2_256")
            return AlgorithmPair(SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        }
    }

    /**
     * Robust key matching logic adapted from Transaction.kt
     * Handles all possible public key format variations for maximum backward compatibility
     */
    private fun isKeyMatchRobust(providerPublicKey: String, onChainPublicKey: String): Boolean {
        val providerRaw = providerPublicKey.removePrefix("0x").lowercase()
        val onChainRaw = onChainPublicKey.removePrefix("0x").lowercase()

        // Handle both compressed and uncompressed EC keys with comprehensive format matching
        val providerStripped = if (providerRaw.startsWith("04") && providerRaw.length == 130) providerRaw.substring(2) else providerRaw
        val onChainStripped = if (onChainRaw.startsWith("04") && onChainRaw.length == 130) onChainRaw.substring(2) else onChainRaw
        val providerWith04 = if (!providerRaw.startsWith("04") && providerRaw.length == 128) "04$providerRaw" else providerRaw
        val onChainWith04 = if (!onChainRaw.startsWith("04") && onChainRaw.length == 128) "04$onChainRaw" else onChainRaw

        // Try all possible combinations for maximum backward compatibility
        return onChainRaw == providerRaw ||
                onChainRaw == providerStripped ||
                onChainStripped == providerRaw ||
                onChainStripped == providerStripped ||
                onChainRaw == providerWith04 ||
                onChainWith04 == providerRaw ||
                onChainWith04 == providerStripped ||
                onChainStripped == providerWith04
    }
}