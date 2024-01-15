package io.outblock.lilico.manager.walletconnect

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.firebase.auth.getFirebaseJwt
import io.outblock.lilico.manager.account.Account
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.walletconnect.model.WCWalletResponse
import io.outblock.lilico.manager.walletconnect.model.WalletConnectMethod
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.clearUserCache
import io.outblock.lilico.network.model.AccountKey
import io.outblock.lilico.network.model.LoginRequest
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.main.MainActivity
import io.outblock.lilico.page.wallet.confirm.WalletConfirmActivity
import io.outblock.lilico.page.walletrestore.firebaseLogin
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.loge
import io.outblock.lilico.utils.setRegistered
import io.outblock.lilico.utils.toast
import io.outblock.lilico.utils.uiScope
import io.outblock.wallet.KeyManager
import io.outblock.wallet.KeyStoreCryptoProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private val TAG = WalletDappDelegate::class.java.simpleName


internal class WalletDappDelegate : SignClient.DappDelegate {

    private var isConnected = false

    /**
     * Triggered whenever the connection state is changed
     */
    override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {
        logd(TAG, "onConnectionStateChange() state:${Gson().toJson(state)}")
        isConnected = state.isAvailable
    }

    /**
     * Triggered whenever there is an issue inside the SDK
     */
    override fun onError(error: Sign.Model.Error) {
        logd(TAG, "onError() error:$error")
        loge(error.throwable)
    }

    /**
     * Triggered when Dapp receives the session approval from wallet
     */
    override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
        logd(TAG, "onSessionApproved() ApprovedSession:${Gson().toJson(approvedSession)}")
        updateWalletConnectSession(approvedSession)
        val params = mapOf(
            "addr" to approvedSession.address(),
        )
        SignClient.request(
            Sign.Params.Request(
                sessionTopic = approvedSession.topic,
                method = WalletConnectMethod.ACCOUNT_INFO.value,
                params = "[${Gson().toJson(params)}]",
                chainId = approvedSession.chainId(),
            )
        ) { error -> loge(error.throwable) }
    }

    /**
     * Triggered when Dapp receives the session delete from wallet
     */
    override fun onSessionDelete(deletedSession: Sign.Model.DeletedSession) {
        logd(TAG, "onSessionDelete() deletedSession:${Gson().toJson(deletedSession)}")
        isConnected = false
    }

    /**
     * Triggered when the peer emits events that match the list of events agreed upon session settlement
     */
    override fun onSessionEvent(sessionEvent: Sign.Model.SessionEvent) {
        logd(TAG, "onSessionEvent() eventSession:${Gson().toJson(sessionEvent)}")
    }

    /**
     * Triggered when Dapp receives the session extend from wallet
     */
    override fun onSessionExtend(session: Sign.Model.Session) {
        logd(TAG, "onSessionExtend() extendedSession:${Gson().toJson(session)}")
    }

    /**
     * Triggered when Dapp receives the session rejection from wallet
     */
    override fun onSessionRejected(rejectedSession: Sign.Model.RejectedSession) {
        logd(TAG, "onSessionRejected() rejectedSession:${Gson().toJson(rejectedSession)}")
    }

    /**
     * Triggered when Dapp receives the session request response from wallet
     */
    override fun onSessionRequestResponse(response: Sign.Model.SessionRequestResponse) {
        logd(TAG, "onSessionRequestResponse() requestResponseSession:${Gson().toJson(response)}")
        when (response.method) {
            WalletConnectMethod.ACCOUNT_INFO.value -> accountInfoResponse(response.result as Sign.Model.JsonRpcResponse.JsonRpcResult)
            WalletConnectMethod.ADD_DEVICE_KEY.value -> addDeviceKeyResponse()
        }
    }

    private fun accountInfoResponse(jsonRpcResponse: Sign.Model.JsonRpcResponse.JsonRpcResult) {
        val activity = BaseActivity.getCurrentActivity() ?: return
        val accountResponse = Gson().fromJson(jsonRpcResponse.result, WCWalletResponse::class.java)
        val account = accountResponse.data ?: return
        WalletConfirmActivity.launch(
            activity, account.userAvatar, account.userName, account.walletAddress
        )
    }

    private fun addDeviceKeyResponse() {
        val activity = BaseActivity.getCurrentActivity() ?: return
        login(KeyManager.getCurrentPrefix()) { isSuccess ->
            uiScope {
                if (isSuccess) {
                    delay(200)
                    MainActivity.relaunch(activity, clearTop = true)
                } else {
                    toast(msg = "login failure")
                }
            }
        }
    }

    private fun login(prefix: String, callback: (isSuccess: Boolean) -> Unit) {
        ioScope {
            val cryptoProvider = KeyStoreCryptoProvider(prefix)
            getFirebaseUid { uid ->
                if (uid.isNullOrBlank()) {
                    callback.invoke(false)
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
                            if (resp.status == 404) {
                                callback.invoke(false)
                            } else {
                                callback.invoke(false)
                            }
                        } else {
                            firebaseLogin(resp.data?.customToken!!) { isSuccess ->
                                if (isSuccess) {
                                    setRegistered()
                                    ioScope {
                                        AccountManager.add(
                                            Account(
                                                userInfo = service.userInfo().data,
                                                prefix = prefix,
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

    /**
     * Triggered when Dapp receives the session update from wallet
     */
    override fun onSessionUpdate(updatedSession: Sign.Model.UpdatedSession) {
        logd(TAG, "onSessionUpdate() updatedSession:${Gson().toJson(updatedSession)}")
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

private var currentSession: Sign.Model.ApprovedSession? = null

fun Sign.Model.ApprovedSession.chainId(): String {
    val account = namespaces["flow"]!!.accounts.first()
    return account.replaceAfterLast(":", "").removeSuffix(":")
}

fun Sign.Model.ApprovedSession.address(): String {
    val account = namespaces["flow"]!!.accounts.first()
    return account.replaceBeforeLast(":", "").removePrefix(":")
}

internal fun updateWalletConnectSession(session: Sign.Model.ApprovedSession) {
    currentSession = session
}

internal fun currentWcSession() = currentSession

