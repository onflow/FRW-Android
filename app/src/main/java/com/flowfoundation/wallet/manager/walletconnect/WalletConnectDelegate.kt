package com.flowfoundation.wallet.manager.walletconnect

import android.app.AlertDialog
import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.walletconnect.model.toWcRequest
import com.flowfoundation.wallet.page.browser.browserInstance
import com.flowfoundation.wallet.ReactNativeDemoActivity
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.page.window.WindowFrame
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
import com.google.gson.Gson
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import kotlinx.coroutines.delay

private val TAG = WalletConnectDelegate::class.java.simpleName

internal class WalletConnectDelegate : SignClient.WalletDelegate {

    private var isConnected = false
    private val processedRequestIds = mutableSetOf<Long>()
    private var pendingRedirectUrl: String? = null
    private var isRedirecting = false
    private var isSessionApproved = false
    private var lastActiveTopic: String? = null
    private var isProcessingRequest = false

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
                
                while (!isConnected && reconnectAttempts < maxReconnectAttempts) {
                    try {
                        delay(1000L * (reconnectAttempts + 1))
                        logd(TAG, "Reconnection attempt ${reconnectAttempts + 1} of $maxReconnectAttempts")
                        
                        // Only clean up sessions if we're not processing a request
                        if (!isProcessingRequest) {
                            try {
                                val activeSessions = SignClient.getListOfActiveSessions()
                                logd(TAG, "Cleaning up stale sessions. Current count: ${activeSessions.size}")
                                activeSessions.forEach { session ->
                                    if (session.metaData == null && session.topic != lastActiveTopic) {
                                        logd(TAG, "Disconnecting stale session: ${session.topic}")
                                        SignClient.disconnect(Sign.Params.Disconnect(sessionTopic = session.topic)) { error ->
                                            loge(TAG, "Error disconnecting stale session: ${error.throwable}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                loge(TAG, "Error cleaning up sessions: ${e.message}")
                                loge(e)
                            }
                        } else {
                            logd(TAG, "Skipping session cleanup while processing request")
                        }
                        
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
        logd(TAG, "onSessionProposal() Starting session proposal handling")

        processedRequestIds.clear()
        isSessionApproved = false  // Reset approval state for new session

        // Try to get the activity with a more robust approach
        ioScope {
            var attempts = 0
            val maxAttempts = 15  // Increased attempts
            var activity: BaseActivity? = null
            var lastError: Exception? = null

            // First add a small delay to allow any activity transitions to complete
            delay(300)

            while (attempts < maxAttempts && activity == null) {
                try {
                    activity = BaseActivity.getCurrentActivity()
                    if (activity == null) {
                        logd(TAG, "Activity not found, attempt ${attempts + 1} of $maxAttempts")
                        delay(500)
                        attempts++
                    } else {
                        logd(TAG, "Found activity: ${activity.javaClass.simpleName}")
                        // Verify activity is in a valid state
                        if (activity.isFinishing || activity.isDestroyed) {
                            logd(TAG, "Activity is finishing or destroyed, retrying")
                            activity = null
                            delay(500)
                            attempts++
                        }
                    }
                } catch (e: Exception) {
                    lastError = e
                    loge(TAG, "Error getting activity: ${e.message}")
                    delay(500)
                    attempts++
                }
            }

            if (activity == null) {
                val errorMsg = lastError?.message ?: "Unknown error"
                loge(TAG, "No current activity found after $maxAttempts attempts. Last error: $errorMsg")
                // Show error message to user and reject the session
                uiScope {
                    toast(R.string.wallet_connect_activity_error)
                    logd(TAG, "Showing activity error toast to user")
                }
                try {
                    logd(TAG, "Rejecting session due to no activity found")
                    sessionProposal.reject()
                    logd(TAG, "Session rejected successfully")
                } catch (e: Exception) {
                    loge(TAG, "Error rejecting session: ${e.message}")
                    loge(e)
                }
                return@ioScope
            }

            // Store a local reference to avoid activity becoming null later
            val foundActivity = activity

            try {
                logd(TAG, "Activity is ready, showing connection dialog UI")
                uiScope {
                    try {
                        with(sessionProposal) {
                            // Determine if this is an EVM dApp request based on session proposal
                            val isEVMRequest = isEVMSessionProposal(sessionProposal)
                            logd(TAG, "Session proposal detected as EVM: $isEVMRequest")
                            
                            val approve = if (isEVMRequest) {
                                logd(TAG, "EVM request detected, showing EVM dialog")
                                if (isShowMoveDialog()) {
                                    logd(TAG, "Launching React Native send workflow")
                                    ReactNativeDemoActivity.launch(
                                        foundActivity,
                                        "SelectTokens",
                                        WalletManager.selectedWalletAddress().toAddress(),
                                        if (isTestnet()) "testnet" else "mainnet"
                                    )
                                }
                                EvmRequestAccountDialog().show(
                                    foundActivity.supportFragmentManager,
                                    EVMDialogModel(
                                        title = name,
                                        url = url,
                                        network = chainNetWorkString()
                                    )
                                )
                            } else {
                                logd(TAG, "Flow request detected, showing FCL dialog")
                                val data = FclDialogModel(
                                    title = name,
                                    url = url,
                                    logo = icons.firstOrNull()?.toString(),
                                    network = if (isEVMRequest) "evm" else network() // Mark as EVM if detected
                                )
                                FclAuthnDialog().show(
                                    foundActivity.supportFragmentManager,
                                    data
                                )
                            }
                            
                            // Check if dialog was actually shown
                            if (approve) {
                                isSessionApproved = true
                                logd(TAG, "Session approved by user")
                                try {
                                    approveSession()
                                    logd(TAG, "Session approval sent successfully")
                                } catch (e: Exception) {
                                    loge(TAG, "Error during session approval: ${e.message}")
                                    loge(e)
                                    uiScope {
                                        toast(R.string.wallet_connect_approval_error)
                                    }
                                }
                                
                                // Show toast only if no redirect URL and browser is not active
                                if (sessionProposal.redirect.isEmpty()) {
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
                                } else {
                                    logd(TAG, "Redirect URL found: ${sessionProposal.redirect}")
                                }
                            } else {
                                logd(TAG, "Session rejected by user")
                                try {
                                    reject()
                                    logd(TAG, "Session rejection sent successfully")
                                } catch (e: Exception) {
                                    loge(TAG, "Error during session rejection: ${e.message}")
                                    loge(e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        loge(TAG, "Error showing dialog: ${e.message}")
                        loge(e)
                        // Try again with a different approach if dialog fails
                        try {
                            // Use a simpler dialog approach as fallback
                            AlertDialog.Builder(foundActivity)
                                .setTitle(sessionProposal.name)
                                .setMessage(sessionProposal.description)
                                .setPositiveButton(R.string.connect) { _, _ ->
                                    isSessionApproved = true
                                    logd(TAG, "Session approved via fallback dialog")
                                    try {
                                        sessionProposal.approveSession()
                                        logd(TAG, "Session approval sent successfully via fallback")
                                    } catch (e: Exception) {
                                        loge(TAG, "Error during fallback session approval: ${e.message}")
                                        loge(e)
                                        uiScope {
                                            toast(R.string.wallet_connect_approval_error)
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.cancel) { _, _ ->
                                    logd(TAG, "Session rejected via fallback dialog")
                                    try {
                                        sessionProposal.reject()
                                        logd(TAG, "Session rejection sent successfully via fallback")
                                    } catch (e: Exception) {
                                        loge(TAG, "Error during fallback session rejection: ${e.message}")
                                        loge(e)
                                    }
                                }
                                .show()
                        } catch (e2: Exception) {
                            loge(TAG, "Error showing fallback dialog: ${e2.message}")
                            loge(e2)
                            try {
                                sessionProposal.reject()
                                logd(TAG, "Session rejected due to dialog failure")
                            } catch (e3: Exception) {
                                loge(TAG, "Error rejecting session after fallback failure: ${e3.message}")
                                loge(e3)
                            }
                            toast(R.string.wallet_connect_proposal_error)
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
                    logd(TAG, "Session rejected due to error")
                } catch (e: Exception) {
                    loge(TAG, "Error rejecting session: ${e.message}")
                    loge(e)
                }
            }
        }
    }


    private fun isSessionTopicValid(topic: String): Boolean {
        val session = SignClient.getActiveSessionByTopic(topic)
        val isValid = session != null
        if (!isValid) {
            loge(TAG, "Attempted to respond on stale or unknown topic: $topic")
        }
        return isValid
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

        if (!isSessionTopicValid(sessionRequest.topic)) {
            loge(TAG, "Stale session topic detected in sessionRequest. Dropping request.")
            return
        }

        logd(TAG, "onSessionRequest() sessionRequest:${Gson().toJson(sessionRequest)}")

        val redirect = SignClient.getActiveSessionByTopic(sessionRequest.topic)?.redirect
        if (!redirect.isNullOrEmpty()) {
            logd(TAG, "Found redirect URL for session: $redirect")
        }

        lastActiveTopic = sessionRequest.topic
        isProcessingRequest = true
        ioScope {
            try {
                sessionRequest.toWcRequest().dispatch()
            } finally {
                isProcessingRequest = false
            }
        }
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

    /**
     * Determines if a session proposal is requesting EVM network support
     * by analyzing the required and optional namespaces
     */
    private fun isEVMSessionProposal(sessionProposal: Sign.Model.SessionProposal): Boolean {
        try {
            logd(TAG, "Analyzing session proposal for EVM indicators")
            
            // Check required namespaces for EVM chains
            sessionProposal.requiredNamespaces.forEach { (namespace, requirement) ->
                logd(TAG, "Checking required namespace: $namespace")
                
                // EIP-155 namespace indicates Ethereum/EVM
                if (namespace.equals("eip155", ignoreCase = true)) {
                    logd(TAG, "Found EIP-155 namespace - this is an EVM request")
                    return true
                }
                
                // Check for specific EVM chain IDs in the chains
                requirement.chains?.forEach { chain ->
                    logd(TAG, "Checking chain: $chain")
                    if (isEVMChain(chain)) {
                        logd(TAG, "Found EVM chain: $chain")
                        return true
                    }
                }
            }
            
            // Check optional namespaces for EVM chains
            sessionProposal.optionalNamespaces?.forEach { (namespace, requirement) ->
                logd(TAG, "Checking optional namespace: $namespace")
                
                if (namespace.equals("eip155", ignoreCase = true)) {
                    logd(TAG, "Found EIP-155 in optional namespace - this is an EVM request")
                    return true
                }
                
                requirement.chains?.forEach { chain ->
                    logd(TAG, "Checking optional chain: $chain")
                    if (isEVMChain(chain)) {
                        logd(TAG, "Found EVM chain in optional: $chain")
                        return true
                    }
                }
            }
            
            logd(TAG, "No EVM indicators found - treating as Flow request")
            return false
            
        } catch (e: Exception) {
            loge(TAG, "Error analyzing session proposal: ${e.message}")
            loge(e)
            // If we can't determine, default to false (Flow)
            return false
        }
    }
    
    /**
     * Checks if a chain identifier represents an EVM-compatible chain
     */
    private fun isEVMChain(chain: String): Boolean {
        return when {
            // Standard Ethereum chains
            chain.contains("eip155:1", ignoreCase = true) -> true     // Ethereum Mainnet
            chain.contains("eip155:5", ignoreCase = true) -> true     // Goerli Testnet
            chain.contains("eip155:11155111", ignoreCase = true) -> true // Sepolia Testnet
            
            // Flow EVM chains
            chain.contains("eip155:747", ignoreCase = true) -> true   // Flow EVM Mainnet
            chain.contains("eip155:545", ignoreCase = true) -> true   // Flow EVM Testnet
            
            // Other common EVM chains
            chain.contains("eip155:137", ignoreCase = true) -> true   // Polygon
            chain.contains("eip155:56", ignoreCase = true) -> true    // BSC
            
            // Generic EVM indicators
            chain.contains("ethereum", ignoreCase = true) -> true
            chain.contains("evm", ignoreCase = true) -> true
            
            else -> false
        }
    }
}