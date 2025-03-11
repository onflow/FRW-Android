package com.flowfoundation.wallet.utils

import org.junit.Assert.*
import org.junit.Test

class CommonUtilsTest {

    @Test
    fun testIsLegalAmountNumber() {
        // Test valid positive numbers
        assertTrue("1".isLegalAmountNumber())
        assertTrue("1.0".isLegalAmountNumber())
        assertTrue("0.1".isLegalAmountNumber())
        assertTrue("123.456".isLegalAmountNumber())
        assertTrue("999999.999".isLegalAmountNumber())
        
        // Test invalid numbers
        assertFalse("0".isLegalAmountNumber())
        assertFalse("-1".isLegalAmountNumber())
        assertFalse("-0.1".isLegalAmountNumber())
        assertFalse("0.0".isLegalAmountNumber())
        
        // Test invalid input
        assertFalse("".isLegalAmountNumber())
        assertFalse("abc".isLegalAmountNumber())
        assertFalse("1.2.3".isLegalAmountNumber())
        assertFalse("1,234".isLegalAmountNumber())
    }

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