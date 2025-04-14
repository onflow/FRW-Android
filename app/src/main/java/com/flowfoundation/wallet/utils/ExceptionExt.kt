package com.flowfoundation.wallet.utils


fun getCurrentCodeLocation(extra: String? = null): String {
    val stackTraceElement = Throwable().stackTrace[1]
    return "${stackTraceElement.fileName}:${stackTraceElement.lineNumber}" + (extra?.let { ":$it" } ?: "")
}

fun Throwable.getLocationInfo(): String {
    val stackTraceElement = this.stackTrace.firstOrNull()
    val fileName = stackTraceElement?.fileName ?: "Unknown"
    val lineNumber = stackTraceElement?.lineNumber ?: -1
    return "$fileName:$lineNumber"
}