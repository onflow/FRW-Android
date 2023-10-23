package io.outblock.lilico.manager.walletconnect

import com.google.gson.Gson
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignInterface
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.manager.walletconnect.model.toWcRequest
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.loge
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.widgets.webview.fcl.dialog.FclAuthnDialog
import io.outblock.lilico.widgets.webview.fcl.model.FclDialogModel

private val TAG = WalletConnectDelegate::class.java.simpleName

internal class WalletConnectDelegate : SignInterface.WalletDelegate {

    private var isConnected = false

    /**
     * Triggered whenever the connection state is changed
     */
    override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {
        logd(TAG, "onConnectionStateChange() state:${Gson().toJson(state)}")
        isConnected = state.isAvailable
    }

    override fun onError(error: Sign.Model.Error) {
        logd(TAG, "onError() error:$error")
        loge(error.throwable)
    }

    /**
     * Triggered when the session is deleted by the peer
     */
    override fun onSessionDelete(deletedSession: Sign.Model.DeletedSession) {
        logd(TAG, "onSessionDelete() deletedSession:${Gson().toJson(deletedSession)}")
        isConnected = false
    }

    override fun onSessionExtend(session: Sign.Model.Session) {
        logd(TAG, "onSessionExtend() extendedSession:${Gson().toJson(session)}")
    }

    /**
     * Triggered when wallet receives the session proposal sent by a Dapp
     */
    override fun onSessionProposal(
        sessionProposal: Sign.Model.SessionProposal,
        verifyContext: Sign.Model.VerifyContext
    ) {
        logd(TAG, "onSessionProposal() sessionProposal json:${Gson().toJson(sessionProposal)}")
        val activity = BaseActivity.getCurrentActivity() ?: return
        uiScope {
            with(sessionProposal) {
                val approve = FclAuthnDialog().show(
                    activity.supportFragmentManager,
                    FclDialogModel(title = description, url = url, logo = icons.firstOrNull()?.toString())
                )
                if (approve) {
                    approveSession()
                } else {
                    reject()
                }
            }
        }
    }

    /**
     * Triggered when a Dapp sends SessionRequest to sign a transaction or a message
     */
    override fun onSessionRequest(
        sessionRequest: Sign.Model.SessionRequest,
        verifyContext: Sign.Model.VerifyContext
    ) {
        logd(TAG, "onSessionRequest() sessionRequest:${Gson().toJson(sessionRequest)}")
        logd(TAG, "onSessionRequest() sessionRequest:$sessionRequest")
        ioScope { sessionRequest.toWcRequest().dispatch() }
    }

    /**
     * Triggered when wallet receives the session settlement response from Dapp
     */
    override fun onSessionSettleResponse(settleSessionResponse: Sign.Model.SettledSessionResponse) {
        logd(TAG, "onSessionSettleResponse() settleSessionResponse:${Gson().toJson(settleSessionResponse)}")
    }

    /**
     * Triggered when wallet receives the session update response from Dapp
     */
    override fun onSessionUpdateResponse(sessionUpdateResponse: Sign.Model.SessionUpdateResponse) {
        logd(TAG, "onSessionUpdateResponse() sessionUpdateResponse:${Gson().toJson(sessionUpdateResponse)}")
    }
}