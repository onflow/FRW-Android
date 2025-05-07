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
import com.flowfoundation.wallet.manager.flowjvm.lastBlockAccount
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
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import retrofit2.HttpException
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.utils.Env
import java.io.File
import com.flow.wallet.keys.PrivateKey
import wallet.core.jni.StoredKey
import com.flowfoundation.wallet.utils.Env.getStorage
import org.onflow.flow.models.DomainTag


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
            ioScope {
                val storage = getStorage()
                val keyStore = StoredKey.importJSON(json.toByteArray())
                val privateKey = PrivateKey(keyStore.decryptPrivateKey(password.toByteArray()), storage)
                val key = PrivateKey(privateKey, storage)
                
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
        if (checkIsLogin(k1PrivateKey, k1PublicKey, SigningAlgorithm.ECDSA_secp256k1)) {
            return
        } else if (checkIsLogin(p1PrivateKey, p1PublicKey, SigningAlgorithm.ECDSA_P256)) {
            return
        } else {
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
        try {
            val response = apiService.checkKeystorePublicKeyImport(publicKey)
            if (response.status == 200) {
                return false
            }
            return false
        } catch (e: Exception) {
            (e as? HttpException)?.let {
                if (it.code() == 409) {
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
                FlowAddress(this.address).lastBlockAccount().keys?.find { it.publicKey == publicKey }
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
        ioScope {
            val activity = BaseActivity.getCurrentActivity() ?: return@ioScope
            loginAndFetchWallet(privateKey, publicKey, signAlgo) { isSuccess, errorMsg ->
                uiScope {
                    loadingLiveData.postValue(false)
                    if (isSuccess) {
                        delay(200)
                        MainActivity.relaunch(activity, clearTop = true)
                    } else {
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

    private fun loginAndFetchWallet(
        privateKey: String, publicKey: String, signAlgo: SigningAlgorithm,
        callback: (isSuccess: Boolean, errorMsg: Int) -> Unit
    ) {
        ioScope {
            val addressResponse = queryService.queryAddress(publicKey)
            val currentKey = addressResponse.accounts.firstOrNull()?.run {
                FlowAddress(this.address).lastBlockAccount().keys?.find { it.publicKey == publicKey }
            } ?: run {
                callback.invoke(false, R.string.login_failure)
                return@ioScope
            }
            if (currentKey.weight.toInt() < 1000) {
                callback.invoke(false, R.string.restore_failure_insufficient_weight)
                return@ioScope
            }
            if (currentKey.revoked) {
                callback.invoke(false, R.string.restore_failure_key_revoked)
                return@ioScope
            }
            getFirebaseUid { uid ->
                if (uid.isNullOrBlank()) {
                    callback.invoke(false, R.string.login_failure)
                    return@getFirebaseUid
                }
                runBlocking {
                    val catching = runCatching {
                        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                        val service = retrofit().create(ApiService::class.java)
                        val resp = service.login(
                            LoginRequest(
                                signature = getSignature(
                                    getFirebaseJwt(), privateKey, currentKey.hashingAlgorithm, signAlgo
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
                            callback.invoke(false, R.string.login_failure)
                        } else {
                            firebaseLogin(resp.data?.customToken!!) { isSuccess ->
                                if (isSuccess) {
                                    ioScope {
                                        try {
                                            val walletData = service.getWalletList()
                                            val wallet = addressResponse.accounts.firstOrNull {
                                                walletData.data?.walletAddress() == it.address
                                            }
                                            if (wallet == null) {
                                                callback.invoke(false, R.string.login_failure)
                                            } else {
                                                AccountManager.add(
                                                    Account(
                                                        userInfo = service.userInfo().data,
                                                        keyStoreInfo = Gson().toJson(
                                                            KeystoreAddress(
                                                                address = wallet.address,
                                                                publicKey = publicKey,
                                                                privateKey = privateKey,
                                                                keyId = wallet.keyId,
                                                                weight = wallet.weight,
                                                                hashAlgo = wallet.hashAlgo,
                                                                signAlgo = wallet.signAlgo
                                                            )
                                                        )
                                                    )
                                                )
                                                MixpanelManager.accountRestore(
                                                    wallet.address,
                                                    restoreType
                                                )
                                                setRegistered()
                                                setBackupManually()
                                                clearUserCache()
                                                callback.invoke(true, 0)
                                            }
                                        } catch (e: Exception) {
                                            callback.invoke(false, R.string.login_failure)
                                        }
                                    }
                                } else {
                                    callback.invoke(false, R.string.login_failure)
                                }
                            }
                        }
                    }

                    if (catching.isFailure) {
                        ErrorReporter.reportWithMixpanel(BackupError.RESTORE_LOGIN_FAILED, catching.exceptionOrNull())
                        loge(catching.exceptionOrNull())
                        callback.invoke(false, R.string.login_failure)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun getSignature(
        jwt: String,
        privateKey: String,
        hashAlgo: HashingAlgorithm,
        signAlgo: SigningAlgorithm
    ): String {
        val storage = getStorage()
        val key = PrivateKey.create(storage).apply {
            importPrivateKey(privateKey.toByteArray(), KeyFormat.HEX)
        }
        return key.sign(DomainTag.User.bytes + jwt.encodeToByteArray(), signAlgo, hashAlgo).toHexString()
    }
}