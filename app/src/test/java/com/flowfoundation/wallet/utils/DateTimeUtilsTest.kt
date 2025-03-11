package com.flowfoundation.wallet.utils

import android.text.format.DateUtils
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class DateTimeUtilsTest {

    @Test
    fun testGmtToTs() {
        val gmtString = "2024-03-11T12:00:00.000Z"
        val expectedTs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
            .parse(gmtString)?.time

        val actualTs = gmtString.gmtToTs()
        
        assertEquals(expectedTs, actualTs)
    }

    @Test
    fun testFormatDate() {
        val timestamp = 1710151200000L // 2024-03-11 12:00:00 GMT
        val expectedFormat = SimpleDateFormat("yyyy-MM-dd HH:mm").apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(timestamp)
        
        val formattedDate = timestamp.formatDate()
        
        assertEquals(expectedFormat, formattedDate)
    }

    @Test
    fun testFormatGMTToDate() {
        val gmtString = "2024-03-11T12:00:00.000Z"
        val expectedFormat = "March 11, 2024"
        
        val formattedDate = formatGMTToDate(gmtString)
        
        assertEquals(expectedFormat, formattedDate)
    }

    @Test
    fun testPlusMonth() {
        val timestamp = 1710151200000L // 2024-03-11 12:00:00 GMT
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            add(Calendar.MONTH, 1)
        }
        val expectedTimestamp = calendar.timeInMillis
        
        val resultTimestamp = timestamp.plusMonth(1)
        
        assertEquals(expectedTimestamp, resultTimestamp)
    }

    @Test
    fun testPlusYear() {
        val timestamp = 1710151200000L // 2024-03-11 12:00:00 GMT
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            add(Calendar.YEAR, 1)
        }
        val expectedTimestamp = calendar.timeInMillis
        
        val resultTimestamp = timestamp.plusYear(1)
        
        assertEquals(expectedTimestamp, resultTimestamp)
    }
}