package io.outblock.lilico.page.restore.multirestore.viewmodel

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.nftco.flow.sdk.FlowTransactionStatus
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.firebase.auth.getFirebaseJwt
import io.outblock.lilico.manager.account.Account
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.backup.BackupCryptoProvider
import io.outblock.lilico.manager.flowjvm.CADENCE_ADD_PUBLIC_KEY
import io.outblock.lilico.manager.flowjvm.CadenceArgumentsBuilder
import io.outblock.lilico.manager.flowjvm.transaction.sendTransactionWithMultiSignature
import io.outblock.lilico.manager.flowjvm.ufix64Safe
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.clearUserCache
import io.outblock.lilico.network.generatePrefix
import io.outblock.lilico.network.model.AccountKey
import io.outblock.lilico.network.model.AccountKeySignature
import io.outblock.lilico.network.model.AccountSignRequest
import io.outblock.lilico.network.model.LoginRequest
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.main.MainActivity
import io.outblock.lilico.page.restore.multirestore.model.RestoreOption
import io.outblock.lilico.page.restore.multirestore.model.RestoreOptionModel
import io.outblock.lilico.page.walletrestore.firebaseLogin
import io.outblock.lilico.page.window.bubble.tools.pushBubbleStack
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.loge
import io.outblock.lilico.utils.setRegistered
import io.outblock.lilico.utils.toast
import io.outblock.lilico.utils.uiScope
import io.outblock.wallet.KeyManager
import io.outblock.wallet.KeyStoreCryptoProvider
import io.outblock.wallet.toFormatString
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import wallet.core.jni.HDWallet
import java.security.Security


class MultiRestoreViewModel : ViewModel(), OnTransactionStateChange {

    val optionChangeLiveData = MutableLiveData<RestoreOptionModel>()

    private val optionList = mutableListOf<RestoreOption>()
    private var currentOption = RestoreOption.RESTORE_START
    private var currentIndex = -1
    private var restoreUserName = ""
    private var restoreAddress = ""
    private val mnemonicList = mutableListOf<String>()
    private var currentTxId: String? = null

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun getRestoreOptionList(): List<RestoreOption> {
        return optionList
    }

    fun getCurrentIndex(): Int {
        return currentIndex
    }

    fun changeOption(option: RestoreOption, index: Int) {
        this.currentOption = option
        this.currentIndex = index
        optionChangeLiveData.postValue(RestoreOptionModel(option, index))
    }

    fun isRestoreValid(): Boolean {
        return optionList.size >= 2
    }

    fun startRestore() {
        addCompleteOption()
        changeOption(optionList[0], 0)
    }

    private fun toNext() {
        ++currentIndex
        changeOption(optionList[currentIndex], currentIndex)
    }

    fun handleBackPressed(): Boolean {
        if (currentIndex > 0) {
            --currentIndex
            if (mnemonicList.size > currentIndex) {
                mnemonicList.removeAt(currentIndex)
            }
            changeOption(optionList[currentIndex], currentIndex)
            return true
        }
        return false
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
                val keyPair = KeyManager.generateKeyWithPrefix(generatePrefix(restoreUserName))
                val txId = CADENCE_ADD_PUBLIC_KEY.executeTransactionWithMultiKey {
                    arg { string(keyPair.public.toFormatString()) }
                    arg { uint8(SignatureAlgorithm.ECDSA_P256.index) }
                    arg { uint8(HashAlgorithm.SHA2_256.index) }
                    arg { ufix64Safe(1000) }
                }
                val transactionState = TransactionState(
                    transactionId = txId!!,
                    time = System.currentTimeMillis(),
                    state = FlowTransactionStatus.PENDING.num,
                    type = TransactionState.TYPE_ADD_PUBLIC_KEY,
                    data = ""
                )
                currentTxId = txId
                TransactionStateManager.newTransaction(transactionState)
                pushBubbleStack(transactionState)
            } catch (e: Exception) {
                loge(e)
                if (e is NoSuchElementException) {
                    toast(msgRes = R.string.restore_failed_option, duration = Toast.LENGTH_LONG)
                } else {
                    toast(msgRes = R.string.restore_failed, duration = Toast.LENGTH_LONG)
                }
                val activity = BaseActivity.getCurrentActivity() ?: return@ioScope
                activity.finish()
            }
        }
    }

    private fun syncAccountInfo() {
        ioScope {
            try {
                val cryptoProvider = KeyStoreCryptoProvider(KeyManager.getCurrentPrefix())
                val service = retrofit().create(ApiService::class.java)
                val providers = mnemonicList.map {
                    BackupCryptoProvider(HDWallet(it, ""))
                }
                val resp = service.signAccount(
                    AccountSignRequest(
                        AccountKey(publicKey = cryptoProvider.getPublicKey()),
                        providers.map {
                            val jwt = getFirebaseJwt()
                            AccountKeySignature(
                                publicKey = it.getPublicKey(),
                                signMessage = jwt,
                                signature = it.getUserSignature(jwt)
                            )
                        }.toList()
                    )
                )
                if (resp.status == 200) {
                    val activity = BaseActivity.getCurrentActivity() ?: return@ioScope
                    login(cryptoProvider) { isSuccess ->
                        uiScope {
                            if (isSuccess) {
                                delay(200)
                                MainActivity.relaunch(activity, clearTop = true)
                            } else {
                                toast(msg = "login failure")
                                activity.finish()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                loge(e)
            }
        }
    }

    private fun login(cryptoProvider: KeyStoreCryptoProvider, callback: (isSuccess: Boolean) -> Unit) {
        ioScope {
            getFirebaseUid { uid ->
                if (uid.isNullOrBlank()) {
                    callback.invoke(false)
                }
                runBlocking {
                    val catching = runCatching {
                        val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
                        val service = retrofit().create(ApiService::class.java)
                        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
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
                                    ioScope {
                                        AccountManager.add(
                                            Account(
                                                userInfo = service.userInfo().data,
                                                prefix = KeyManager.getCurrentPrefix()
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

    private suspend fun getFirebaseUid(callback: (uid: String?) -> Unit) {
        val uid = Firebase.auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            callback.invoke(uid)
            return
        }

        getFirebaseJwt(true)

        callback.invoke(Firebase.auth.currentUser?.uid)
    }

    fun addWalletInfo(userName: String, address: String) {
        restoreUserName = userName
        restoreAddress = address
    }

    private suspend fun String.executeTransactionWithMultiKey(arguments: CadenceArgumentsBuilder.() -> Unit): String? {
        val args = CadenceArgumentsBuilder().apply { arguments(this) }
        val providers = mnemonicList.map {
            BackupCryptoProvider(HDWallet(it, ""))
        }
        return try {
            sendTransactionWithMultiSignature(providers = providers, builder = {
                args.build().forEach { arg(it) }
                walletAddress(restoreAddress)
                script(this@executeTransactionWithMultiKey)
            })
        } catch (e: Exception) {
            loge(e)
            null
        }
    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_ADD_PUBLIC_KEY }
        transaction?.let { state ->
            if (currentTxId == state.transactionId && state.isSuccess()) {
                syncAccountInfo()
            }
        }
    }
}