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
import com.flowfoundation.wallet.manager.flowjvm.transaction.checkSecurityProvider
import com.flowfoundation.wallet.manager.flowjvm.transaction.updateSecurityProvider
import wallet.core.jni.StoredKey
import com.flowfoundation.wallet.utils.Env.getStorage
import org.onflow.flow.models.DomainTag
import com.flowfoundation.wallet.manager.wallet.walletAddress
import org.onflow.flow.ChainId
import com.flowfoundation.wallet.utils.logd
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import com.nftco.flow.sdk.crypto.Crypto
import org.onflow.flow.models.bytesToHex
import org.onflow.flow.models.hexToBytes

class KeyStoreRestoreViewModel : ViewModel() {
    private val TAG = KeyStoreRestoreViewModel::class.java.simpleName

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
            ioScope {
                val storage = getStorage()
                val keyStore = StoredKey.importJSON(json.toByteArray())
                val decryptedKey = keyStore.decryptPrivateKey(password.toByteArray())
                val key = PrivateKey.create(storage).apply {
                    importPrivateKey(decryptedKey, KeyFormat.RAW)
                }
                
                // Create a new wallet using the private key
                val wallet = WalletFactory.createKeyWallet(
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
                    importPrivateKey(privateKey.toByteArray(), KeyFormat.HEX)
                }
                
                // Create a new wallet using the private key
                val wallet = WalletFactory.createKeyWallet(
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
        logd(TAG, "importSeedPhrase called with mnemonic length: ${mnemonic.length}, passphrase length: ${passphrase.length}, address: $address")
        loadingLiveData.postValue(true)
        restoreType = RestoreType.SEED_PHRASE
        try {
            ioScope {
                logd(TAG, "Creating storage and seed phrase key")
                val storage = getStorage()
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = mnemonic,
                    passphrase = passphrase,
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                
                logd(TAG, "Creating wallet with seed phrase key")
                // Create a new wallet using the seed phrase key
                val wallet = WalletFactory.createKeyWallet(
                    seedPhraseKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                )
                logd(TAG, "Wallet created with address: ${wallet.walletAddress()}")
                
                logd(TAG, "Initializing WalletManager")
                // Initialize WalletManager with the new wallet
                WalletManager.init()
                
                logd(TAG, "Getting public keys")
                val p1PublicKey = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04")
                val k1PublicKey = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()?.removePrefix("04")
                logd(TAG, "P1 Public Key: $p1PublicKey")
                logd(TAG, "K1 Public Key: $k1PublicKey")

                if (address.isEmpty()) {
                    logd(TAG, "Address is empty, checking query address")
                    checkIsQueryAddress(
                        seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString() ?: "",
                        k1PublicKey ?: "",
                        seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_P256)?.toHexString() ?: "",
                        p1PublicKey ?: ""
                    )
                } else {
                    logd(TAG, "Address provided, querying address public key")
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
            logd(TAG, "Error in importSeedPhrase: ${e.message}")
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
            val account = FlowCadenceApi.getAccount(address)
            if (checkIsMatched(account, k1PrivateKey, k1PublicKey)) {
                return
            } else if (checkIsMatched(account, p1PrivateKey, p1PublicKey)) {
                return
            } else {
                checkIsQueryAddress(k1PrivateKey, k1PublicKey, p1PrivateKey, p1PublicKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        logd(TAG, "checkIsQueryAddress called")
        if (checkIsLogin(k1PrivateKey, k1PublicKey, SigningAlgorithm.ECDSA_secp256k1)) {
            logd(TAG, "K1 login successful")
            return
        } else if (checkIsLogin(p1PrivateKey, p1PublicKey, SigningAlgorithm.ECDSA_P256)) {
            logd(TAG, "P1 login successful")
            return
        } else {
            logd(TAG, "No login successful, querying address with public key")
            queryAddressWithPublicKey(
                k1PrivateKey, k1PublicKey, p1PrivateKey, p1PublicKey
            )
        }
    }

    private fun checkIsMatched(
        account: org.onflow.flow.models.Account,
        privateKey: String,
        publicKey: String
    ): Boolean {
        val accountKey = account.keys?.lastOrNull { it.publicKey == publicKey }
        return accountKey?.run {
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
        } ?: false
    }

    private suspend fun queryAddressWithPublicKey(
        k1PrivateKey: String, k1PublicKey: String,
        p1PrivateKey: String, p1PublicKey: String
    ) {
        addressList.clear()
        val k1Response = queryService.queryAddress(k1PublicKey)
        if (k1Response.publicKey == k1PublicKey && k1Response.accounts.isNotEmpty()) {
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
        val p1Response = queryService.queryAddress(p1PublicKey)
        if (p1Response.publicKey == p1PublicKey && p1Response.accounts.isNotEmpty()) {
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
    }

    private suspend fun checkIsLogin(
        privateKey: String,
        publicKey: String,
        signAlgo: SigningAlgorithm
    ): Boolean {
        logd(TAG, "checkIsLogin called for algorithm: $signAlgo")
        try {
            logd(TAG, "Calling checkKeystorePublicKeyImport with public key: ${publicKey.take(10)}...")
            val response = apiService.checkKeystorePublicKeyImport(publicKey)
            logd(TAG, "checkKeystorePublicKeyImport response status: ${response.status}")
            if (response.status == 200) {
                logd(TAG, "Response status 200, returning false")
                return false
            }
            logd(TAG, "Response status not 200, returning false")
            return false
        } catch (e: Exception) {
            logd(TAG, "Error in checkIsLogin: ${e.message}")
            (e as? HttpException)?.let {
                logd(TAG, "HTTP Exception with code: ${it.code()}")
                if (it.code() == 409) {
                    logd(TAG, "HTTP 409 received, attempting login with private key")
                    loginWithPrivateKey(privateKey, publicKey, signAlgo)
                    return true
                }
                logd(TAG, "HTTP code not 409, returning false")
                return false
            } ?: run {
                logd(TAG, "Not an HTTP Exception, returning false")
                return false
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
        signAlgo: SigningAlgorithm
    ) {
        logd(TAG, "loginWithPrivateKey called")
        ioScope {
            val activity = BaseActivity.getCurrentActivity() ?: return@ioScope
            logd(TAG, "Getting current activity")
            loginAndFetchWallet(privateKey, publicKey, signAlgo) { isSuccess, errorMsg ->
                logd(TAG, "loginAndFetchWallet callback - success: $isSuccess, error: $errorMsg")
                uiScope {
                    loadingLiveData.postValue(false)
                    if (isSuccess) {
                        logd(TAG, "Login successful, delaying and relaunching activity")
                        delay(200)
                        MainActivity.relaunch(activity, clearTop = true)
                    } else {
                        logd(TAG, "Login failed, showing error toast and finishing activity")
                        toast(msgRes = errorMsg)
                        activity.finish()
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
                            logd(TAG, "No custom token in response")
                            callback.invoke(false)
                        } else {
                            logd(TAG, "Got custom token, attempting Firebase login")
                            firebaseLogin(resp.data?.customToken!!) { isSuccess ->
                                if (isSuccess) {
                                    logd(TAG, "Firebase login successful")
                                    ioScope {
                                        try {
                                            logd(TAG, "Getting wallet list")
                                            val walletList = service.getWalletList()
                                            val wallet = walletList.data?.let { walletData ->
                                                val address = walletData.walletAddress()
                                                if (address == cryptoProvider.getAddress()) {
                                                    walletData
                                                } else null
                                            }
                                            if (wallet == null) {
                                                logd(TAG, "No matching wallet found")
                                                callback.invoke(false)
                                            } else {
                                                logd(TAG, "Found matching wallet, adding to AccountManager")
                                                AccountManager.add(
                                                    Account(
                                                        userInfo = service.userInfo().data,
                                                        keyStoreInfo = cryptoProvider.getKeyStoreInfo()
                                                    )
                                                )
                                                logd(TAG, "Account added to AccountManager")
                                                
                                                // Set the wallet address in WalletManager
                                                val walletAddress = wallet.walletAddress()
                                                logd(TAG, "Setting wallet address in WalletManager: '$walletAddress'")
                                                if (walletAddress.isNullOrBlank()) {
                                                    logd(TAG, "WARNING: Attempting to set blank wallet address")
                                                }
                                                
                                                // Clear any existing wallet state
                                                logd(TAG, "Clearing existing wallet state")
                                                WalletManager.clear()
                                                
                                                // Create a private key from the existing private key
                                                val storage = getStorage()
                                                val key = PrivateKey.create(storage).apply {
                                                    logd(TAG, "Created new PrivateKey instance")
                                                    importPrivateKey(cryptoProvider.getPrivateKey().hexToBytes(), KeyFormat.RAW)
                                                    logd(TAG, "Imported private key")
                                                }
                                                logd(TAG, "Created private key")

                                                // Create a new wallet using the private key
                                                val newWallet = WalletFactory.createKeyWallet(
                                                    key,
                                                    setOf(ChainId.Mainnet, ChainId.Testnet),
                                                    storage
                                                )
                                                logd(TAG, "Created key wallet")

                                                // Set the wallet address
                                                logd(TAG, "Selecting wallet address: '$walletAddress'")
                                                WalletManager.selectWalletAddress(walletAddress ?: "")

                                                // Initialize the wallet
                                                logd(TAG, "Initializing WalletManager")
                                                WalletManager.init()

                                                // Verify the wallet address was set correctly
                                                val currentAddress = WalletManager.selectedWalletAddress()
                                                logd(TAG, "Current wallet address after init: '$currentAddress'")

                                                if (currentAddress != walletAddress) {
                                                    logd(TAG, "WARNING: Wallet address mismatch. Expected: '$walletAddress', Got: '$currentAddress'")
                                                    // Try to set it again
                                                    WalletManager.selectWalletAddress(walletAddress ?: "")
                                                    WalletManager.init()
                                                    logd(TAG, "Retried wallet initialization. New address: '${WalletManager.selectedWalletAddress()}'")
                                                }

                                                logd(TAG, "Tracking account restore in Mixpanel")
                                                MixpanelManager.accountRestore(
                                                    newWallet.walletAddress() ?: "",
                                                    restoreType
                                                )
                                                setRegistered()
                                                setBackupManually()
                                                clearUserCache()
                                                
                                                // Ensure AccountManager is initialized
                                                AccountManager.init()
                                                
                                                // Wait a bit to ensure initialization is complete
                                                delay(500)
                                                
                                                callback.invoke(true)
                                            }
                                        } catch (e: Exception) {
                                            logd(TAG, "Error in wallet setup: ${e.message}")
                                            e.printStackTrace()
                                            callback.invoke(false)
                                        }
                                    }
                                } else {
                                    logd(TAG, "Firebase login failed")
                                    callback.invoke(false)
                                }
                            }
                        }
                    }

                    if (catching.isFailure) {
                        logd(TAG, "Error in login process: ${catching.exceptionOrNull()?.message}")
                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, catching.exceptionOrNull())
                        loge(catching.exceptionOrNull())
                        callback.invoke(false)
                    }
                }
            }
        }
    }

    private fun getSignatureOld(
        jwt: String,
        privateKey: String,
        hashAlgo: HashAlgorithm,
        signAlgo: SignatureAlgorithm
    ): String {
        checkSecurityProvider()
        updateSecurityProvider()
        return Crypto.getSigner(
            privateKey = Crypto.decodePrivateKey(
                privateKey, signAlgo
            ),
            hashAlgo = hashAlgo
        ).sign(com.nftco.flow.sdk.DomainTag.USER_DOMAIN_TAG + jwt.encodeToByteArray()).bytesToHex()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun getSignature(
        jwt: String,
        privateKey: String,
        hashAlgo: HashingAlgorithm,
        signAlgo: SigningAlgorithm
    ): String {
        logd(TAG, "getSignature called with hashAlgo: $hashAlgo, signAlgo: $signAlgo")
        val storage = getStorage()
        val key = PrivateKey.create(storage).apply {
            logd(TAG, "Importing private key in RAW format")
            importPrivateKey(privateKey.hexToBytes(), KeyFormat.RAW)
        }
        logd(TAG, "Private key imported successfully")
        val signature = key.sign(DomainTag.User.bytes + jwt.encodeToByteArray(), signAlgo, hashAlgo).toHexString()
        logd(TAG, "Signature generated successfully")
        return signature
    }

    private fun loginAndFetchWallet(
        privateKeyHex: String, publicKey: String, signAlgo: SigningAlgorithm,
        callback: (isSuccess: Boolean, errorMsg: Int) -> Unit
    ) {
        logd(TAG, "loginAndFetchWallet called")
        ioScope {
            logd(TAG, "Querying address with public key")
            val addressResponse = queryService.queryAddress(publicKey)
            logd(TAG, "Got address response with ${addressResponse.accounts.size} accounts")

            if (addressResponse.accounts.isEmpty()) {
                logd(TAG, "No accounts found for public key")
                callback.invoke(false, R.string.login_failure)
                return@ioScope
            }

            val currentKey = addressResponse.accounts.firstOrNull()?.run {
                logd(TAG, "Getting Flow account for address: $address")
                val flowAccount = FlowCadenceApi.getAccount(this.address)
                logd(TAG, "Flow account keys: ${flowAccount.keys?.map { it.publicKey }}")
                logd(TAG, "Looking for public key: $publicKey")

                // Try to find the key with the same public key, handling both 0x and 04 prefixes
                val matchingKey = flowAccount.keys?.find {
                    val keyWithoutPrefix = it.publicKey.removePrefix("0x").removePrefix("04")
                    val searchKeyWithoutPrefix = publicKey.removePrefix("0x").removePrefix("04")
                    logd(TAG, "Comparing with key: $keyWithoutPrefix")
                    logd(TAG, "Search key without prefix: $searchKeyWithoutPrefix")
                    keyWithoutPrefix.equals(searchKeyWithoutPrefix, ignoreCase = true)
                }

                if (matchingKey == null) {
                    logd(TAG, "No exact match found, trying with raw public key")
                    // If no match found, try with the raw public key
                    flowAccount.keys?.find {
                        val keyWithoutPrefix = it.publicKey.removePrefix("0x")
                        val searchKeyWithoutPrefix = publicKey.removePrefix("0x")
                        logd(TAG, "Comparing raw keys: $keyWithoutPrefix == $searchKeyWithoutPrefix")
                        keyWithoutPrefix.equals(searchKeyWithoutPrefix, ignoreCase = true)
                    }
                } else {
                    logd(TAG, "Found matching key")
                    matchingKey
                }
            } ?: run {
                logd(TAG, "No matching account found")
                callback.invoke(false, R.string.login_failure)
                return@ioScope
            }

            logd(TAG, "Checking key weight: ${currentKey.weight}")
            if (currentKey.weight.toInt() < 1000) {
                logd(TAG, "Key weight insufficient")
                callback.invoke(false, R.string.restore_failure_insufficient_weight)
                return@ioScope
            }

            logd(TAG, "Checking if key is revoked: ${currentKey.revoked}")
            if (currentKey.revoked) {
                logd(TAG, "Key is revoked")
                callback.invoke(false, R.string.restore_failure_key_revoked)
                return@ioScope
            }

            logd(TAG, "Getting Firebase UID")
            getFirebaseUid { uid ->
                if (uid.isNullOrBlank()) {
                    logd(TAG, "Firebase UID is null or blank")
                    callback.invoke(false, R.string.login_failure)
                    return@getFirebaseUid
                }

                logd(TAG, "Got Firebase UID: $uid")
                runBlocking {
                    val catching = runCatching {
                        logd(TAG, "Getting device info")
                        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                        val service = retrofit().create(ApiService::class.java)

                        logd(TAG, "Calling login API")
                        val resp = service.login(
                            LoginRequest(
                                signature = getSignatureOld(
                                    getFirebaseJwt(),
                                    privateKeyHex,
                                    when (currentKey.hashingAlgorithm) {
                                        HashingAlgorithm.SHA2_256 -> HashAlgorithm.SHA2_256
                                        HashingAlgorithm.SHA3_256 -> HashAlgorithm.SHA3_256
                                        else -> HashAlgorithm.SHA2_256
                                    },
                                    when (currentKey.signingAlgorithm) {
                                        SigningAlgorithm.ECDSA_P256 -> SignatureAlgorithm.ECDSA_P256
                                        SigningAlgorithm.ECDSA_secp256k1 -> SignatureAlgorithm.ECDSA_SECP256k1
                                        else -> SignatureAlgorithm.ECDSA_P256
                                    }
                                ),
                                accountKey = AccountKey(
                                    publicKey = publicKey,
                                    hashAlgo = currentKey.hashingAlgorithm.cadenceIndex,
                                    signAlgo = currentKey.signingAlgorithm.cadenceIndex
                                ),
                                deviceInfo = deviceInfoRequest
                            )
                        )

                        if (resp.data?.customToken.isNullOrBlank()) {
                            logd(TAG, "No custom token in response")
                            callback.invoke(false, R.string.login_failure)
                        } else {
                            logd(TAG, "Got custom token, attempting Firebase login")
                            firebaseLogin(resp.data?.customToken!!) { isSuccess ->
                                if (isSuccess) {
                                    logd(TAG, "Firebase login successful")
                                    ioScope {
                                        try {
                                            logd(TAG, "Getting wallet list")
                                            val walletList = service.getWalletList()
                                            val matchingWallet = addressResponse.accounts.firstOrNull { account ->
                                                walletList.data?.walletAddress() == account.address
                                            }
                                            if (matchingWallet == null) {
                                                logd(TAG, "No matching wallet found")
                                                callback.invoke(false, R.string.login_failure)
                                            } else {
                                                logd(TAG, "Found matching wallet, adding to AccountManager")
                                                val keystoreAddress = KeystoreAddress(
                                                    address = matchingWallet.address,
                                                    publicKey = publicKey,
                                                    privateKey = privateKeyHex,
                                                    keyId = currentKey.index.toInt(),
                                                    weight = currentKey.weight.toInt(),
                                                    hashAlgo = currentKey.hashingAlgorithm.cadenceIndex,
                                                    signAlgo = currentKey.signingAlgorithm.cadenceIndex
                                                )

                                                AccountManager.add(
                                                    Account(
                                                        userInfo = service.userInfo().data,
                                                        keyStoreInfo = Gson().toJson(keystoreAddress)
                                                    )
                                                )
                                                logd(TAG, "Account added to AccountManager")

                                                // Set the wallet address in WalletManager
                                                val walletAddress = matchingWallet.address
                                                logd(TAG, "Setting wallet address in WalletManager: '$walletAddress'")
                                                if (walletAddress.isNullOrBlank()) {
                                                    logd(TAG, "WARNING: Attempting to set blank wallet address")
                                                }

                                                // Clear any existing wallet state
                                                logd(TAG, "Clearing existing wallet state")
                                                WalletManager.clear()

                                                // Create a private key from the existing private key
                                                val storage = getStorage()
                                                val key = PrivateKey.create(storage).apply {
                                                    logd(TAG, "Created new PrivateKey instance")
                                                    importPrivateKey(privateKeyHex.hexToBytes(), KeyFormat.RAW)
                                                    logd(TAG, "Imported private key")
                                                }
                                                logd(TAG, "Created private key")
                                                
                                                // Create a new wallet using the private key
                                                val newWallet = WalletFactory.createKeyWallet(
                                                    key,
                                                    setOf(ChainId.Mainnet, ChainId.Testnet),
                                                    storage
                                                )
                                                logd(TAG, "Created key wallet")
                                                
                                                // Set the wallet address
                                                logd(TAG, "Selecting wallet address: '$walletAddress'")
                                                WalletManager.selectWalletAddress(walletAddress)
                                                
                                                // Initialize the wallet
                                                logd(TAG, "Initializing WalletManager")
                                                WalletManager.init()
                                                
                                                // Verify the wallet address was set correctly
                                                val currentAddress = WalletManager.selectedWalletAddress()
                                                logd(TAG, "Current wallet address after init: '$currentAddress'")
                                                
                                                if (currentAddress != walletAddress) {
                                                    logd(TAG, "WARNING: Wallet address mismatch. Expected: '$walletAddress', Got: '$currentAddress'")
                                                    // Try to set it again
                                                    WalletManager.selectWalletAddress(walletAddress)
                                                    WalletManager.init()
                                                    logd(TAG, "Retried wallet initialization. New address: '${WalletManager.selectedWalletAddress()}'")
                                                }
                                                
                                                logd(TAG, "Tracking account restore in Mixpanel")
                                                MixpanelManager.accountRestore(
                                                    newWallet.walletAddress() ?: "",
                                                    restoreType
                                                )
                                                setRegistered()
                                                setBackupManually()
                                                clearUserCache()
                                                
                                                // Ensure AccountManager is initialized
                                                AccountManager.init()
                                                
                                                // Wait a bit to ensure initialization is complete
                                                delay(500)
                                                
                                                callback.invoke(true, 0)
                                            }
                                        } catch (e: Exception) {
                                            logd(TAG, "Error in wallet setup: ${e.message}")
                                            e.printStackTrace()
                                            callback.invoke(false, R.string.login_failure)
                                        }
                                    }
                                } else {
                                    logd(TAG, "Firebase login failed")
                                    callback.invoke(false, R.string.login_failure)
                                }
                            }
                        }
                    }

                    if (catching.isFailure) {
                        logd(TAG, "Error in login process: ${catching.exceptionOrNull()?.message}")
                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, catching.exceptionOrNull())
                        loge(catching.exceptionOrNull())
                        callback.invoke(false, R.string.login_failure)
                    }
                }
            }
        }
    }
}