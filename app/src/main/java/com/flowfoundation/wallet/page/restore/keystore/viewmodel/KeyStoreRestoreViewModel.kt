package com.flowfoundation.wallet.page.restore.keystore.viewmodel

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.flowjvm.FlowApi
import com.flowfoundation.wallet.manager.flowjvm.transaction.checkSecurityProvider
import com.flowfoundation.wallet.manager.flowjvm.transaction.updateSecurityProvider
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
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.setBackupManually
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.gson.Gson
import com.nftco.flow.sdk.DomainTag
import com.nftco.flow.sdk.FlowAccount
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import com.nftco.flow.sdk.bytesToHex
import com.nftco.flow.sdk.crypto.Crypto
import com.nftco.flow.sdk.hexToBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet
import wallet.core.jni.PrivateKey
import wallet.core.jni.StoredKey


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

    fun importKeyStore(json: String, password: String, address: String) {
        loadingLiveData.postValue(true)
        restoreType = RestoreType.KEYSTORE
        try {
            ioScope {
                val keyStore = StoredKey.importJSON(json.toByteArray())
                val privateKeyData = keyStore.decryptPrivateKey(password.toByteArray())
                if (privateKeyData == null || privateKeyData.isEmpty()) {
                    loadingLiveData.postValue(false)
                    toast(msgRes = R.string.wrong_password)
                    return@ioScope
                }
                val privateKey = PrivateKey(privateKeyData)

                val p1PublicKey =
                    privateKey.publicKeyNist256p1.uncompressed().data().bytesToHex().removePrefix("04")

                val k1PublicKey =
                    privateKey.getPublicKeySecp256k1(false).data().bytesToHex().removePrefix("04")

                if (address.isEmpty()) {
                    checkIsQueryAddress(privateKey.data().bytesToHex(), k1PublicKey, privateKey.data().bytesToHex(), p1PublicKey)
                } else {
                    queryAddressPublicKey(
                        address,
                        privateKey.data().bytesToHex(),
                        k1PublicKey,
                        privateKey.data().bytesToHex(),
                        p1PublicKey
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadingLiveData.postValue(false)
            toast(msgRes = R.string.restore_failed)
        }

    }

    fun importPrivateKey(privateKeyInput: String, address: String) {
        loadingLiveData.postValue(true)
        restoreType = RestoreType.PRIVATE_KEY
        try {
            ioScope {
                val privateKey = PrivateKey(privateKeyInput.hexToBytes())

                val p1PublicKey =
                    privateKey.publicKeyNist256p1.uncompressed().data().bytesToHex().removePrefix("04")

                val k1PublicKey =
                    privateKey.getPublicKeySecp256k1(false).data().bytesToHex().removePrefix("04")

                if (address.isEmpty()) {
                    checkIsQueryAddress(privateKey.data().bytesToHex(), k1PublicKey, privateKey.data().bytesToHex(), p1PublicKey)
                } else {
                    queryAddressPublicKey(
                        address,
                        privateKey.data().bytesToHex(),
                        k1PublicKey,
                        privateKey.data().bytesToHex(),
                        p1PublicKey
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadingLiveData.postValue(false)
            toast(msgRes = R.string.restore_failed)
        }
    }

    fun importSeedPhrase(
        mnemonic: String,
        address: String,
        passphrase: String,
        derivationPath: String
    ) {
        loadingLiveData.postValue(true)
        restoreType = RestoreType.SEED_PHRASE
        try {
            ioScope {
                val hdWallet = HDWallet(mnemonic, passphrase)
                val k1PrivateKey = hdWallet.getCurveKey(Curve.SECP256K1, derivationPath)
                val k1PublicKey =
                    k1PrivateKey.getPublicKeySecp256k1(false).data().bytesToHex().removePrefix("04")
                val p1PrivateKey = hdWallet.getCurveKey(Curve.NIST256P1, derivationPath)
                val p1PublicKey =
                    p1PrivateKey.publicKeyNist256p1.uncompressed().data().bytesToHex()
                        .removePrefix("04")
                if (address.isEmpty()) {
                    checkIsQueryAddress(k1PrivateKey.data().bytesToHex(), k1PublicKey, p1PrivateKey.data().bytesToHex(), p1PublicKey)
                } else {
                    queryAddressPublicKey(
                        address,
                        k1PrivateKey.data().bytesToHex(),
                        k1PublicKey,
                        p1PrivateKey.data().bytesToHex(),
                        p1PublicKey
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadingLiveData.postValue(false)
            toast(msgRes = R.string.restore_failed)
        }
    }

    private suspend fun queryAddressPublicKey(
        address: String, k1PrivateKey: String, k1PublicKey: String,
        p1PrivateKey: String, p1PublicKey: String
    ) {
        try {
            val account = FlowApi.get().getAccountAtLatestBlock(FlowAddress(address))
            if (account == null) {
                if (checkIsLogin(k1PrivateKey, k1PublicKey, SignatureAlgorithm.ECDSA_SECP256k1)) {
                    return
                } else if (checkIsLogin(p1PrivateKey, p1PublicKey, SignatureAlgorithm.ECDSA_P256)) {
                    return
                } else {
                    checkIsQueryAddress(k1PrivateKey, k1PublicKey, p1PrivateKey, p1PublicKey)
                }
                return
            }
            if (checkIsMatched(account, k1PrivateKey, k1PublicKey)) {
                return
            } else if (checkIsMatched(account, p1PrivateKey, p1PublicKey)) {
                return
            } else {
                checkIsQueryAddress(k1PrivateKey, k1PublicKey, p1PrivateKey, p1PublicKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            checkIsQueryAddress(k1PrivateKey, k1PublicKey, p1PrivateKey, p1PublicKey)
        }
    }

    private suspend fun checkIsQueryAddress(
        k1PrivateKey: String,
        k1PublicKey: String,
        p1PrivateKey: String,
        p1PublicKey: String
    ) {
        if (checkIsLogin(k1PrivateKey, k1PublicKey, SignatureAlgorithm.ECDSA_SECP256k1)) {
            return
        } else if (checkIsLogin(p1PrivateKey, p1PublicKey, SignatureAlgorithm.ECDSA_P256)) {
            return
        } else {
            queryAddressWithPublicKey(
                k1PrivateKey, k1PublicKey, p1PrivateKey, p1PublicKey
            )
        }
    }

    private fun checkIsMatched(
        account: FlowAccount,
        privateKey: String,
        publicKey: String
    ): Boolean {
        val accountKey = account.keys.lastOrNull { it.publicKey.base16Value == publicKey }
        return accountKey?.run {
            checkAndImportKeyStoreAddress(
                KeystoreAddress(
                    address = account.address.base16Value,
                    publicKey = publicKey,
                    privateKey = privateKey,
                    keyId = id,
                    weight = weight,
                    hashAlgo = hashAlgo.index,
                    signAlgo = signAlgo.index
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

    private suspend fun checkIsLogin(privateKey: String, publicKey: String, signAlgo: SignatureAlgorithm): Boolean {
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

    private fun checkAndImportKeyStoreAddress(keystoreAddress: KeystoreAddress) {
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
                        loginWithKeyStoreAddress(keystoreAddress)
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
            || currentKeyStoreAddress?.address == "0x") {
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
                                    hashAlgo = cryptoProvider.getHashAlgorithm().index,
                                    signAlgo = cryptoProvider.getSignatureAlgorithm().index
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
                        loge(catching.exceptionOrNull())
                        callback.invoke(false)
                    }
                }
            }
        }
    }

    private fun loginWithPrivateKey(privateKey: String, publicKey: String, signAlgo: SignatureAlgorithm) {
        ioScope {
            val activity = BaseActivity.getCurrentActivity() ?: return@ioScope
            loginAndFetchWallet(privateKey, publicKey, signAlgo) { isSuccess ->
                uiScope {
                    loadingLiveData.postValue(false)
                    if (isSuccess) {
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

    private fun loginWithKeyStoreAddress(keystoreAddress: KeystoreAddress) {
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
                                    hashAlgo = cryptoProvider.getHashAlgorithm().index,
                                    signAlgo = cryptoProvider.getSignatureAlgorithm().index
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
                                        MixpanelManager.accountRestore(cryptoProvider.getAddress(), restoreType)
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
                        callback.invoke(false)
                    }
                }
            }
        }
    }

    private fun loginAndFetchWallet(
        privateKey: String, publicKey: String, signAlgo: SignatureAlgorithm,
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
                                signature = getSignature(
                                    getFirebaseJwt(), privateKey, signAlgo
                                ),
                                accountKey = AccountKey(
                                    publicKey = publicKey,
                                    hashAlgo = HashAlgorithm.SHA2_256.index,
                                    signAlgo = signAlgo.index
                                ),
                                deviceInfo = deviceInfoRequest
                            )
                        )
                        if (resp.data?.customToken.isNullOrBlank()) {
                            callback.invoke(false)
                        } else {
                            firebaseLogin(resp.data?.customToken!!) { isSuccess ->
                                if (isSuccess) {
                                    ioScope {
                                        try {
                                            val walletData = service.getWalletList()
                                            val addressResponse = queryService.queryAddress(publicKey)
                                            val wallet = addressResponse.accounts.firstOrNull {
                                                walletData.data?.walletAddress() == it.address
                                            }
                                            if (wallet == null) {
                                                callback.invoke(false)
                                            } else {
                                                AccountManager.add(
                                                    Account(
                                                        userInfo = service.userInfo().data,
                                                        keyStoreInfo = Gson().toJson(KeystoreAddress(
                                                            address = wallet.address,
                                                            publicKey = publicKey,
                                                            privateKey = privateKey,
                                                            keyId = wallet.keyId,
                                                            weight = wallet.weight,
                                                            hashAlgo = wallet.hashAlgo,
                                                            signAlgo = wallet.signAlgo
                                                        ))
                                                    )
                                                )
                                                MixpanelManager.accountRestore(wallet.address, restoreType)
                                                setRegistered()
                                                setBackupManually()
                                                clearUserCache()
                                                callback.invoke(true)
                                            }
                                        } catch (e: Exception) {
                                            callback.invoke(false)
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
                        callback.invoke(false)
                    }
                }
            }
        }
    }

    private fun getSignature(jwt: String, privateKey: String, signAlgo: SignatureAlgorithm): String {
        checkSecurityProvider()
        updateSecurityProvider()
        return Crypto.getSigner(
            privateKey = Crypto.decodePrivateKey(
                privateKey, signAlgo
            ),
            hashAlgo = HashAlgorithm.SHA2_256
        ).sign(DomainTag.USER_DOMAIN_TAG + jwt.encodeToByteArray()).bytesToHex()
    }
}