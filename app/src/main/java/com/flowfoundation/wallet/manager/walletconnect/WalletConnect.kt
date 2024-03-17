package com.flowfoundation.wallet.manager.walletconnect

import android.app.Application
import com.flowfoundation.wallet.R
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import com.flowfoundation.wallet.manager.env.EnvKey
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.toast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TAG = WalletConnect::class.java.simpleName

private val projectId by lazy { EnvKey.get("WALLET_CONNECT_PROJECT_ID") }

@OptIn(DelicateCoroutinesApi::class)
class WalletConnect {

    init {
        GlobalScope.launch {
            CoreClient.Relay.isConnectionAvailable.collect { isConnected ->
                logd(TAG, "CoreClient.Relay connect change:$isConnected")
                if (!isConnected) {
                    safeRun {
                        CoreClient.Relay.connect { error: Core.Model.Error -> logw(TAG, "CoreClient.Relay connect error: $error") }
                    }
                }
            }
        }
    }

    fun pair(uri: String) {
        logd(TAG, "CoreClient.Relay isConnectionAvailable :${CoreClient.Relay.isConnectionAvailable.value}")
        if (!CoreClient.Relay.isConnectionAvailable.value) {
            var job: kotlinx.coroutines.Job? = null
            job = ioScope {
                CoreClient.Relay.isConnectionAvailable.collect { isConnected ->
                    if (isConnected) {
                        delay(1000)
                        logd(TAG, "Pair on connected")
                        paring(uri)
                        job?.cancel()
                    } else {
                        safeRun {
                            CoreClient.Relay.connect { error: Core.Model.Error ->
                                logw(TAG, "CoreClient.Relay connect error: $error")
                            }
                        }
                        delay(1000)
                        paring(uri)
                        job?.cancel()
                    }
                }
            }
        } else {
            paring(uri)
        }
    }

    private fun paring(uri: String) {
        val pairingParams = Core.Params.Pair(uri)
        CoreClient.Pairing.pair(pairingParams) { error ->
            toast(msgRes = R.string.wallet_connect_error)
            loge(error.throwable)
        }
    }

    fun sessionCount(): Int = sessions().size

    fun sessions() = SignClient.getListOfActiveSessions().filter { it.metaData != null }

    fun disconnect(topic: String) {
        SignClient.disconnect(
            Sign.Params.Disconnect(sessionTopic = topic)
        ) { error -> loge(error.throwable) }
    }

    companion object {
        private var instance: WalletConnect? = null

        fun init(application: Application) {
            ioScope {
                setup(application)
                instance = WalletConnect()
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
        metaData = appMetaData,
        relayServerUrl = "wss://relay.walletconnect.com?projectId=${projectId}",
        connectionType = ConnectionType.MANUAL,
        application = application,
    ) {
        logw(TAG, "WalletConnect init error: $it")
    }

    SignClient.initialize(
        Sign.Params.Init(core = CoreClient),
        onSuccess = {
            CoreClient.Relay.connect { error: Core.Model.Error ->
                logw(TAG, "CoreClient.Relay connect error: $error")
            }
        }
    ) {
        logw(TAG, "SignClient init error: $it")
    }

    SignClient.setWalletDelegate(WalletConnectDelegate())
    SignClient.setDappDelegate(WalletDappDelegate())

//    RelayClient.connect { error -> logw(TAG, "connect error:$error") }
}

fun getWalletConnectPendingRequests(): List<Sign.Model.PendingRequest> {
    return SignClient.getListOfSettledSessions().map { SignClient.getPendingRequests(it.topic) }.flatten()
}