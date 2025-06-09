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

    fun pair(uri: String) {
        logd(TAG, "CoreClient.Relay isConnectionAvailable :${isConnectionAvailable.value}")
        if (!isConnectionAvailable.value) {
            var job: kotlinx.coroutines.Job? = null
            job = ioScope {
                var attempts = 0
                val maxAttempts = 10
                isConnectionAvailable.collect { isConnected ->
                    if (isConnected) {
                        delay(1000)
                        logd(TAG, "Pair on connected")
                        try {
                            val pairingParams = Core.Params.Pair(uri)
                            logd(TAG, "Attempting to pair with params: $pairingParams")
                            CoreClient.Pairing.pair(pairingParams) { error ->
                                loge(TAG, "Pairing error: ${error.throwable}")
                            }
                            logd(TAG, "Pairing completed, checking for active sessions")
                            val sessions = SignClient.getListOfActiveSessions()
                            logd(TAG, "Active sessions: ${sessions.size}")
                        } catch (e: Exception) {
                            loge(TAG, "Pairing exception: ${e.message}")
                            loge(e)
                        } finally {
                            job?.cancel()
                        }
                    } else {
                        attempts++
                        if (attempts >= maxAttempts) {
                            logd(TAG, "Pair on max attempts")
                            try {
                                val pairingParams = Core.Params.Pair(uri)
                                logd(TAG, "Attempting to pair with params: $pairingParams")
                                CoreClient.Pairing.pair(pairingParams) { error ->
                                    loge(TAG, "Pairing error: ${error.throwable}")
                                }
                                logd(TAG, "Pairing completed, checking for active sessions")
                                val sessions = SignClient.getListOfActiveSessions()
                                logd(TAG, "Active sessions: ${sessions.size}")
                            } catch (e: Exception) {
                                loge(TAG, "Pairing exception: ${e.message}")
                                loge(e)
                            } finally {
                                job?.cancel()
                            }
                            return@collect
                        }
                        safeRun {
                            CoreClient.Relay.connect { error: Core.Model.Error ->
                                loge(TAG, "CoreClient.Relay connect error: $error")
                            }
                        }
                        delay(1000)
                    }
                }
            }
        } else {
            try {
                val pairingParams = Core.Params.Pair(uri)
                logd(TAG, "Attempting to pair with params: $pairingParams")
                CoreClient.Pairing.pair(pairingParams) { error ->
                    loge(TAG, "Pairing error: ${error.throwable}")
                }
                logd(TAG, "Pairing completed, checking for active sessions")
                val sessions = SignClient.getListOfActiveSessions()
                logd(TAG, "Active sessions: ${sessions.size}")
            } catch (e: Exception) {
                loge(TAG, "Pairing exception: ${e.message}")
                loge(e)
            }
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

        fun init(application: Application) {
            ioScope {
                setup(application)
                instance = WalletConnect().apply {
                    initCombine()
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
        redirect = "flowwallet://wc",
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

//    RelayClient.connect { error -> logw(TAG, "connect error:$error") }
}

fun getWalletConnectPendingRequests(): List<Sign.Model.SessionRequest> {
    return SignClient.getListOfActiveSessions().flatMap { SignClient.getPendingSessionRequests(it.topic) }
}