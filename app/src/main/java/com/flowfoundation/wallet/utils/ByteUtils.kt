package com.flowfoundation.wallet.utils

import java.text.DecimalFormat

private const val BYTE = 1L
private const val KB = BYTE * 1000
private const val MB = KB * 1000
private const val GB = MB * 1000
private const val TB = GB * 1000
private const val PB = TB * 1000
private const val EB = PB * 1000

private val DEC_FORMAT = DecimalFormat("#.##")

private fun formatSize(size: Long, divider: Long, unitName: String): String {
    return DEC_FORMAT.format(size.toDouble() / divider) + " " + unitName
}


fun toHumanReadableBinaryPrefixes(size: Long): String {
    require(size >= 0) { "Invalid file size: $size" }
    if (size >= EiB) return formatSize(size, EiB, "EiB")
    if (size >= PiB) return formatSize(size, PiB, "PiB")
    if (size >= TiB) return formatSize(size, TiB, "TiB")
    if (size >= GiB) return formatSize(size, GiB, "GiB")
    if (size >= MiB) return formatSize(size, MiB, "MiB")
    return if (size >= KiB) formatSize(size, KiB, "KiB") else formatSize(size, BYTE, "Bytes")
}
