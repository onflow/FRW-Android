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
import com.flowfoundation.wallet.utils.logd
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.crypto.Crypto
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
                
                // Create a seed phrase key from the private key
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = "",  // Empty mnemonic since we're using a private key
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,  // We don't need to pass the keyPair since we're using the private key directly
                    storage = storage
                )
                logd("KeyStoreRestoreViewModel", "Created seed phrase key")
                
                // Create a new wallet using the seed phrase key
                WalletFactory.createKeyWallet(
                    seedPhraseKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                )
                logd("KeyStoreRestoreViewModel", "Created key wallet")
                
                // Initialize WalletManager with the new wallet
                WalletManager.init()
                logd("KeyStoreRestoreViewModel", "Initialized WalletManager")
                
                // Get public keys and format them correctly
                val p1PublicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()
                val k1PublicKey = key.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()
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
                    importPrivateKey(privateKey.toByteArray(), KeyFormat.RAW)
                }
                logd("KeyStoreRestoreViewModel", key)
                
                // Create a seed phrase key from the private key
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = "",  // Empty mnemonic since we're using a private key
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,  // We don't need to pass the keyPair since we're using the private key directly
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
            
            if (response.status == 200) {
                logd("KeyStoreRestoreViewModel", "Key not imported yet")
                return false
            }
            return false
        } catch (e: Exception) {
            logd("KeyStoreRestoreViewModel", "Error checking login: ${e.message}")
            (e as? HttpException)?.let {
                if (it.code() == 409) {
                    logd("KeyStoreRestoreViewModel", "Key already exists, attempting login")
                    loginWithPrivateKey(privateKey, publicKey, signAlgo)
                    return true
                }
                return false
            } ?: run {
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
        logd("KeyStoreRestoreViewModel", "Starting login with private key")
        ioScope {
            getFirebaseUid { uid ->
                if (uid.isNullOrBlank()) {
                    logd("KeyStoreRestoreViewModel", "No Firebase UID found")
                    return@getFirebaseUid
                }
                logd("KeyStoreRestoreViewModel", "Got Firebase UID: $uid")
                
                runBlocking {
                    val catching = runCatching {
                        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                        logd("KeyStoreRestoreViewModel", "Got device info: $deviceInfoRequest")
                        
                        val service = retrofit().create(ApiService::class.java)
                        val jwt = getFirebaseJwt()
                        logd("KeyStoreRestoreViewModel", "Got Firebase JWT: $jwt")
                        
                        // Convert SigningAlgorithm to SignatureAlgorithm for getSignatureOld
                        val oldSignAlgo = when (signAlgo) {
                            SigningAlgorithm.ECDSA_P256 -> SignatureAlgorithm.ECDSA_P256
                            SigningAlgorithm.ECDSA_secp256k1 -> SignatureAlgorithm.ECDSA_SECP256k1
                            else -> SignatureAlgorithm.ECDSA_P256
                        }
                        
                        val signature = getSignatureOld(jwt, privateKey, HashAlgorithm.SHA2_256, oldSignAlgo)
                        logd("KeyStoreRestoreViewModel", "Generated signature using old method: $signature")
                        
                        // Format public key - remove "04" prefix if present
                        val formattedPublicKey = if (publicKey.startsWith("04")) publicKey.substring(2) else publicKey
                        logd("KeyStoreRestoreViewModel", "Formatted public key: $formattedPublicKey")
                        
                        val loginRequest = LoginRequest(
                            signature = signature,
                            accountKey = AccountKey(
                                publicKey = formattedPublicKey,
                                hashAlgo = HashAlgorithm.SHA2_256.index,
                                signAlgo = oldSignAlgo.index
                            ),
                            deviceInfo = deviceInfoRequest
                        )
                        logd("KeyStoreRestoreViewModel", "Sending login request: $loginRequest")
                        
                        val resp = service.login(loginRequest)
                        logd("KeyStoreRestoreViewModel", "Login response: $resp")
                        
                        if (resp.data?.customToken.isNullOrBlank()) {
                            logd("KeyStoreRestoreViewModel", "No custom token in response")
                            return@runCatching
                        }
                        
                        logd("KeyStoreRestoreViewModel", "Starting Firebase login with custom token")
                        firebaseLogin(resp.data?.customToken!!) { isSuccess ->
                            logd("KeyStoreRestoreViewModel", "Firebase login result: $isSuccess")
                            if (isSuccess) {
                                logd("KeyStoreRestoreViewModel", "Setting registered and backup manually flags")
                                setRegistered()
                                setBackupManually()
                                
                                ioScope {
                                    try {
                                        logd("KeyStoreRestoreViewModel", "Getting user info from service")
                                        try {
                                            val userInfo = service.userInfo().data
                                            logd("KeyStoreRestoreViewModel", "Got user info: $userInfo")
                                            
                                            // Get wallet list to ensure we have the correct address
                                            logd("KeyStoreRestoreViewModel", "Getting wallet list")
                                            val walletList = service.getWalletList()
                                            logd("KeyStoreRestoreViewModel", "Got wallet list: $walletList")
                                            
                                            if (walletList.data == null || walletList.data?.walletAddress().isNullOrBlank()) {
                                                logd("KeyStoreRestoreViewModel", "WARNING: No wallet address found in wallet list")
                                                throw IllegalStateException("No wallet address found")
                                            }
                                            
                                            val walletAddress = walletList.data?.walletAddress()
                                            logd("KeyStoreRestoreViewModel", "Wallet address from list: '$walletAddress'")
                                            
                                            logd("KeyStoreRestoreViewModel", "Creating KeystoreAddress")
                                            val keystoreAddress = KeystoreAddress(
                                                address = walletAddress ?: "",
                                                publicKey = formattedPublicKey,
                                                privateKey = privateKey,
                                                keyId = currentKeyStoreAddress?.keyId ?: 0,
                                                weight = currentKeyStoreAddress?.weight ?: 0,
                                                hashAlgo = HashAlgorithm.SHA2_256.index,
                                                signAlgo = oldSignAlgo.index
                                            )
                                            logd("KeyStoreRestoreViewModel", "Created KeystoreAddress: $keystoreAddress")
                                            
                                            logd("KeyStoreRestoreViewModel", "Creating Account")
                                            val account = Account(
                                                userInfo = userInfo,
                                                keyStoreInfo = Gson().toJson(keystoreAddress),
                                                wallet = walletList.data
                                            )
                                            logd("KeyStoreRestoreViewModel", "Created Account: $account")
                                            
                                            logd("KeyStoreRestoreViewModel", "Adding account to AccountManager")
                                            AccountManager.add(account)
                                            logd("KeyStoreRestoreViewModel", "Account added to AccountManager")
                                            
                                            // Set the wallet address in WalletManager
                                            logd("KeyStoreRestoreViewModel", "Setting wallet address in WalletManager: '$walletAddress'")
                                            if (walletAddress.isNullOrBlank()) {
                                                logd("KeyStoreRestoreViewModel", "WARNING: Attempting to set blank wallet address")
                                            }
                                            
                                            // Clear any existing wallet state
                                            logd("KeyStoreRestoreViewModel", "Clearing existing wallet state")
                                            WalletManager.clear()
                                            
                                            // Create a seed phrase key for the wallet
                                            val seedPhraseKey = SeedPhraseKey(
                                                mnemonicString = walletAddress ?: "",
                                                passphrase = "",
                                                derivationPath = "m/44'/539'/0'/0/0",
                                                keyPair = null,
                                                storage = getStorage()
                                            )
                                            logd("KeyStoreRestoreViewModel", "Created seed phrase key")
                                            
                                            // Create a new wallet using the seed phrase key
                                            val wallet = WalletFactory.createKeyWallet(
                                                seedPhraseKey,
                                                setOf(ChainId.Mainnet, ChainId.Testnet),
                                                getStorage()
                                            )
                                            logd("KeyStoreRestoreViewModel", "Created key wallet")
                                            
                                            // Set the wallet address
                                            logd("KeyStoreRestoreViewModel", "Selecting wallet address: '$walletAddress'")
                                            WalletManager.selectWalletAddress(walletAddress ?: "")
                                            
                                            // Initialize the wallet
                                            logd("KeyStoreRestoreViewModel", "Initializing WalletManager")
                                            WalletManager.init()
                                            
                                            // Verify the wallet address was set correctly
                                            val currentAddress = WalletManager.selectedWalletAddress()
                                            logd("KeyStoreRestoreViewModel", "Current wallet address after init: '$currentAddress'")
                                            
                                            if (currentAddress != walletAddress) {
                                                logd("KeyStoreRestoreViewModel", "WARNING: Wallet address mismatch. Expected: '$walletAddress', Got: '$currentAddress'")
                                                // Try to set it again
                                                WalletManager.selectWalletAddress(walletAddress ?: "")
                                                WalletManager.init()
                                                logd("KeyStoreRestoreViewModel", "Retried wallet initialization. New address: '${WalletManager.selectedWalletAddress()}'")
                                            }
                                            
                                            logd("KeyStoreRestoreViewModel", "Tracking account restore in Mixpanel")
                                            MixpanelManager.accountRestore(
                                                walletAddress ?: "",
                                                restoreType
                                            )
                                            
                                            logd("KeyStoreRestoreViewModel", "Clearing user cache")
                                            clearUserCache()
                                            logd("KeyStoreRestoreViewModel", "User cache cleared")
                                            
                                            logd("KeyStoreRestoreViewModel", "Post-login process completed")
                                            
                                            // Add a small delay before finishing
                                            delay(500)
                                            logd("KeyStoreRestoreViewModel", "Setting loading to false")
                                            loadingLiveData.postValue(false)
                                            
                                            // Relaunch the main activity
                                            val activity = BaseActivity.getCurrentActivity()
                                            if (activity != null) {
                                                logd("KeyStoreRestoreViewModel", "Relaunching MainActivity")
                                                MainActivity.relaunch(activity, clearTop = true)
                                            } else {
                                                logd("KeyStoreRestoreViewModel", "No current activity found for relaunch")
                                            }
                                        } catch (e: Exception) {
                                            logd("KeyStoreRestoreViewModel", "Error during post-login process: ${e.message}")
                                            logd("KeyStoreRestoreViewModel", "Error stack trace: ${e.stackTraceToString()}")
                                            
                                            // Check if it's a 404 error
                                            if (e is HttpException && e.code() == 404) {
                                                logd("KeyStoreRestoreViewModel", "User info not found (404). Checking if user exists...")

                                            }
                                            
                                            ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, e)
                                            loadingLiveData.postValue(false)
                                            toast(msgRes = R.string.login_failure)
                                        }
                                    } catch (e: Exception) {
                                        logd("KeyStoreRestoreViewModel", "Error during post-login process: ${e.message}")
                                        logd("KeyStoreRestoreViewModel", "Error stack trace: ${e.stackTraceToString()}")
                                        
                                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, e)
                                        loadingLiveData.postValue(false)
                                        toast(msgRes = R.string.login_failure)
                                    }
                                }
                            } else {
                                logd("KeyStoreRestoreViewModel", "Firebase login failed")
                                loadingLiveData.postValue(false)
                            }
                        }
                    }

                    if (catching.isFailure) {
                        val error = catching.exceptionOrNull()
                        logd("KeyStoreRestoreViewModel", "Login failed: ${error?.message}")
                        logd("KeyStoreRestoreViewModel", "Error details: ${error?.stackTraceToString()}")
                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, error)
                        loge(error)
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
        logd("KeyStoreRestoreViewModel", "Getting signature for JWT")
        logd("KeyStoreRestoreViewModel", "JWT: $jwt")
        val storage = getStorage()
        logd("KeyStoreRestoreViewModel", "Got storage")
        
        val key = PrivateKey.create(storage).apply {
            logd("KeyStoreRestoreViewModel", "Created new PrivateKey instance")
            // Convert hex string to bytes
            val keyBytes = privateKey.hexToBytes()
            logd("KeyStoreRestoreViewModel", "Converted private key to bytes")
            importPrivateKey(keyBytes, KeyFormat.RAW)
            logd("KeyStoreRestoreViewModel", "Imported private key")
        }
        
        // Use the old format for domain tag and signing
        val domainTagBytes = DomainTag.User.bytes
        val jwtBytes = jwt.encodeToByteArray()
        logd("KeyStoreRestoreViewModel", "Domain tag bytes: ${domainTagBytes.joinToString("") { "%02x".format(it) }}")
        logd("KeyStoreRestoreViewModel", "JWT bytes: ${jwtBytes.joinToString("") { "%02x".format(it) }}")
        
        val dataToSign = domainTagBytes + jwtBytes
        logd("KeyStoreRestoreViewModel", "Data to sign length: ${dataToSign.size}")
        logd("KeyStoreRestoreViewModel", "Data to sign: ${dataToSign.joinToString("") { "%02x".format(it) }}")
        
        val signature = key.sign(dataToSign, signAlgo, hashAlgo).toHexString()
        logd("KeyStoreRestoreViewModel", "Generated signature: $signature")
        logd("KeyStoreRestoreViewModel", "Signature length: ${signature.length}")
        return signature
    }
}