package com.flowfoundation.wallet.manager.rotation

import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import androidx.core.content.edit

class BloctoDetectorService {
    data class Result(
        val isBlocto: Boolean,
        val needRevoke: Boolean,
        val revokeKeyIndexes: List<Int>
    )

    companion object {
        private const val PREF_NAME = "blocto_detector_cache"
        private const val TAG = "BloctoDetectorService"

        private fun cacheKey(address: String): String {
            return "blocto.detector.false.${address.lowercase()}"
        }

        suspend fun detectBloctoKey(address: String): Result {
            logd(TAG, "detectBloctoKey() called with address: $address")
            val normalized = address.lowercase()
            val prefs = Env.getApp().getSharedPreferences(PREF_NAME, 0)
            if (prefs.getBoolean(cacheKey(normalized), false)) {
                logd(TAG, "detectBloctoKey() - cached result found (not Blocto)")
                return Result(isBlocto = false, needRevoke = false, revokeKeyIndexes = emptyList())
            }

            val account = FlowCadenceApi.getAccount(normalized)
            val keys = account.keys ?: emptyList()
            logd(TAG, "detectBloctoKey() - fetched ${keys.size} keys for account")

            val candidateKeys = keys.filter { key ->
                key.signingAlgorithm == SigningAlgorithm.ECDSA_secp256k1 &&
                  key.hashingAlgorithm == HashingAlgorithm.SHA3_256
            }

            val hasWeight999 = candidateKeys.any { it.weight.toInt() == 999 }
            val hasWeight1 = candidateKeys.any { it.weight.toInt() == 1 }
            val isBlocto = hasWeight999 && hasWeight1
            val revokeKeyIndexes = candidateKeys
                .filter { !it.revoked }
                .map { it.index.toInt() }
            val needRevoke = isBlocto && revokeKeyIndexes.isNotEmpty()

            logd(TAG, "detectBloctoKey() - isBlocto: $isBlocto, needRevoke: $needRevoke, revokeKeyIndexes: $revokeKeyIndexes")

            if (!isBlocto) {
                prefs.edit { putBoolean(cacheKey(normalized), true) }
            }

            return Result(
                isBlocto = isBlocto,
                needRevoke = needRevoke,
                revokeKeyIndexes = revokeKeyIndexes
            )
        }
    }
}
