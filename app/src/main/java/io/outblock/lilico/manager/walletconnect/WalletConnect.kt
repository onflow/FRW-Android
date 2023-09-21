package io.outblock.lilico.manager.walletconnect

import android.app.Application
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.android.relay.RelayClient
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import io.outblock.lilico.manager.env.EnvKey
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.loge
import io.outblock.lilico.utils.logw
import io.outblock.lilico.utils.safeRun
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
            RelayClient.isConnectionAvailable.collect { isConnected ->
                logd(TAG, "RelayClient connect change:$isConnected")
                if (!isConnected) {
                    safeRun {
                        RelayClient.connect { error: Core.Model.Error -> logw(TAG, "RelayClient connect error: $error") }
                    }
                }
            }
        }
    }

    fun pair(uri: String) {
        logd(TAG, "RelayClient isConnectionAvailable :${RelayClient.isConnectionAvailable.value}")
        if (!RelayClient.isConnectionAvailable.value) {
            var job: kotlinx.coroutines.Job? = null
            job = ioScope {
                RelayClient.isConnectionAvailable.collect { isConnected ->
                    if (isConnected) {
                        delay(1000)
                        logd(TAG, "Pair on connected")
                        val pairingParams = Core.Params.Pair(uri)
                        CoreClient.Pairing.pair(pairingParams) { error -> loge(error.throwable) }
                        job?.cancel()
                    }
                }
            }
        } else {
            val pairingParams = Core.Params.Pair(uri)
            CoreClient.Pairing.pair(pairingParams) { error -> loge(error.throwable) }
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
        name = "Flow Reference Wallet Android",
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
            RelayClient.connect { error: Core.Model.Error ->
                logw(TAG, "RelayClient connect error: $error")
            }
        }
    ) {
        logw(TAG, "SignClient init error: $it")
    }

    SignClient.setWalletDelegate(WalletConnectDelegate())

//    RelayClient.connect { error -> logw(TAG, "connect error:$error") }
}

fun getWalletConnectPendingRequests(): List<Sign.Model.PendingRequest> {
    return SignClient.getListOfSettledSessions().map { SignClient.getPendingRequests(it.topic) }.flatten()
}