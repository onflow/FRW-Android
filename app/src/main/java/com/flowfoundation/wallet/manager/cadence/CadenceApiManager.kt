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
                logd(TAG, "Signature received: ${signature.take(50)}...")
                val responseBody = rawResponse.body()?.let { body ->
                    body.string().also {
                        body.close()
                    }
                } ?: ""
                logd(TAG, "Response body length: ${responseBody.length}")
                
                val isSignatureValid = try {
                    verifySignature(signature, responseBody.toByteArray())
                } catch (e: Exception) {
                    loge(TAG, "Error verifying signature: ${e.message}")
                    loge(TAG, "Signature (first 100 chars): ${signature.take(100)}")
                    ErrorReporter.reportWithMixpanel(CadenceError.SIGNATURE_VERIFICATION_ERROR, e)
                    false
                }
                
                if (!isSignatureValid) {
                    loge(TAG, "Invalid script signature - continuing with cached scripts")
                    loge(TAG, "Response preview (first 200 chars): ${responseBody.take(200)}")
                    ErrorReporter.reportWithMixpanel(CadenceError.INVALID_SCRIPT_SIGNATURE)
                    // Don't return here - continue with existing cached scripts
                } else {
                    // Only update scripts if signature is valid
                    try {
                        val response = Gson().fromJson(responseBody, CadenceScriptResponse::class.java)
                        if (response?.data == null) {
                            loge(TAG, "Decode script failed - response data is null")
                            ErrorReporter.reportWithMixpanel(CadenceError.DECODE_SCRIPT_FAILED)
                            return@ioScope
                        }
                        val localVersion = cadenceApi?.version?.toSafeFloat() ?: 0f
                        val currentVersion = response.data.version.toSafeFloat()
                        logd(TAG, "cadenceScriptVersion::local::$localVersion::current::$currentVersion")
                        if (currentVersion > localVersion) {
                            cadenceApi = response.data
                            saveCadenceToLocal(Gson().toJson(response))
                            logd(TAG, "Updated to new script version: $currentVersion")
                        } else {
                            logd(TAG, "Current version ($currentVersion) not newer than local ($localVersion), keeping cached scripts")
                        }
                    } catch (e: Exception) {
                        loge(TAG, "Failed to parse response JSON: ${e.message}")
                        ErrorReporter.reportWithMixpanel(CadenceError.DECODE_SCRIPT_FAILED, e)
                    }
                }

                // Always report version info (even if signature failed)
                MixpanelManager.cadenceScriptVersion(getCadenceScriptVersion(), getCadenceVersion())
            } catch (e: Exception) {
                loge(TAG, "Network fetch failed: ${e.message}")
                ErrorReporter.reportWithMixpanel(CadenceError.FETCH_SCRIPT_FAILED, e)
                e.printStackTrace()
            }
        }
    }

    private fun verifySignature(signature: String, data: ByteArray): Boolean {
        return try {
            // Validate signature format before decoding
            if (signature.isBlank()) {
                loge(TAG, "Empty signature provided for verification")
                return false
            }
            
            // Clean the signature - remove any whitespace and non-hex characters
            val cleanSignature = signature.trim().replace(Regex("[^0-9a-fA-F]"), "")
            
            if (cleanSignature.isBlank()) {
                loge(TAG, "Signature contains no valid hex characters: ${signature.take(50)}...")
                return false
            }
            
            // Check if signature contains only valid hex characters
            val hexPattern = "^[0-9a-fA-F]+$".toRegex()
            if (!hexPattern.matches(cleanSignature)) {
                loge(TAG, "Invalid signature format after cleaning - contains non-hex characters: ${cleanSignature.take(50)}...")
                return false
            }
            
            // Validate signature length (should be even number for hex)
            if (cleanSignature.length % 2 != 0) {
                loge(TAG, "Invalid signature length - not even number of hex chars: ${cleanSignature.length}")
                return false
            }
            
            // Validate expected signature length for ECDSA (typically 64 bytes = 128 hex chars)
            if (cleanSignature.length != 128) {
                logd(TAG, "Warning: Unexpected signature length: ${cleanSignature.length} (expected 128)")
                // Don't fail here, just log warning as signature lengths may vary
            }
            
            logd(TAG, "Signature validation passed - original length: ${signature.length}, cleaned length: ${cleanSignature.length}")
            
            val hashedData = Hash.sha256(data)
            val pubKeyBytes = BuildConfig.X_SIGNATURE_KEY.decodeHex().toByteArray()
            val public = PublicKey(pubKeyBytes, PublicKeyType.NIST256P1EXTENDED)
            
            val signatureBytes = cleanSignature.decodeHex().toByteArray()
            val isValid = public.verify(signatureBytes, hashedData)
            
            logd(TAG, "Signature verification result: $isValid")
            return isValid
        } catch (e: IllegalArgumentException) {
            loge(TAG, "Invalid hex format in signature: ${e.message}")
            loge(TAG, "Signature (first 100 chars): ${signature.take(100)}")
            return false
        } catch (e: Exception) {
            loge(TAG, "Error verifying signature: ${e.message}")
            loge(TAG, "Signature (first 100 chars): ${signature.take(100)}")
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
        val script = getCadenceScript()?.basic?.get(method)?.decodeBase64()?.utf8()
        if (script.isNullOrBlank()) {
            loge(TAG, "Failed to get basic script for method: $method, falling back to assets")
            return getFallbackScriptFromAssets(method, "basic")
        }
        return script
    }

    fun getCadenceAccountScript(method: String): String {
        val script = getCadenceScript()?.account?.get(method)?.decodeBase64()?.utf8()
        if (script.isNullOrBlank()) {
            loge(TAG, "Failed to get account script for method: $method, falling back to assets")
            return getFallbackScriptFromAssets(method, "account")
        }
        return script
    }

    fun getCadenceCollectionScript(method: String): String {
        val script = getCadenceScript()?.collection?.get(method)?.decodeBase64()?.utf8()
        if (script.isNullOrBlank()) {
            loge(TAG, "Failed to get collection script for method: $method, falling back to assets")
            return getFallbackScriptFromAssets(method, "collection")
        }
        return script
    }

    fun getCadenceFTScript(method: String): String {
        val script = getCadenceScript()?.ft?.get(method)?.decodeBase64()?.utf8()
        if (script.isNullOrBlank()) {
            loge(TAG, "Failed to get FT script for method: $method, falling back to assets")
            return getFallbackScriptFromAssets(method, "ft")
        }
        return script
    }

    fun getCadenceContractScript(method: String): String {
        return getCadenceScript()?.contract?.get(method)?.decodeBase64()?.utf8() ?: ""
    }

    fun getCadenceDomainScript(method: String): String {
        return getCadenceScript()?.domain?.get(method)?.decodeBase64()?.utf8() ?: ""
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

    private fun getFallbackScriptFromAssets(method: String, category: String): String {
        return try {
            val assetsData = Gson().fromJson(
                readTextFromAssets(ASSETS_CADENCE_FILE_PATH),
                CadenceScriptResponse::class.java
            )
            val script = when (chainNetwork()) {
                NETWORK_TESTNET -> assetsData.data?.scripts?.testnet
                else -> assetsData.data?.scripts?.mainnet
            }
            
            val scriptContent = when (category) {
                "basic" -> script?.basic?.get(method)
                "account" -> script?.account?.get(method)
                "collection" -> script?.collection?.get(method)
                "ft" -> script?.ft?.get(method)
                else -> null
            }?.decodeBase64()?.utf8()
            
            scriptContent ?: run {
                loge(TAG, "No fallback script found for method: $method, category: $category")
                ""
            }
        } catch (e: Exception) {
            loge(TAG, "Failed to load fallback script from assets: ${e.message}")
            ""
        }
    }
}
