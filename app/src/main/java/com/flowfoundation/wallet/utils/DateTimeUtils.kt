package com.flowfoundation.wallet.utils

import android.annotation.SuppressLint
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("SimpleDateFormat")
fun String.gmtToTs(): Long {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(this)!!.time
}

@SuppressLint("SimpleDateFormat")
fun Long.formatDate(format: String = "yyyy-MM-dd HH:mm"): String {
    return SimpleDateFormat(format).apply { timeZone = TimeZone.getTimeZone("GMT") }.format(this)
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

fun isToday(ts: Long): Boolean {
    if (Math.abs(System.currentTimeMillis() - ts) > DateUtils.DAY_IN_MILLIS) {
        return false
    }

    val other = Calendar.getInstance().apply {
        timeInMillis = ts
    }

    val now = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
    }

    return now.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}

fun Long.plusDays(days: Int): Long {
    return this + DateUtils.DAY_IN_MILLIS * days
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

fun Long.plusWeek(plus: Int): Long {
    val calendar = Calendar.getInstance().apply {
        time = Date(this@plusWeek)
        add(Calendar.DATE, plus * 7)
    }
    return calendar.timeInMillis
}
