package com.flowfoundation.wallet.utils.error

import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.utils.getCurrentCodeLocation
import com.flowfoundation.wallet.utils.getLocationInfo
import ai.luciq.crash.CrashReporting
import ai.luciq.crash.models.LuciqNonFatalException
import ai.luciq.library.Luciq
import org.onflow.flow.infrastructure.CadenceErrorCode


object ErrorReporter {
    private fun report(error: BaseError, cause: Throwable? = null) {
        LuciqNonFatalException.Builder(CustomException(error, cause))
            .setLevel(LuciqNonFatalException.Level.ERROR)
            .build()
            .let { exception ->  CrashReporting.report(exception)}
    }

    fun reportMoveAssetsError(locationInfo: String) {
        reportWithMixpanel(MoveError.FAILED_TO_SUBMIT_TRANSACTION, locationInfo)
        Luciq.show()
    }

    fun reportWithMixpanel(error: BaseError, locationInfo: String) {
        report(error)
        MixpanelManager.error(error, locationInfo)
    }

    fun reportWithMixpanel(error: BaseError, cause: Throwable? = null) {
        if (cause == null) {
            reportWithMixpanel(error, getCurrentCodeLocation())
            return
        }
        report(error, cause)
        MixpanelManager.error(error, cause.getLocationInfo(), cause.message)
    }

    fun reportCriticalWithMixpanel(error: BaseError, cause: Throwable? = null) {
        val throwable = cause ?: CustomException(error)
        LuciqNonFatalException.Builder(throwable)
            .setLevel(LuciqNonFatalException.Level.CRITICAL)
            .build()
            .let { exception ->  CrashReporting.report(exception)}
        MixpanelManager.error(error, throwable.getLocationInfo(), throwable.message)
    }

    fun reportTransactionError(txId: String, errorCode: Int) {
        val scriptId = TransactionStateManager.getScriptId(txId)
        val errorMessage = "scriptId: $scriptId, txId: $txId"
        CadenceErrorCode.forErrorCode(errorCode)?.let { cadenceError ->
            // Map CadenceErrorCode to WalletError for consistent error reporting
            val walletError = WalletError(errorCode, cadenceError.name)
            val cause = CustomTransactionException(walletError, errorMessage)
            LuciqNonFatalException.Builder(cause)
                .setLevel(LuciqNonFatalException.Level.ERROR)
                .setFingerprint("$scriptId.$errorCode")
                .build()
                .let { exception ->  CrashReporting.report(exception)}
            MixpanelManager.error(walletError, cause.getLocationInfo(), cause.message)
        }
    }
}

class CustomException(
    val error: BaseError,
    cause: Throwable? = null
) : Throwable(error.errorLog, cause)

class CustomTransactionException(
    val error: WalletError,
    errorMessage: String
) : Throwable("${error.message}($errorMessage)")

class InvalidKeyException(
    errorMessage: String
) : Exception(errorMessage)