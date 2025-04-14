package com.flowfoundation.wallet.utils.error

import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.utils.getLocationInfo
import com.instabug.crash.CrashReporting
import com.instabug.crash.models.IBGNonFatalException


object ErrorReporter {
    private fun report(error: BaseError, cause: Throwable? = null) {
        IBGNonFatalException.Builder(CustomException(error, cause))
            .setLevel(IBGNonFatalException.Level.ERROR)
            .build()
            .let { exception ->  CrashReporting.report(exception)}
    }

    fun reportWithMixpanel(error: BaseError, locationInfo: String) {
        report(error)
        MixpanelManager.error(error, locationInfo)
    }

    fun reportWithMixpanel(error: BaseError, cause: Throwable) {
        report(error, cause)
        MixpanelManager.error(error, cause.getLocationInfo(), cause.message)
    }

    // private key and signature error
    fun reportCritical(error: BaseError, cause: Throwable? = null) {
        IBGNonFatalException.Builder(CustomException(error, cause))
           .setLevel(IBGNonFatalException.Level.CRITICAL)
           .build()
           .let { exception ->  CrashReporting.report(exception)}
    }
}

class CustomException(
    val error: BaseError,
    cause: Throwable? = null
) : Throwable(error.errorLog, cause)
