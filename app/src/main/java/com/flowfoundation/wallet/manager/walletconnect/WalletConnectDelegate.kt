package com.flowfoundation.wallet.manager.walletconnect

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.google.gson.Gson
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import com.flowfoundation.wallet.manager.walletconnect.model.toWcRequest
import com.flowfoundation.wallet.page.wallet.dialog.MoveDialog
import com.flowfoundation.wallet.utils.extensions.openInSystemBrowser
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isShowMoveDialog
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EvmRequestAccountDialog
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.FclAuthnDialog
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import com.reown.android.Core
import com.reown.android.CoreClient
import kotlinx.coroutines.delay
import com.flowfoundation.wallet.page.window.WindowFrame
import android.view.View
import com.flowfoundation.wallet.page.browser.browserInstance
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.Env

private val TAG = WalletConnectDelegate::class.java.simpleName

internal class WalletConnectDelegate : SignClient.WalletDelegate {

    private var isConnected = false
    private val processedRequestIds = mutableSetOf<Long>()
    private var pendingRedirectUrl: String? = null
    private var isRedirecting = false
    private var isSessionApproved = false

    /**
     * Triggered whenever the connection state is changed
     */
    override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {
        logd(TAG, "onConnectionStateChange() state:${state.isAvailable}")
        isConnected = state.isAvailable
        if (!state.isAvailable) {
            logd(TAG, "Connection lost, attempting to reconnect")
            ioScope {
                var reconnectAttempts = 0
                val maxReconnectAttempts = 3
                
                while (reconnectAttempts < maxReconnectAttempts && !isConnected) {
                    try {
                        logd(TAG, "Reconnection attempt ${reconnectAttempts + 1} of $maxReconnectAttempts")
                        //delay(1000 * (reconnectAttempts + 1)) // Exponential backoff
                        CoreClient.Relay.connect { error: Core.Model.Error ->
                            loge(TAG, "CoreClient.Relay connect error: $error")
                        }
                        reconnectAttempts++
                    } catch (e: Exception) {
                        loge(TAG, "Error during reconnection attempt ${reconnectAttempts + 1}: ${e.message}")
                        loge(e)
                        reconnectAttempts++
                    }
                }
                
                if (!isConnected) {
                    loge(TAG, "Failed to reconnect after $maxReconnectAttempts attempts")
                } else {
                    logd(TAG, "Successfully reconnected")
                }
            }
        } else if (isRedirecting) {
            // If we were trying to redirect and connection is restored, try again
            performRedirect()
        }
    }

    private fun performRedirect() {
        val redirectUrl = pendingRedirectUrl ?: return
        val activity = BaseActivity.getCurrentActivity() ?: run {
            loge(TAG, "No current activity found for redirection")
            return
        }

        logd(TAG, "Attempting to redirect to: $redirectUrl")
        try {
            redirectUrl.openInSystemBrowser(activity, true)
            logd(TAG, "Successfully opened URL in system browser")
            pendingRedirectUrl = null
            isRedirecting = false
        } catch (e: Exception) {
            loge(TAG, "Failed to open URL in system browser: ${e.message}")
            loge(e)
        }
    }

    override fun onError(error: Sign.Model.Error) {
        logd(TAG, "onError() error:$error")
        loge(error.throwable)
        
        // Show user-friendly error message
        uiScope {
            val errorMessage = when {
                error.throwable.message?.contains("No proposal or pending session") == true -> {
                    // Clean up any stale sessions when we get this error
                    try {
                        val activeSessions = SignClient.getListOfActiveSessions()
                        logd(TAG, "Cleaning up stale sessions. Current count: ${activeSessions.size}")
                        activeSessions.forEach { session ->
                            if (session.metaData == null) {
                                logd(TAG, "Disconnecting stale session: ${session.topic}")
                                SignClient.disconnect(Sign.Params.Disconnect(sessionTopic = session.topic)) { disconnectError ->
                                    loge(TAG, "Error disconnecting stale session: ${disconnectError.throwable}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        loge(TAG, "Error cleaning up sessions: ${e.message}")
                        loge(e)
                    }
                    R.string.wallet_connect_no_proposal
                }
                error.throwable.message?.contains("pairing topic") == true -> {
                    R.string.wallet_connect_pairing_error
                }
                error.throwable.message?.contains("Pairing URI expired") == true -> {
                    R.string.wallet_connect_pairing_error
                }
                else -> R.string.wallet_connect_generic_error
            }
            try {
                toast(errorMessage)
                logd(TAG, "Showed error toast for message: ${error.throwable.message}")
            } catch (e: Exception) {
                loge(TAG, "Failed to show error toast: ${e.message}")
                loge(e)
            }
        }
    }

    override fun onProposalExpired(proposal: Sign.Model.ExpiredProposal) {
        logd(TAG, "onProposalExpired() expiredProposal:${Gson().toJson(proposal)}")
        // Clean up any associated sessions when proposal expires
        try {
            val activeSessions = SignClient.getListOfActiveSessions()
            activeSessions.forEach { session ->
                if (session.metaData == null) {
                    logd(TAG, "Disconnecting session for expired proposal: ${session.topic}")
                    SignClient.disconnect(Sign.Params.Disconnect(sessionTopic = session.topic)) { error ->
                        loge(TAG, "Error disconnecting session: ${error.throwable}")
                    }
                }
            }
        } catch (e: Exception) {
            loge(TAG, "Error cleaning up expired proposal sessions: ${e.message}")
            loge(e)
        }
        uiScope {
            try {
                toast(R.string.wallet_connect_proposal_expired)
                logd(TAG, "Showed proposal expired toast")
            } catch (e: Exception) {
                loge(TAG, "Failed to show proposal expired toast: ${e.message}")
                loge(e)
            }
        }
    }

    override fun onRequestExpired(request: Sign.Model.ExpiredRequest) {
        logd(TAG, "onRequestExpired() expiredRequest:${Gson().toJson(request)}")
        uiScope {
            toast(R.string.wallet_connect_request_expired)
        }
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

        processedRequestIds.clear()
        isSessionApproved = false  // Reset approval state for new session

        // Try to get the activity with a small delay to allow it to be ready
        ioScope {
            var attempts = 0
            val maxAttempts = 5
            var activity: BaseActivity? = null

            while (attempts < maxAttempts && activity == null) {
                activity = BaseActivity.getCurrentActivity()
                if (activity == null) {
                    logd(TAG, "Activity not found, attempt ${attempts + 1} of $maxAttempts")
                    delay(1000)
                    attempts++
                }
            }

            if (activity == null) {
                loge(TAG, "No current activity found after $maxAttempts attempts")
                // Show error message to user and reject the session
                uiScope {
                    toast(R.string.wallet_connect_activity_error)
                    logd(TAG, "Showing activity error toast to user")
                }
                try {
                    sessionProposal.reject()
                    logd(TAG, "Rejected session due to no activity found")
                } catch (e: Exception) {
                    loge(TAG, "Error rejecting session: ${e.message}")
                    loge(e)
                }
                return@ioScope
            }

            try {
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
                            isSessionApproved = true
                            logd(TAG, "Session approved by user")
                            approveSession()
                            
                            // Show toast only if no redirect URL and browser is not active
                            if (sessionProposal.redirect.isNullOrEmpty()) {
                                logd(TAG, "No redirect URL, checking if browser is active")
                                val browserContainer = WindowFrame.browserContainer()
                                val browserInstance = browserInstance()
                                if (browserContainer?.visibility != View.VISIBLE || browserInstance == null) {
                                    logd(TAG, "Browser is not active, showing toast")
                                    uiScope {
                                        toast(R.string.return_to_browser_to_continue)
                                    }
                                } else {
                                    logd(TAG, "Browser is active, skipping toast")
                                }
                            }
                        } else {
                            logd(TAG, "Session rejected by user")
                            reject()
                        }
                    }
                }
            } catch (e: Exception) {
                loge(TAG, "Error in session proposal handling: ${e.message}")
                loge(e)
                uiScope {
                    toast(R.string.wallet_connect_proposal_error)
                }
                try {
                    sessionProposal.reject()
                } catch (e: Exception) {
                    loge(TAG, "Error rejecting session: ${e.message}")
                    loge(e)
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

        // Get the redirect from the active session
        val redirect = SignClient.getActiveSessionByTopic(sessionRequest.topic)?.redirect
        if (!redirect.isNullOrEmpty()) {
            logd(TAG, "Found redirect URL for session: $redirect")
        }

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

        when (settleSessionResponse) {
            is Sign.Model.SettledSessionResponse.Result -> {
                // Get the redirect URL from either pendingRedirectUrl or the settled session metadata
                val redirectUrl = pendingRedirectUrl ?: run {
                    val metadata = settleSessionResponse.session.metaData
                    if (!metadata?.redirect.isNullOrEmpty()) {
                        logd(TAG, "Using metadata redirect URL: ${metadata?.redirect}")
                        metadata?.redirect
                    } else {
                        logd(TAG, "No redirect URL found in session metadata")
                        null
                    }
                }

                logd(TAG, "Final redirect URL: $redirectUrl")

                if (redirectUrl != null) {
                    val activity = BaseActivity.getCurrentActivity() ?: run {
                        loge(TAG, "No current activity found for redirection")
                        return
                    }

                    logd(TAG, "Attempting to redirect to: $redirectUrl")
                    try {
                        redirectUrl.openInSystemBrowser(activity, true)
                        logd(TAG, "Successfully opened URL in system browser")
                        pendingRedirectUrl = null
                        isRedirecting = false
                    } catch (e: Exception) {
                        loge(TAG, "Failed to open URL in system browser: ${e.message}")
                        loge(e)
                    }
                } else {
                    logd(TAG, "No redirect URL found in pendingRedirectUrl or session metadata")
                }
            }
            is Sign.Model.SettledSessionResponse.Error -> {
                loge(TAG, "Session settlement error: ${settleSessionResponse.errorMessage}")
                uiScope {
                    try {
                        toast(R.string.wallet_connect_pairing_error)
                        logd(TAG, "Showed session settlement error toast")
                    } catch (e: Exception) {
                        loge(TAG, "Failed to show session settlement error toast: ${e.message}")
                        loge(e)
                    }
                }
            }
        }
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