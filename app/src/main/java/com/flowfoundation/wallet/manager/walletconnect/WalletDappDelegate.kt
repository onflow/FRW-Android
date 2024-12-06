package com.flowfoundation.wallet.manager.walletconnect

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.google.gson.Gson
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.drive.ACTION_GOOGLE_DRIVE_UPLOAD_FINISH
import com.flowfoundation.wallet.manager.drive.EXTRA_SUCCESS
import com.flowfoundation.wallet.manager.walletconnect.model.WCWalletResponse
import com.flowfoundation.wallet.manager.walletconnect.model.WalletConnectMethod
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.RestoreType
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.wallet.confirm.WalletConfirmActivity
import com.flowfoundation.wallet.page.wallet.sync.WalletSyncActivity
import com.flowfoundation.wallet.page.walletrestore.firebaseLogin
import com.flowfoundation.wallet.page.walletrestore.getFirebaseUid
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import io.outblock.wallet.KeyManager
import io.outblock.wallet.KeyStoreCryptoProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private val TAG = WalletDappDelegate::class.java.simpleName


internal class WalletDappDelegate : SignClient.DappDelegate {

    private var isConnected = false
    private var deviceBackupAddress = ""

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

    override fun onProposalExpired(proposal: Sign.Model.ExpiredProposal) {
        logd(TAG, "onProposalExpired() expiredProposal:${Gson().toJson(proposal)}")
    }

    override fun onRequestExpired(request: Sign.Model.ExpiredRequest) {
        logd(TAG, "onRequestExpired() expiredRequest:${Gson().toJson(request)}")
    }

    /**
     * Triggered when Dapp receives the session approval from wallet
     */
    override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
        logd(TAG, "onSessionApproved() ApprovedSession:${Gson().toJson(approvedSession)}")
        updateWalletConnectSession(approvedSession)
        sendSyncCallback(true)
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
        ) { error ->
            loge(error.throwable)
            sendSyncCallback(false)
        }
    }

    private fun sendSyncCallback(isSyncing: Boolean) {
        LocalBroadcastManager.getInstance(Env.getApp()).sendBroadcast(
            Intent(
                WalletSyncActivity.ACTION_SYNCING
            ).apply {
                putExtra(WalletSyncActivity.EXTRA_SYNCING, isSyncing)
            })
    }

    /**
     * Triggered when Dapp receives the session delete from wallet
     */
    override fun onSessionDelete(deletedSession: Sign.Model.DeletedSession) {
        logd(TAG, "onSessionDelete() deletedSession:${Gson().toJson(deletedSession)}")
        isConnected = false
        deviceBackupAddress = ""
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
            WalletConnectMethod.ACCOUNT_INFO.value -> accountInfoResponse(response.result)
            WalletConnectMethod.ADD_DEVICE_KEY.value -> addDeviceKeyResponse()
        }
    }

    private fun accountInfoResponse(jsonRpcResult: Sign.Model.JsonRpcResponse) {
        try {
            val activity = BaseActivity.getCurrentActivity() ?: return
            val rpcResult = jsonRpcResult as Sign.Model.JsonRpcResponse.JsonRpcResult
            val accountResponse = Gson().fromJson(rpcResult.result, WCWalletResponse::class.java)
            val account = accountResponse.data ?: return
            deviceBackupAddress = account.walletAddress
            WalletConfirmActivity.launch(
                activity, account.userAvatar, account.userName, account.walletAddress
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addDeviceKeyResponse() {
        val activity = BaseActivity.getCurrentActivity() ?: return
        login(KeyManager.getCurrentPrefix()) { isSuccess ->
            uiScope {
                if (isSuccess) {
                    MixpanelManager.accountRestore(deviceBackupAddress, RestoreType.DEVICE_BACKUP)
                    delay(200)
                } else {
                    toast(msgRes = R.string.login_failure)
                }
                MainActivity.relaunch(activity, clearTop = isSuccess)
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

