package com.flowfoundation.wallet.page.restore.keystore.viewmodel

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flow.wallet.keys.KeyFormat
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.RestoreType
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.OtherHostService
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.ImportRequest
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.network.retrofitWithHost
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.restore.keystore.PrivateKeyStoreCryptoProvider
import com.flowfoundation.wallet.page.restore.keystore.model.KeyStoreOption
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.flowfoundation.wallet.page.walletrestore.firebaseLogin
import com.flowfoundation.wallet.page.walletrestore.getFirebaseUid
import com.flowfoundation.wallet.utils.error.BackupError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.WalletError
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.setBackupManually
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import retrofit2.HttpException
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import wallet.core.jni.StoredKey
import com.flowfoundation.wallet.utils.Env.getStorage
import org.onflow.flow.models.DomainTag
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.ChainId
import org.onflow.flow.models.hexToBytes

class KeyStoreRestoreViewModel : ViewModel() {

    private val queryService by lazy {
        retrofitWithHost("https://production.key-indexer.flow.com").create(OtherHostService::class.java)
    }

    private val apiService by lazy {
        retrofit().create(ApiService::class.java)
    }

    private val addressList = mutableListOf<KeystoreAddress>()
    private var currentKeyStoreAddress: KeystoreAddress? = null
    private var restoreType: RestoreType = RestoreType.KEYSTORE

    val addressListLiveData = MutableLiveData<List<KeystoreAddress>>()
    val optionChangeLiveData = MutableLiveData<KeyStoreOption>()
    val loadingLiveData = MutableLiveData<Boolean>()

    fun changeOption(option: KeyStoreOption) {
        optionChangeLiveData.postValue(option)
    }

    fun getAddressList(): List<KeystoreAddress> {
        return addressList
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun importKeyStore(json: String, password: String, address: String) {
        loadingLiveData.postValue(true)
        restoreType = RestoreType.KEYSTORE
        try {
            logd("KeyStoreRestoreViewModel", "Starting keystore import")
            ioScope {
                val storage = getStorage()
                logd("KeyStoreRestoreViewModel", "Got storage")

                val keyStore = StoredKey.importJSON(json.toByteArray())
                logd("KeyStoreRestoreViewModel", "Imported JSON keystore")

                val decryptedKey = keyStore.decryptPrivateKey(password.toByteArray())
                logd("KeyStoreRestoreViewModel", "Decrypted private key")

                val key = PrivateKey.create(storage).apply {
                    logd("KeyStoreRestoreViewModel", "Created new PrivateKey instance")
                    importPrivateKey(decryptedKey, KeyFormat.RAW)
                    logd("KeyStoreRestoreViewModel", "Imported private key")
                }
                logd("KeyStoreRestoreViewModel", "Key created and imported: $key")

                // Create a new wallet using the private key directly
                WalletFactory.createKeyWallet(
                    key,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                )
                logd("KeyStoreRestoreViewModel", "Created key wallet")

                // Initialize WalletManager with the new wallet
                WalletManager.init()
                logd("KeyStoreRestoreViewModel", "Initialized WalletManager")

                // Get public keys and format them correctly
                val p1PublicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04")
                val k1PublicKey = key.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()?.removePrefix("04")
                logd("KeyStoreRestoreViewModel", "Generated public keys - P256: $p1PublicKey, K1: $k1PublicKey")

                if (address.isEmpty()) {
                    logd("KeyStoreRestoreViewModel", "No address provided, checking query address")
                    checkIsQueryAddress(
                        key.privateKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString() ?: "",
                        k1PublicKey ?: "",
                        key.privateKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: "",
                        p1PublicKey ?: ""
                    )
                } else {
                    logd("KeyStoreRestoreViewModel", "Address provided: $address, querying public key")
                    queryAddressPublicKey(
                        address,
                        key.privateKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString() ?: "",
                        k1PublicKey ?: "",
                        key.privateKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: "",
                        p1PublicKey ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logd("KeyStoreRestoreViewModel", "Error during keystore import: ${e.message}")
            ErrorReporter.reportWithMixpanel(BackupError.KEYSTORE_RESTORE_FAILED, e)
            loadingLiveData.postValue(false)
            toast(msgRes = R.string.restore_failed)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun importPrivateKey(privateKey: String, address: String) {
        loadingLiveData.postValue(true)
        restoreType = RestoreType.PRIVATE_KEY
        try {
            ioScope {
                val storage = getStorage()
                val key = PrivateKey.create(storage).apply {
                    logd("KeyStoreRestoreViewModel", "Created new PrivateKey instance")
                    // Convert hex string to bytes
                    val keyBytes = privateKey.hexToBytes()
                    logd("KeyStoreRestoreViewModel", "Converted private key to bytes")
                    importPrivateKey(keyBytes, KeyFormat.RAW)
                    logd("KeyStoreRestoreViewModel", "Imported private key")
                }
                logd("KeyStoreRestoreViewModel", key)

                // Create a new wallet using the private key directly
                WalletFactory.createKeyWallet(
                    key,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                )

                // Initialize WalletManager with the new wallet
                WalletManager.init()

                val p1PublicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04")
                val k1PublicKey = key.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()?.removePrefix("04")

                if (address.isEmpty()) {
                    checkIsQueryAddress(
                        key.privateKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString() ?: "",
                        k1PublicKey ?: "",
                        key.privateKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: "",
                        p1PublicKey ?: ""
                    )
                } else {
                    queryAddressPublicKey(
                        address,
                        key.privateKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString() ?: "",
                        k1PublicKey ?: "",
                        key.privateKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: "",
                        p1PublicKey ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ErrorReporter.reportWithMixpanel(BackupError.PRIVATE_KEY_RESTORE_FAILED, e)
            loadingLiveData.postValue(false)
            toast(msgRes = R.string.restore_failed)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun importSeedPhrase(mnemonic: String, passphrase: String, address: String) {
        loadingLiveData.postValue(true)
        restoreType = RestoreType.SEED_PHRASE
        try {
            ioScope {
                val storage = getStorage()
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = mnemonic,
                    passphrase = passphrase,
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )

                // Create a new wallet using the seed phrase key
                WalletFactory.createKeyWallet(
                    seedPhraseKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                )

                // Initialize WalletManager with the new wallet
                WalletManager.init()

                val p1PublicKey = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04")
                val k1PublicKey = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()?.removePrefix("04")

                if (address.isEmpty()) {
                    checkIsQueryAddress(
                        seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString() ?: "",
                        k1PublicKey ?: "",
                        seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: "",
                        p1PublicKey ?: ""
                    )
                } else {
                    queryAddressPublicKey(
                        address,
                        seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString() ?: "",
                        k1PublicKey ?: "",
                        seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: "",
                        p1PublicKey ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ErrorReporter.reportWithMixpanel(BackupError.SEED_PHRASE_RESTORE_FAILED, e)
            loadingLiveData.postValue(false)
            toast(msgRes = R.string.restore_failed)
        }
    }

    private suspend fun queryAddressPublicKey(
        address: String, k1PrivateKey: String, k1PublicKey: String,
        p1PrivateKey: String, p1PublicKey: String
    ) {
        try {
            logd("KeyStoreRestoreViewModel", "Querying address: $address")
            val account = FlowCadenceApi.getAccount(address)
            logd("KeyStoreRestoreViewModel", "Got account: $account")

            if (checkIsMatched(account, k1PrivateKey, k1PublicKey)) {
                logd("KeyStoreRestoreViewModel", "K1 key matched")
                return
            } else if (checkIsMatched(account, p1PrivateKey, p1PublicKey)) {
                logd("KeyStoreRestoreViewModel", "P256 key matched")
                return
            } else {
                logd("KeyStoreRestoreViewModel", "No key matched, checking query address")
                checkIsQueryAddress(k1PrivateKey, k1PublicKey, p1PrivateKey, p1PublicKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logd("KeyStoreRestoreViewModel", "Error querying address: ${e.message}")
            ErrorReporter.reportWithMixpanel(WalletError.QUERY_PUBLIC_KEY_FAILED, e)
            checkIsQueryAddress(k1PrivateKey, k1PublicKey, p1PrivateKey, p1PublicKey)
        }
    }

    private suspend fun checkIsQueryAddress(
        k1PrivateKey: String,
        k1PublicKey: String,
        p1PrivateKey: String,
        p1PublicKey: String
    ) {
        logd("KeyStoreRestoreViewModel", "Checking query address with K1 public key: $k1PublicKey")
        if (checkIsLogin(k1PrivateKey, k1PublicKey, SigningAlgorithm.ECDSA_secp256k1)) {
            logd("KeyStoreRestoreViewModel", "K1 key login successful")
            return
        } else {
            logd("KeyStoreRestoreViewModel", "K1 key login failed, trying P256")
            if (checkIsLogin(p1PrivateKey, p1PublicKey, SigningAlgorithm.ECDSA_P256)) {
                logd("KeyStoreRestoreViewModel", "P256 key login successful")
                return
            } else {
                logd("KeyStoreRestoreViewModel", "Both keys failed, querying address with public keys")
                queryAddressWithPublicKey(
                    k1PrivateKey, k1PublicKey, p1PrivateKey, p1PublicKey
                )
            }
        }
    }

    private fun checkIsMatched(
        account: org.onflow.flow.models.Account,
        privateKey: String,
        publicKey: String
    ): Boolean {
        logd("KeyStoreRestoreViewModel", "Checking if key matches account: $account")
        val accountKey = account.keys?.lastOrNull { it.publicKey == publicKey }
        return accountKey?.run {
            logd("KeyStoreRestoreViewModel", "Found matching key: $this")
            checkAndImportKeyStoreAddress(
                this,
                KeystoreAddress(
                    address = account.address,
                    publicKey = publicKey,
                    privateKey = privateKey,
                    keyId = this.index.toInt(),
                    weight = weight.toInt(),
                    hashAlgo = this.hashingAlgorithm.cadenceIndex,
                    signAlgo = this.signingAlgorithm.cadenceIndex
                )
            )
            true
        } ?: run {
            logd("KeyStoreRestoreViewModel", "No matching key found")
            false
        }
    }

    private suspend fun queryAddressWithPublicKey(
        k1PrivateKey: String, k1PublicKey: String,
        p1PrivateKey: String, p1PublicKey: String
    ) {
        logd("KeyStoreRestoreViewModel", "Querying address with public keys")
        addressList.clear()

        logd("KeyStoreRestoreViewModel", "Querying K1 key: $k1PublicKey")
        val k1Response = queryService.queryAddress(k1PublicKey)
        logd("KeyStoreRestoreViewModel", "K1 response: $k1Response")

        if (k1Response.publicKey == k1PublicKey && k1Response.accounts.isNotEmpty()) {
            logd("KeyStoreRestoreViewModel", "Found K1 accounts: ${k1Response.accounts}")
            addressList.addAll(k1Response.accounts.map {
                KeystoreAddress(
                    address = it.address,
                    publicKey = k1PublicKey,
                    privateKey = k1PrivateKey,
                    keyId = it.keyId,
                    weight = it.weight,
                    hashAlgo = it.hashAlgo,
                    signAlgo = it.signAlgo
                )
            }.toList())
        }

        logd("KeyStoreRestoreViewModel", "Querying P256 key: $p1PublicKey")
        val p1Response = queryService.queryAddress(p1PublicKey)
        logd("KeyStoreRestoreViewModel", "P256 response: $p1Response")

        if (p1Response.publicKey == p1PublicKey && p1Response.accounts.isNotEmpty()) {
            logd("KeyStoreRestoreViewModel", "Found P256 accounts: ${p1Response.accounts}")
            addressList.addAll(p1Response.accounts.map {
                KeystoreAddress(
                    address = it.address,
                    publicKey = p1PublicKey,
                    privateKey = p1PrivateKey,
                    keyId = it.keyId,
                    weight = it.weight,
                    hashAlgo = it.hashAlgo,
                    signAlgo = it.signAlgo
                )
            }.toList())
        }

        loadingLiveData.postValue(false)
        addressListLiveData.postValue(addressList)
        logd("KeyStoreRestoreViewModel", "Final address list: $addressList")
    }

    private suspend fun checkIsLogin(
        privateKey: String,
        publicKey: String,
        signAlgo: SigningAlgorithm
    ): Boolean {
        try {
            logd("KeyStoreRestoreViewModel", "Checking login for public key: $publicKey")
            val response = apiService.checkKeystorePublicKeyImport(publicKey)
            logd("KeyStoreRestoreViewModel", "Check import response: $response")

            // If status is 200, it means key is NOT imported (available for import)
            // so it's not a "login" scenario in the sense of an existing registered key.
            if (response.status == 200) {
                logd("KeyStoreRestoreViewModel", "Key not imported yet (available for import based on checkKeystorePublicKeyImport status 200)")
                return false // Not an existing key to log in with
            }
            // For any other status from checkKeystorePublicKeyImport, assume it's not a direct login path either.
            // The 409 is handled by the catch block.
            logd("KeyStoreRestoreViewModel", "checkKeystorePublicKeyImport status was not 200 (was ${response.status}). Not proceeding to login via this path.")
            return false
        } catch (e: Exception) {
            logd("KeyStoreRestoreViewModel", "Error checking login: ${e.message}")
            (e as? HttpException)?.let {
                if (it.code() == 409) {
                    logd("KeyStoreRestoreViewModel", "Key already exists (409), attempting loginWithPrivateKey")
                    // loginWithPrivateKey handles its own UI and async flow.
                    // We return true here to indicate that a login attempt has been initiated.
                    // The success/failure of that login is handled by loginWithPrivateKey's own callback.
                    loginWithPrivateKey(privateKey, publicKey, signAlgo) { isLoginSuccess ->
                        // This callback is primarily for any specific actions needed *immediately after* 
                        // loginWithPrivateKey completes, if checkIsLogin itself needed to do more.
                        // In the current structure, loginWithPrivateKey handles navigation, so this callback here
                        // might not need to do much more than log.
                        if (isLoginSuccess) {
                            logd("KeyStoreRestoreViewModel", "checkIsLogin: loginWithPrivateKey reported success for $publicKey")
                        } else {
                            logd("KeyStoreRestoreViewModel", "checkIsLogin: loginWithPrivateKey reported failure for $publicKey")
                            // If loginWithPrivateKey fails, its own toast should appear.
                            // The false return from checkIsLogin (if this path was taken) will allow subsequent checks.
                            // This part is tricky: if login attempt was made, should checkIsLogin still return false to allow other checks?
                            // Or should it be true because an attempt was made?
                            // For now, if login is ATTEMPTED, let its own flow dictate UI.
                            // The original code returned true from here.
                        }
                    }
                    return true // Indicate login process was initiated.
                }
                logd("KeyStoreRestoreViewModel", "checkKeystorePublicKeyImport HTTP error was not 409 (was ${it.code()}).")
                return false // Not a 409, so not a login attempt via this path
            } ?: run {
                logd("KeyStoreRestoreViewModel", "checkKeystorePublicKeyImport error was not HTTP.")
                return false // Non-HTTP error, not a login attempt via this path
            }
        }
    }

    private fun checkAndImportKeyStoreAddress(accountKey: AccountPublicKey, keystoreAddress: KeystoreAddress) {
        currentKeyStoreAddress = keystoreAddress
        loadingLiveData.postValue(true)
        ioScope {
            try {
                val response = apiService.checkKeystorePublicKeyImport(keystoreAddress.publicKey)
                if (response.status == 200) {
                    loadingLiveData.postValue(false)
                    changeOption(KeyStoreOption.CREATE_USERNAME)
                }
            } catch (e: Exception) {
                (e as? HttpException)?.let {
                    if (it.code() == 409) {
                        loginWithKeyStoreAddress(accountKey, keystoreAddress)
                    }
                } ?: run {
                    loadingLiveData.postValue(false)
                    toast(msgRes = R.string.restore_failed)
                }
            }
        }
    }

    fun importKeyStoreAddress(keystoreAddress: KeystoreAddress) {
        currentKeyStoreAddress = keystoreAddress
        loadingLiveData.postValue(false)
        changeOption(KeyStoreOption.CREATE_USERNAME)
    }

    fun importWithUsername(username: String) {
        if (currentKeyStoreAddress == null || username.isEmpty()
            || currentKeyStoreAddress?.address.isNullOrEmpty()
            || currentKeyStoreAddress?.address == "0x"
        ) {
            loadingLiveData.postValue(false)
            toast(msgRes = R.string.login_failure)
            return
        }
        if (WalletManager.wallet()?.walletAddress() == currentKeyStoreAddress?.address) {
            toast(msgRes = R.string.wallet_already_logged_in, duration = Toast.LENGTH_LONG)
            val activity = BaseActivity.getCurrentActivity() ?: return
            activity.finish()
            return
        }
        val account = AccountManager.list()
            .firstOrNull { it.wallet?.walletAddress() == currentKeyStoreAddress?.address }
        if (account != null) {
            AccountManager.switch(account) {}
            return
        }
        ioScope {
            val cryptoProvider =
                PrivateKeyStoreCryptoProvider(Gson().toJson(currentKeyStoreAddress))
            val activity = BaseActivity.getCurrentActivity() ?: return@ioScope
            val currentKey = currentKeyStoreAddress?.run {
                val flowAccount = FlowCadenceApi.getAccount(this.address)
                flowAccount.keys?.find { it.publicKey == publicKey }
            } ?: run {
                toast(msgRes = R.string.login_failure)
                activity.finish()
                return@ioScope
            }
            if (currentKey.weight.toInt() < 1000) {
                toast(msgRes = R.string.restore_failure_insufficient_weight)
                activity.finish()
                return@ioScope
            }
            if (currentKey.revoked) {
                toast(msgRes = R.string.restore_failure_key_revoked)
                activity.finish()
                return@ioScope
            }
            import(cryptoProvider, username) { isSuccess ->
                uiScope {
                    loadingLiveData.postValue(false)
                    if (isSuccess) {
                        CryptoProviderManager.clear()
                        delay(200)
                        MixpanelManager.accountRestore(cryptoProvider.getAddress(), restoreType)
                        MainActivity.relaunch(activity, clearTop = true)
                    } else {
                        toast(msgRes = R.string.login_failure)
                        activity.finish()
                    }
                }
            }
        }
    }

    private fun import(
        cryptoProvider: PrivateKeyStoreCryptoProvider, username: String, callback:
            (isSuccess: Boolean) -> Unit
    ) {
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
                        val resp = service.import(
                            ImportRequest(
                                address = cryptoProvider.getAddress(),
                                username = username,
                                accountKey = AccountKey(
                                    publicKey = cryptoProvider.getPublicKey(),
                                    hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                                    signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
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
                                    setBackupManually()
                                    ioScope {
                                        AccountManager.add(
                                            Account(
                                                userInfo = service.userInfo().data,
                                                keyStoreInfo = cryptoProvider.getKeyStoreInfo()
                                            )
                                        )
                                        WalletManager.init()
                                        CryptoProviderManager.clear()
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
                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_IMPORT_FAILED, catching.exceptionOrNull())
                        loge(catching.exceptionOrNull())
                        callback.invoke(false)
                    }
                }
            }
        }
    }

    private fun loginWithPrivateKey(
        privateKey: String,
        publicKey: String,
        signAlgo: SigningAlgorithm,
        loginProcessCallback: (isSuccess: Boolean) -> Unit
    ) {
        logd("KeyStoreRestoreViewModel", "Starting login with private key")
        ioScope {
            getFirebaseUid { uid ->
                if (uid.isNullOrBlank()) {
                    logd("KeyStoreRestoreViewModel", "No Firebase UID found")
                    loginProcessCallback.invoke(false)
                    return@getFirebaseUid
                }
                logd("KeyStoreRestoreViewModel", "Got Firebase UID: $uid")

                runBlocking {
                    val catching = runCatching {
                        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                        val service = retrofit().create(ApiService::class.java)
                        val jwt = getFirebaseJwt()

                        val newSignature = getSignature(jwt, privateKey, HashingAlgorithm.SHA2_256, signAlgo)
                        val formattedPublicKey = if (publicKey.startsWith("04")) publicKey.substring(2) else publicKey
                        val loginRequest = LoginRequest(
                            signature = newSignature,
                            accountKey = AccountKey(
                                publicKey = formattedPublicKey,
                                hashAlgo = HashingAlgorithm.SHA2_256.cadenceIndex,
                                signAlgo = signAlgo.cadenceIndex
                            ),
                            deviceInfo = deviceInfoRequest
                        )
                        val resp = service.login(loginRequest)

                        if (resp.data?.customToken.isNullOrBlank()) {
                            logd("KeyStoreRestoreViewModel", "No custom token in response")
                            loginProcessCallback.invoke(false)
                            return@runCatching
                        }

                        logd("KeyStoreRestoreViewModel", "Starting Firebase login with custom token")
                        firebaseLogin(resp.data?.customToken!!) { isFirebaseSuccess ->
                            logd("KeyStoreRestoreViewModel", "Firebase login result: $isFirebaseSuccess")
                            if (isFirebaseSuccess) {
                                logd("KeyStoreRestoreViewModel", "Setting registered and backup manually flags")
                                setRegistered()
                                setBackupManually()

                                ioScope {
                                    var finalWalletAddress: String? = null
                                    var accountSuccessfullyAdded = false
                                    try {
                                        logd("KeyStoreRestoreViewModel", "Getting user info from service")
                                        val userInfo = service.userInfo().data
                                        logd("KeyStoreRestoreViewModel", "Got user info: $userInfo")

                                        logd("KeyStoreRestoreViewModel", "Getting wallet list")
                                        val walletListResponse = service.getWalletList()
                                        logd("KeyStoreRestoreViewModel", "Got wallet list: $walletListResponse")

                                        finalWalletAddress = walletListResponse.data?.walletAddress()

                                        if (finalWalletAddress.isNullOrBlank()) {
                                            logd("KeyStoreRestoreViewModel", "WARNING: No wallet address found in wallet list after login.")
                                            loginProcessCallback.invoke(false)
                                            return@ioScope
                                        }
                                        logd("KeyStoreRestoreViewModel", "Wallet address from list: '$finalWalletAddress'")

                                        var determinedKeyId = 0
                                        var determinedWeight = 1000
                                        var determinedHashAlgo = org.onflow.flow.models.HashingAlgorithm.SHA2_256.cadenceIndex
                                        var determinedSignAlgo = signAlgo.cadenceIndex

                                        try {
                                            logd("KeyStoreRestoreViewModel", "Login: Fetching on-chain account details for $finalWalletAddress to refine key algorithms.")
                                            val onChainAccount = FlowCadenceApi.getAccount(finalWalletAddress)
                                            val matchedOnChainKey = onChainAccount.keys?.find { key ->
                                                val keyPubHex = key.publicKey.removePrefix("0x").lowercase()
                                                val formattedPubKeyHex = formattedPublicKey.removePrefix("0x").lowercase()
                                                keyPubHex == formattedPubKeyHex || keyPubHex == formattedPubKeyHex.removePrefix("04")
                                            }

                                            if (matchedOnChainKey != null) {
                                                determinedKeyId = matchedOnChainKey.index.toInt()
                                                determinedWeight = matchedOnChainKey.weight.toInt()
                                                determinedHashAlgo = matchedOnChainKey.hashingAlgorithm.cadenceIndex
                                                determinedSignAlgo = matchedOnChainKey.signingAlgorithm.cadenceIndex
                                                logd("KeyStoreRestoreViewModel", "Login: Matched on-chain key for $finalWalletAddress: index=${matchedOnChainKey.index}, signAlgo=${matchedOnChainKey.signingAlgorithm}, hashAlgo=${matchedOnChainKey.hashingAlgorithm}. Using these for KeystoreAddress.")
                                            } else {
                                                logd("KeyStoreRestoreViewModel", "Login: Could NOT find matching on-chain key for $formattedPublicKey on account $finalWalletAddress. Using login-derived signAlgo ($determinedSignAlgo) and default hashAlgo ($determinedHashAlgo) for KeystoreAddress.")
                                            }
                                        } catch (e: Exception) {
                                            loge("KeyStoreRestoreViewModel", "Login: Error fetching on-chain details for $finalWalletAddress to refine key algorithms: ${e.message}. Using login-derived signAlgo ($determinedSignAlgo) and default hashAlgo ($determinedHashAlgo) for KeystoreAddress.")
                                        }

                                        logd("KeyStoreRestoreViewModel", "Creating KeystoreAddress with: address=$finalWalletAddress, keyId=$determinedKeyId, signAlgo=$determinedSignAlgo, hashAlgo=$determinedHashAlgo")
                                        val keystoreAddress = KeystoreAddress(
                                            address = finalWalletAddress, 
                                            publicKey = formattedPublicKey,
                                            privateKey = privateKey, 
                                            keyId = determinedKeyId,
                                            weight = determinedWeight,
                                            hashAlgo = determinedHashAlgo,
                                            signAlgo = determinedSignAlgo
                                        )
                                        logd("KeyStoreRestoreViewModel", "Created KeystoreAddress: $keystoreAddress")

                                        val userAccount = Account(
                                            userInfo = userInfo,
                                            keyStoreInfo = Gson().toJson(keystoreAddress)
                                        )
                                        AccountManager.add(userAccount)
                                        WalletManager.init()
                                        CryptoProviderManager.clear()
                                        MixpanelManager.accountRestore(finalWalletAddress, restoreType)
                                        clearUserCache()
                                        accountSuccessfullyAdded = true
                                        logd("KeyStoreRestoreViewModel", "Post-login process completed successfully.")

                                    } catch (e: Exception) {
                                        logd("KeyStoreRestoreViewModel", "Error during post-login data processing: ${e.message}")
                                        loge(e) 
                                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, e) 
                                        // accountSuccessfullyAdded remains false
                                    }

                                    if (accountSuccessfullyAdded) {
                                        delay(500) 
                                        loadingLiveData.postValue(false)
                                        val activity = BaseActivity.getCurrentActivity()
                                        if (activity != null) {
                                            MainActivity.relaunch(activity, clearTop = true)
                                        }
                                        loginProcessCallback.invoke(true) 
                                    } else {
                                        loadingLiveData.postValue(false)
                                        toast(msgRes = R.string.login_failure) 
                                        loginProcessCallback.invoke(false) 
                                    }
                                }
                            } else {
                                logd("KeyStoreRestoreViewModel", "Firebase login failed")
                                loadingLiveData.postValue(false)
                                loginProcessCallback.invoke(false)
                            }
                        }
                    }

                    if (catching.isFailure) {
                        val error = catching.exceptionOrNull()
                        logd("KeyStoreRestoreViewModel", "Login failed: ${error?.message}")
                        loge(error)
                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, error)
                        loadingLiveData.postValue(false)
                    }
                }
            }
        }
    }

    private fun loginWithKeyStoreAddress(flowAccountKey: AccountPublicKey, keystoreAddress: KeystoreAddress) {
        if (WalletManager.wallet()?.walletAddress() == keystoreAddress.address) {
            toast(msgRes = R.string.wallet_already_logged_in, duration = Toast.LENGTH_LONG)
            val activity = BaseActivity.getCurrentActivity() ?: return
            activity.finish()
            return
        }
        val account = AccountManager.list()
            .firstOrNull { it.wallet?.walletAddress() == keystoreAddress.address }
        if (account != null) {
            AccountManager.switch(account) {}
            return
        }
        ioScope {
            val cryptoProvider = PrivateKeyStoreCryptoProvider(Gson().toJson(keystoreAddress))
            val activity = BaseActivity.getCurrentActivity() ?: return@ioScope
            if (flowAccountKey.weight.toInt() < 1000) {
                toast(msgRes = R.string.restore_failure_insufficient_weight)
                activity.finish()
                return@ioScope
            }
            if (flowAccountKey.revoked) {
                toast(msgRes = R.string.restore_failure_key_revoked)
                activity.finish()
                return@ioScope
            }
            login(cryptoProvider) { isSuccess ->
                uiScope {
                    loadingLiveData.postValue(false)
                    if (isSuccess) {
                        CryptoProviderManager.clear()
                        MixpanelManager.accountRestore(cryptoProvider.getAddress(), restoreType)
                        delay(200)
                        MainActivity.relaunch(activity, clearTop = true)
                    } else {
                        toast(msgRes = R.string.login_failure)
                        activity.finish()
                    }
                }
            }
        }
    }

    private fun login(
        cryptoProvider: PrivateKeyStoreCryptoProvider,
        callback: (isSuccess: Boolean) -> Unit
    ) {
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
                        val resp = service.login(
                            LoginRequest(
                                signature = cryptoProvider.getUserSignature(
                                    getFirebaseJwt()
                                ),
                                accountKey = AccountKey(
                                    publicKey = cryptoProvider.getPublicKey(),
                                    hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
                                    signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
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
                                    setBackupManually()
                                    ioScope {
                                        AccountManager.add(
                                            Account(
                                                userInfo = service.userInfo().data,
                                                keyStoreInfo = cryptoProvider.getKeyStoreInfo()
                                            )
                                        )
                                        WalletManager.init()
                                        CryptoProviderManager.clear()
                                        MixpanelManager.accountRestore(
                                            cryptoProvider.getAddress(),
                                            restoreType
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
                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, catching.exceptionOrNull())
                        loge(catching.exceptionOrNull())
                        callback.invoke(false)
                    }
                }
            }
        }
    }

    private suspend fun getSignature(
        jwt: String,
        privateKey: String,
        hashAlgo: HashingAlgorithm,
        signAlgo: SigningAlgorithm
    ): String {
        logd("KeyStoreRestoreViewModel", "Generating signature using wallet module PrivateKey")
        logd("KeyStoreRestoreViewModel", "Hash algorithm: $hashAlgo, Sign algorithm: $signAlgo")
        
        val storage = getStorage()
        
        // Create PrivateKey instance from wallet module
        val key = com.flow.wallet.keys.PrivateKey.create(storage).apply {
            val keyBytes = privateKey.hexToBytes()
            logd("KeyStoreRestoreViewModel", "Importing private key with ${keyBytes.size} bytes")
            importPrivateKey(keyBytes, com.flow.wallet.keys.KeyFormat.RAW)
        }

        // Use the wallet module's domain tag and signing
        val domainTagBytes = DomainTag.User.bytes
        val jwtBytes = jwt.encodeToByteArray()
        val dataToSign = domainTagBytes + jwtBytes
        
        logd("KeyStoreRestoreViewModel", "Data to sign length: ${dataToSign.size}")
        
        // Sign using the wallet module's signing method
        val signatureBytes = key.sign(dataToSign, signAlgo, hashAlgo)
        
        // Remove recovery ID if present (wallet module includes it, but server expects standard 64-byte signature)
        val finalSignatureBytes = if (signatureBytes.size == 65) {
            logd("KeyStoreRestoreViewModel", "Removing recovery ID from 65-byte signature")
            signatureBytes.copyOfRange(0, 64) // Remove the last byte (recovery ID)
        } else {
            logd("KeyStoreRestoreViewModel", "Using signature as-is (${signatureBytes.size} bytes)")
            signatureBytes
        }
        
        val signature = finalSignatureBytes.joinToString("") { "%02x".format(it) }
        
        logd("KeyStoreRestoreViewModel", "Generated signature: $signature")
        logd("KeyStoreRestoreViewModel", "Signature length: ${signature.length}")
        
        return signature
    }
}