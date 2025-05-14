package com.flowfoundation.wallet.manager.walletconnect

import android.app.Application
import com.flowfoundation.wallet.BuildConfig
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.uiScope
import com.reown.android.internal.common.scope
import com.reown.android.relay.WSSConnectionState
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.widget.Toast
import android.view.Gravity
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.utils.toast
import kotlinx.coroutines.withTimeoutOrNull

private val TAG = WalletConnect::class.java.simpleName

private const val projectId = BuildConfig.WALLET_CONNECT_PROJECT_ID

@OptIn(DelicateCoroutinesApi::class)
class WalletConnect {

    private val isConnectionAvailable: StateFlow<Boolean> by lazy {
        combine(CoreClient.Relay.isNetworkAvailable, CoreClient.Relay.wssConnectionState) {
            networkAvailable, wss -> networkAvailable == true && wss is WSSConnectionState.Connected
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )
    }

    private var isInitializing = false
    private var pairingInProgress = false

    suspend fun waitForInitialization(timeoutMs: Long = 10000): Boolean {
        if (!isInitialized()) {
            logd(TAG, "WalletConnect not initialized. Waiting for initialization...")
            return withTimeoutOrNull(timeoutMs) {
                var waitTime = 100L
                var attempts = 0
                val maxAttempts = 20

                while (!isInitialized() && attempts < maxAttempts) {
                    logd(TAG, "Waiting for WalletConnect initialization, attempt ${attempts + 1} of $maxAttempts (waiting ${waitTime}ms)")
                    delay(waitTime)
                    attempts++
                    waitTime = minOf(waitTime * 2, 1000)
                }

                isInitialized()
            } ?: false
        }
        return true
    }

    fun pair(uri: String) {
        if (pairingInProgress) {
            logd(TAG, "Pairing already in progress, ignoring new pairing request")
            return
        }
        
        pairingInProgress = true
        
        // Show connecting toast immediately when pairing starts
        val activity = BaseActivity.getCurrentActivity()
        if (activity != null) {
            uiScope {
                val toast = Toast.makeText(activity, R.string.connecting, Toast.LENGTH_SHORT)
                toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 0)
                toast.show()
            }
        }
        
        ioScope {
            try {
                // First, ensure WalletConnect is initialized
                val initialized = waitForInitialization()
                if (!initialized) {
                    loge(TAG, "WalletConnect initialization timeout. Cannot proceed with pairing.")
                    uiScope {
                        toast(R.string.wallet_connect_initialization_error)
                    }
                    pairingInProgress = false
                    return@ioScope
                }
                
                // Clean up all active sessions before pairing
                try {
                    cleanupActiveSessions()
                } catch (e: Exception) {
                    loge(TAG, "Error cleaning up sessions before pairing: ${e.message}")
                    loge(e)
                    // Continue with pairing anyway
                }
                
                // Add a short delay to ensure cleanup has time to complete
                delay(500)

                logd(TAG, "CoreClient.Relay isConnectionAvailable: ${isConnectionAvailable.value}")
                
                if (!isConnectionAvailable.value) {
                    logd(TAG, "Connection not available, attempting to establish connection")
                    // Try to establish connection
                    var connected = false
                    for (attempt in 1..3) {
                        logd(TAG, "Attempting to connect relay (attempt $attempt of 3)")
                        safeRun {
                            CoreClient.Relay.connect { error: Core.Model.Error ->
                                loge(TAG, "CoreClient.Relay connect error: $error")
                            }
                        }
                        
                        // Wait for connection to establish
                        for (i in 1..5) {
                            if (isConnectionAvailable.value) {
                                connected = true
                                break
                            }
                            delay(300)
                        }
                        
                        if (connected) break
                    }
                    
                    if (!connected) {
                        logd(TAG, "Failed to establish connection after multiple attempts")
                        // Try pairing anyway as a last resort
                    } else {
                        // Add a short delay after connection is established
                        delay(500)
                    }
                }
                
                // Proceed with pairing
                logd(TAG, "Attempting to pair with URI: $uri")
                try {
                    val pairingParams = Core.Params.Pair(uri)
                    CoreClient.Pairing.pair(pairingParams) { error ->
                        loge(TAG, "Pairing error: ${error.throwable}")
                        uiScope {
                            try {
                                toast(R.string.wallet_connect_pairing_error)
                                logd(TAG, "Showed pairing error toast")
                            } catch (e: Exception) {
                                loge(TAG, "Failed to show pairing error toast: ${e.message}")
                                loge(e)
                            }
                        }
                    }
                    logd(TAG, "Pairing request sent successfully")
                    
                    // Check if sessions were established after a short delay
                    delay(1000)
                    val sessions = SignClient.getListOfActiveSessions()
                    logd(TAG, "Active sessions after pairing attempt: ${sessions.size}")
                } catch (e: Exception) {
                    loge(TAG, "Pairing exception: ${e.message}")
                    loge(e)
                    uiScope {
                        try {
                            toast(R.string.wallet_connect_pairing_error)
                            logd(TAG, "Showed pairing exception toast")
                        } catch (e: Exception) {
                            loge(TAG, "Failed to show pairing exception toast: ${e.message}")
                            loge(e)
                        }
                    }
                } finally {
                    pairingInProgress = false
                }
            } catch (e: Exception) {
                loge(TAG, "Unexpected error during pairing process: ${e.message}")
                loge(e)
                pairingInProgress = false
            }
        }
    }

    private suspend fun cleanupActiveSessions() {
        val activeSessions = SignClient.getListOfActiveSessions()
        logd(TAG, "Cleaning up all active sessions before pairing. Current count: ${activeSessions.size}")
        activeSessions.forEach { session ->
            logd(TAG, "Disconnecting session before pairing: ${session.topic}")
            SignClient.disconnect(Sign.Params.Disconnect(sessionTopic = session.topic)) { error ->
                loge(TAG, "Error disconnecting session: ${error.throwable}")
            }
            // Add a small delay between disconnects to prevent overwhelming the system
            delay(100)
        }
    }

    fun sessionCount(): Int = sessions().size

    fun sessions() = SignClient.getListOfActiveSessions().filter { it.metaData != null }

    fun disconnect(topic: String) {
        SignClient.disconnect(
            Sign.Params.Disconnect(sessionTopic = topic)
        ) { error -> loge(error.throwable) }
    }

    private fun initCombine() {
        GlobalScope.launch {
            isConnectionAvailable.collect { isConnected ->
                logd(TAG, "CoreClient.Relay connect change:$isConnected")
                if (!isConnected) {
                    safeRun {
                        CoreClient.Relay.connect { error: Core.Model.Error -> loge(TAG, "CoreClient.Relay connect error: $error") }
                    }
                }
            }
        }
    }

    companion object {
        private var instance: WalletConnect? = null
        private var initializationStarted = false

        fun init(application: Application) {
            if (initializationStarted) {
                logd(TAG, "WalletConnect initialization already started, skipping")
                return
            }
            
            initializationStarted = true
            ioScope {
                try {
                    setup(application)
                    instance = WalletConnect().apply {
                        initCombine()
                    }
                    logd(TAG, "WalletConnect successfully initialized")
                } catch (e: Exception) {
                    loge(TAG, "Error initializing WalletConnect: ${e.message}")
                    loge(e)
                    initializationStarted = false
                }
            }
        }

        fun isInitialized() = instance != null

        fun get() = instance!!
    }
}

private fun setup(application: Application) {
    val appMetaData = Core.Model.AppMetaData(
        name = "Flow Wallet Android",
        description = "Digital wallet created for everyone.",
        url = "https://core.flow.com/",
        icons = listOf("https://lilico.app/fcw-logo.png"),
        redirect = null,
    )
    CoreClient.initialize(
        application = application,
        projectId = projectId,
        metaData = appMetaData,
        connectionType = ConnectionType.MANUAL
    ) {
        loge(TAG, "WalletConnect init error: $it")
    }

    SignClient.initialize(
        Sign.Params.Init(core = CoreClient),
        onSuccess = {
            CoreClient.Relay.connect { error: Core.Model.Error ->
                loge(TAG, "CoreClient.Relay connect error: $error")
            }
        }
    ) {
        loge(TAG, "SignClient init error: $it")
    }

    SignClient.setWalletDelegate(WalletConnectDelegate())
    SignClient.setDappDelegate(WalletDappDelegate())
}

fun getWalletConnectPendingRequests(): List<Sign.Model.SessionRequest> {
    return SignClient.getListOfActiveSessions().flatMap { SignClient.getPendingSessionRequests(it.topic) }
}