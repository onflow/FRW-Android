package com.flowfoundation.wallet.utils


fun getCurrentCodeLocation(extra: String? = null): String {
    val stackTrace = Throwable().stackTrace
    val currentFileName = stackTrace[0].fileName

    val caller = stackTrace.firstOrNull { element ->
        element.fileName != currentFileName
    } ?: stackTrace.firstOrNull()

    return "${caller?.fileName ?: "Unknown"}:${caller?.lineNumber ?: -1}" + (extra?.let { ":$it" } ?: "")
}

fun Throwable.getLocationInfo(): String {
    val stackTraceElement = this.stackTrace.firstOrNull()
    val fileName = stackTraceElement?.fileName ?: "Unknown"
    val lineNumber = stackTraceElement?.lineNumber ?: -1
    return "$fileName:$lineNumber"
}