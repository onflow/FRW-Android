package com.flowfoundation.wallet.manager.walletconnect

import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.google.gson.Gson
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import com.flowfoundation.wallet.manager.walletconnect.model.toWcRequest
import com.flowfoundation.wallet.page.wallet.dialog.MoveDialog
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isShowMoveDialog
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EvmRequestAccountDialog
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.FclAuthnDialog
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel

private val TAG = WalletConnectDelegate::class.java.simpleName

internal class WalletConnectDelegate : SignClient.WalletDelegate {

    private var isConnected = false
    private val processedRequestIds = mutableSetOf<Long>()

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

    override fun onProposalExpired(proposal: Sign.Model.ExpiredProposal) {
        logd(TAG, "onProposalExpired() expiredProposal:${Gson().toJson(proposal)}")
    }

    override fun onRequestExpired(request: Sign.Model.ExpiredRequest) {
        logd(TAG, "onRequestExpired() expiredRequest:${Gson().toJson(request)}")
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
        logd(TAG, "onSessionProposal() verifyContext json:${Gson().toJson(verifyContext)}")
        val activity = BaseActivity.getCurrentActivity() ?: return
        processedRequestIds.clear()
        uiScope {
            with(sessionProposal) {
                val approve = if (WalletManager.isEVMAccountSelected()) {
                    if (isShowMoveDialog()) {
                        MoveDialog().showMove(activity.supportFragmentManager, description)
                    }
                    EvmRequestAccountDialog().show(
                        activity.supportFragmentManager,
                        EVMDialogModel(
                            title = name,
                            url = url,
                            network = chainNetWorkString()
                        )
                    )
                } else {
                    val data = FclDialogModel(
                        title = name,
                        url = url,
                        logo = icons.firstOrNull()?.toString(),
                        network = network()
                    )
                    FclAuthnDialog().show(
                        activity.supportFragmentManager,
                        data
                    )
                }
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
        if (processedRequestIds.contains(sessionRequest.request.id)) {
            logd(TAG, "onSessionRequest() Duplicate request ignored. Request ID: ${sessionRequest.request.id}")
            return
        }
        processedRequestIds.add(sessionRequest.request.id)
        logd(TAG, "onSessionRequest() sessionRequest:${Gson().toJson(sessionRequest)}")
        logd(TAG, "onSessionRequest() sessionRequest:$sessionRequest")
        ioScope { sessionRequest.toWcRequest().dispatch() }
    }

    /**
     * Triggered when wallet receives the session settlement response from Dapp
     */
    override fun onSessionSettleResponse(settleSessionResponse: Sign.Model.SettledSessionResponse) {
        logd(
            TAG,
            "onSessionSettleResponse() settleSessionResponse:${Gson().toJson(settleSessionResponse)}"
        )
    }

    /**
     * Triggered when wallet receives the session update response from Dapp
     */
    override fun onSessionUpdateResponse(sessionUpdateResponse: Sign.Model.SessionUpdateResponse) {
        logd(
            TAG,
            "onSessionUpdateResponse() sessionUpdateResponse:${Gson().toJson(sessionUpdateResponse)}"
        )
    }
}