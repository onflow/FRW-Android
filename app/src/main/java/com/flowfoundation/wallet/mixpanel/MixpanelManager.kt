package com.flowfoundation.wallet.mixpanel

import android.annotation.SuppressLint
import android.app.Application
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.cadence.CadenceApiManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.isTesting
import com.flowfoundation.wallet.utils.loge
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.walletconnect.android.internal.common.crypto.sha256
import org.json.JSONArray
import org.json.JSONObject


object MixpanelManager {

    private val TAG = MixpanelManager::class.java.simpleName

    @SuppressLint("StaticFieldLeak")
    private lateinit var mixpanel: MixpanelAPI

    fun init(application: Application) {
        val superObj = JSONObject().apply {
            put(
                KEY_APP_ENV, if (isTesting() || isDev()) {
                    MixpanelEnv.DEV.value
                } else {
                    MixpanelEnv.PROD.value
                }
            )
            put(KEY_FLOW_NETWORK, chainNetWorkString())
            put(KEY_FW_DEVICE_ID, DeviceInfoManager.getDeviceID())
        }
        val token = if (isTesting() || isDev()) {
            BuildConfig.MIXPANEL_TOKEN_DEV
        } else {
            BuildConfig.MIXPANEL_TOKEN_PROD
        }
        mixpanel = MixpanelAPI.getInstance(application, token, superObj, true)
    }

    fun networkChange() {
        val networkObj = JSONObject().apply {
            put(KEY_FLOW_NETWORK, chainNetWorkString())
            put(KEY_CADENCE_VERSION, CadenceApiManager.getCadenceVersion())
        }
        registerSuperProperties(networkObj)
    }

    fun cadenceScriptVersion(scriptVersion: String, version: String) {
        val versionObj = JSONObject().apply {
            put(KEY_CADENCE_SCRIPT_VERSION, scriptVersion)
            put(KEY_CADENCE_VERSION, version)
        }
        registerSuperProperties(versionObj)
    }

    fun identifyUserProfile() {
        identify(
            firebaseUid() ?: WalletManager.wallet()?.id ?: AccountManager.userInfo()?.username ?: ""
        )
    }

    fun scriptError(scriptName: String, errorMsg: String) {
        val properties = JSONObject().apply {
            put(KEY_SCRIPT_ID, scriptName)
            put(KEY_ERROR, errorMsg)
        }
        trackEvent(EVENT_SCRIPT_ERROR, properties)
    }

    fun delegationCreated(address: String, nodeId: String, amount: String) {
        val properties = JSONObject().apply {
            put(KEY_ADDRESS, address)
            put(KEY_NODE_ID, nodeId)
            put(KEY_AMOUNT, amount)
        }
        trackEvent(EVENT_DELEGATION_CREATED, properties)
    }

    fun onRampClicked(rampSource: MixpanelRampSource) {
        val properties = JSONObject().apply {
            put(KEY_SOURCE, rampSource.value)
        }
        trackEvent(EVENT_ON_RAMP_CLICKED, properties)
    }

    fun coaCreation(txId: String, errorMsg: String? = null) {
        val properties = JSONObject().apply {
            put(KEY_TX_ID, txId)
            put(KEY_FLOW_ADDRESS, WalletManager.wallet()?.walletAddress())
            put(KEY_ERROR_MESSAGE, errorMsg.orEmpty())
        }
        trackEvent(EVENT_COA_CREATION, properties)
    }

    fun securityTool(tool: MixpanelSecurityTool) {
        val properties = JSONObject().apply {
            put(KEY_TYPE, tool.value)
        }
        trackEvent(EVENT_SECURITY_TOOL, properties)
    }

    fun multiBackupCreated(provider: MixpanelBackupProvider? = null) {
        trackMultiBackupEvent(EVENT_MULTI_BACKUP_CREATED, provider)
    }

    fun multiBackupCreationFailed(provider: MixpanelBackupProvider? = null) {
        trackMultiBackupEvent(EVENT_MULTI_BACKUP_CREATION_FAILED, provider)
    }

    fun cadenceTransactionSigned(
        cadence: String, txId: String, authorizers: List<String>,
        proposer: String, payer: String, isSuccess: Boolean
    ) {
        val properties = JSONObject().apply {
            put(KEY_SHA256_CADENCE, sha256(cadence.toByteArray()))
            put(KEY_TX_ID, txId)
            put(KEY_AUTHORIZERS, authorizers)
            put(KEY_PROPOSER, proposer)
            put(KEY_PAYER, payer)
            put(KEY_SUCCESS, isSuccess.toString())
        }
        trackEvent(EVENT_CADENCE_TRANSACTION_SIGNED, properties)
    }

    fun evmTransactionSigned(
        txId: String, flowAddress: String, evmAddress: String, isSuccess: Boolean
    ) {
        val properties = JSONObject().apply {
            put(KEY_TX_ID, txId)
            put(KEY_FLOW_ADDRESS, flowAddress)
            put(KEY_EVM_ADDRESS, evmAddress)
            put(KEY_SUCCESS, isSuccess.toString())
        }
        trackEvent(EVENT_EVM_TRANSACTION_SIGNED, properties)
    }

    fun transferFT(
        fromAddress: String, toAddress: String, symbol: String, amount: String, ftIdentifier: String
    ) {
        val properties = JSONObject().apply {
            put(KEY_FROM_ADDRESS, fromAddress)
            put(KEY_TO_ADDRESS, toAddress)
            put(KEY_TYPE, symbol)
            put(KEY_AMOUNT, amount)
            put(KEY_FT_IDENTIFIER, ftIdentifier)
        }
        trackEvent(EVENT_FT_TRANSFER, properties)
    }

    fun transferNFT(
        fromAddress: String, toAddress: String, nftIdentifier: String, txId: String,
        fromType: TransferAccountType, toType: TransferAccountType, isMove: Boolean
    ) {
        val properties = JSONObject().apply {
            put(KEY_FROM_ADDRESS, fromAddress)
            put(KEY_TO_ADDRESS, toAddress)
            put(KEY_TX_ID, txId)
            put(KEY_TRANSFER_FROM_TYPE, fromType.value)
            put(KEY_TRANSFER_TO_TYPE, toType.value)
            put(KEY_NFT_IDENTIFIER, nftIdentifier)
            put(KEY_IS_MOVE_ACTION, isMove.toString())
        }
        trackEvent(EVENT_NFT_TRANSFER, properties)
    }

    fun transactionResult(txId: String, isSuccess: Boolean, errorMsg: String? = null) {
        val properties = JSONObject().apply {
            put(KEY_TX_ID, txId)
            put(KEY_IS_SUCCESSFUL, isSuccess.toString())
            put(KEY_ERROR_MESSAGE, errorMsg.orEmpty())
        }
        trackEvent(EVENT_TRANSACTION_RESULT, properties)
    }

    fun accountCreated(
        publicKey: String, keyType: AccountCreateKeyType, signAlgo: String,
        hashAlgo: String
    ) {
        val properties = JSONObject().apply {
            put(KEY_PUBLIC_KEY, publicKey)
            put(KEY_PUBLIC_KEY_TYPE, keyType.value)
            put(KEY_SIGN_ALGO, signAlgo)
            put(KEY_HASH_ALGO, hashAlgo)
        }
        trackEvent(EVENT_ACCOUNT_CREATED, properties)
    }

    fun accountCreationStart() {
        if (this::mixpanel.isInitialized) {
            mixpanel.timeEvent(EVENT_ACCOUNT_CREATION_TIME)
        } else {
            loge(TAG, "Mixpanel is not initialized")
        }
    }

    fun accountCreationFinish() {
        trackEvent(EVENT_ACCOUNT_CREATION_TIME)
    }

    fun accountRestore(
        address: String, restoreType: RestoreType
    ) {
        val properties = JSONObject().apply {
            put(KEY_ADDRESS, address)
            put(KEY_RESTORE_MECHANISM, restoreType.value)
            if (restoreType == RestoreType.MULTI_BACKUP) {
                put(
                    KEY_RESTORE_MULTI_METHODS, JSONArray(
                        listOf(
                            MixpanelBackupProvider.GOOGLE_DRIVE.value,
                            MixpanelBackupProvider.SEED_PHRASE.value
                        )
                    )
                )
            }
        }
        trackEvent(EVENT_ACCOUNT_RECOVERED, properties)
    }

    private fun trackMultiBackupEvent(eventName: String, provider: MixpanelBackupProvider?) {
        val properties = JSONObject().apply {
            put(KEY_ADDRESS, WalletManager.wallet()?.walletAddress())
            put(
                KEY_PROVIDERS,
                JSONArray(
                    provider?.let { listOf(it.value) } ?: listOf(
                        MixpanelBackupProvider.GOOGLE_DRIVE.value,
                        MixpanelBackupProvider.SEED_PHRASE.value
                    )
                )
            )
        }
        trackEvent(eventName, properties)
    }

    private fun identify(userId: String) {
        if (this::mixpanel.isInitialized) {
            mixpanel.identify(userId)
        } else {
            loge(TAG, "Mixpanel is not initialized")
        }
    }

    private fun registerSuperProperties(properties: JSONObject) {
        if (this::mixpanel.isInitialized) {
            mixpanel.registerSuperProperties(properties)
        } else {
            loge(TAG, "Mixpanel is not initialized")
        }
    }

    private fun trackEvent(eventName: String, properties: JSONObject? = null) {
        if (this::mixpanel.isInitialized) {
            mixpanel.track(eventName, properties)
        } else {
            loge(TAG, "Mixpanel is not initialized")
        }
    }
}