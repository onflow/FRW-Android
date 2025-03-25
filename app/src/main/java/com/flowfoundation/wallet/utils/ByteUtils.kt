package com.flowfoundation.wallet.utils

import android.icu.text.DecimalFormat

private const val BYTE = 1L
private const val KB = BYTE * 1000
private const val MB = KB * 1000
private const val GB = MB * 1000
private const val TB = GB * 1000
private const val PB = TB * 1000
private const val EB = PB * 1000

private val DEC_FORMAT: DecimalFormat = DecimalFormat("#.##")

private fun formatSize(size: Long, divider: Long, unitName: String): String {
    return DEC_FORMAT.format(size.toDouble() / divider).toString() + " " + unitName
}


fun toHumanReadableSIPrefixes(size: Long): String {
    require(size >= 0) { "Invalid file size: $size" }
    if (size >= EB) return formatSize(size, EB, "EB")
    if (size >= PB) return formatSize(size, PB, "PB")
    if (size >= TB) return formatSize(size, TB, "TB")
    if (size >= GB) return formatSize(size, GB, "GB")
    if (size >= MB) return formatSize(size, MB, "MB")
    return if (size >= KB) formatSize(size, KB, "KB") else formatSize(size, BYTE, "Bytes")
}
