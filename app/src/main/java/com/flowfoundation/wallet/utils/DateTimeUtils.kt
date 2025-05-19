package com.flowfoundation.wallet.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("SimpleDateFormat")
fun String.gmtToTs(): Long {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(this).time
}

@SuppressLint("SimpleDateFormat")
fun Long.formatDate(format: String = "yyyy-MM-dd HH:mm"): String {
    return SimpleDateFormat(format).format(this)
}

fun formatGMTToDate(inputDate: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)

        val date = inputFormat.parse(inputDate)
        date?.let { outputFormat.format(it) } ?: inputDate
    } catch (e: Exception) {
        e.printStackTrace()
        inputDate
    }
}

fun Long.plusMonth(plus: Int): Long {
    val calendar = Calendar.getInstance().apply {
        time = Date(this@plusMonth)
        add(Calendar.MONTH, plus)
    }
    return calendar.timeInMillis
}

fun Long.plusYear(plus: Int): Long {
    val calendar = Calendar.getInstance().apply {
        time = Date(this@plusYear)
        add(Calendar.YEAR, plus)
    }
    return calendar.timeInMillis
}
