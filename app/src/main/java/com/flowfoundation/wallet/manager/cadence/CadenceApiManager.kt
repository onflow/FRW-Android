package com.flowfoundation.wallet.manager.cadence

import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.app.chainNetwork
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.cadenceScriptApi
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.NETWORK_TESTNET
import com.flowfoundation.wallet.utils.error.CadenceError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.extensions.toSafeFloat
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.read
import com.flowfoundation.wallet.utils.readTextFromAssets
import com.flowfoundation.wallet.utils.saveToFile
import com.google.gson.Gson
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import wallet.core.jni.Hash
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import java.io.File


object CadenceApiManager {

    private val TAG = CadenceApiManager::class.java.simpleName
    private const val LOCAL_CADENCE_FILE_NAME = "local_cadence.json"
    private const val ASSETS_CADENCE_FILE_PATH = "config/cadence_api.json"
    private var cadenceApi: CadenceScriptData? = null
    private const val SIGNATURE_HEADER = "x-signature"

    fun init() {
        loadCadenceFromLocal()
    }

    private fun loadCadenceFromLocal() {
        try {
            logd(TAG, "loadCadenceFromLocal")
            val file = File(Env.getApp().filesDir, LOCAL_CADENCE_FILE_NAME)
            cadenceApi = if (file.exists()) {
                val fileData = Gson().fromJson(file.read(), CadenceScriptResponse::class.java)
                fileData.data
            } else {
                val assetsData = Gson().fromJson(
                    readTextFromAssets(ASSETS_CADENCE_FILE_PATH),
                    CadenceScriptResponse::class.java
                )
                assetsData.data
            }
            fetchCadenceFromNetwork()
        } catch (e: Exception) {
            e.printStackTrace()
            ErrorReporter.reportWithMixpanel(CadenceError.LOAD_SCRIPT_FAILED, e)
            fetchCadenceFromNetwork()
        }
    }

    private fun saveCadenceToLocal(json: String) {
        json.saveToFile(File(Env.getApp().filesDir, LOCAL_CADENCE_FILE_NAME))
    }

    private fun fetchCadenceFromNetwork() {
        ioScope {
            try {
                logd(TAG, "fetchCadenceFromNetwork")
                val rawResponse = cadenceScriptApi().create(ApiService::class.java).getCadenceScriptWithHeaders()
                val signature = rawResponse.headers()[SIGNATURE_HEADER]
                if (signature.isNullOrBlank()) {
                    loge(TAG, "Empty script signature")
                    ErrorReporter.reportWithMixpanel(CadenceError.EMPTY_SCRIPT_SIGNATURE)
                    return@ioScope
                }
                logd(TAG, "Signature: $signature")
                val responseBody = rawResponse.body()?.let { body ->
                    body.string().also {
                        body.close()
                    }
                } ?: ""
                logd(TAG, "Response: $responseBody")
                val isSignatureValid = try {
                    verifySignature(signature, responseBody.toByteArray())
                } catch (e: Exception) {
                    loge(TAG, "Error verifying signature: ${e.message}")
                    ErrorReporter.reportWithMixpanel(CadenceError.SIGNATURE_VERIFICATION_ERROR, e)
                    false
                }
                if (!isSignatureValid) {
                    loge(TAG, "Invalid script signature")
                    ErrorReporter.reportWithMixpanel(CadenceError.INVALID_SCRIPT_SIGNATURE)
                    return@ioScope
                }

                val response = Gson().fromJson(responseBody, CadenceScriptResponse::class.java)
                if (response.data == null) {
                    loge(TAG, "Decode script failed")
                    ErrorReporter.reportWithMixpanel(CadenceError.DECODE_SCRIPT_FAILED)
                    return@ioScope
                }
                val localVersion = cadenceApi?.version.toSafeFloat()
                val currentVersion = response.data.version.toSafeFloat()
                logd(TAG, "cadenceScriptVersion::local::$localVersion::current::$currentVersion")
                if (currentVersion > localVersion) {
                    cadenceApi = response.data
                    saveCadenceToLocal(Gson().toJson(response))
                }
                MixpanelManager.cadenceScriptVersion(getCadenceScriptVersion(), getCadenceVersion())
            } catch (e: Exception) {
                ErrorReporter.reportWithMixpanel(CadenceError.FETCH_SCRIPT_FAILED, e)
                e.printStackTrace()
            }
        }
    }

    private fun verifySignature(signature: String, data: ByteArray): Boolean {
        try {
            val hashedData = Hash.sha256(data)
            val pubKeyBytes = BuildConfig.X_SIGNATURE_KEY.decodeHex().toByteArray()
            val public = PublicKey(pubKeyBytes, PublicKeyType.NIST256P1EXTENDED)
            return public.verify(signature.decodeHex().toByteArray(), hashedData)
        } catch (e: Exception) {
            loge(TAG, "Error verifying signature: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun getCadenceScript(): CadenceScript? {
        return when (chainNetwork()) {
            NETWORK_TESTNET -> cadenceApi?.scripts?.testnet
            else -> cadenceApi?.scripts?.mainnet
        }
    }

    fun getCadenceVersion(): String {
        return getCadenceScript()?.version.orEmpty()
    }

    fun getCadenceScriptVersion(): String {
        return cadenceApi?.version.orEmpty()
    }

    fun getCadenceBasicScript(method: String): String {
        return getCadenceScript()?.basic?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceAccountScript(method: String): String {
        return getCadenceScript()?.account?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceCollectionScript(method: String): String {
        return getCadenceScript()?.collection?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceContractScript(method: String): String {
        return getCadenceScript()?.contract?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceDomainScript(method: String): String {
        return getCadenceScript()?.domain?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceFTScript(method: String): String {
        return getCadenceScript()?.ft?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceHybridCustodyScript(method: String): String {
        return getCadenceScript()?.hybridCustody?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceStakingScript(method: String): String {
        return getCadenceScript()?.staking?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceStorageScript(method: String): String {
        return getCadenceScript()?.storage?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceEVMScript(method: String): String {
        return getCadenceScript()?.evm?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceNFTScript(method: String): String {
        return getCadenceScript()?.nft?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceSwapScript(method: String): String {
        return (getCadenceScript()?.swap?.get(method) as? String)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceBridgeScript(method: String): String {
        return getCadenceScript()?.bridge?.get(method)?.decodeBase64()?.utf8() ?: ""
    }
}
