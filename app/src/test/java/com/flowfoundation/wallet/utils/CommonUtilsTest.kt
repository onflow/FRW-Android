package com.flowfoundation.wallet.utils

import org.junit.Assert.*
import org.junit.Test

class CommonUtilsTest {

    @Test
    fun testSafeRun() {
        // Test successful execution
        var result = 0
        safeRun {
            result = 42
        }
        assertEquals(42, result)
        
        // Test exception handling
        safeRun {
            throw RuntimeException("Test exception")
        }

        // Test with printLog = false
        safeRun(printLog = false) {
            throw RuntimeException("Test exception")
        }
    }
} 