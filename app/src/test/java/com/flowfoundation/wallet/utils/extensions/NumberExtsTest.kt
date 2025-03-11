package com.flowfoundation.wallet.utils.extensions

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NumberExtsTest {

    @Test
    fun testOrZero() {
        // Test Int?.orZero()
        val nullInt: Int? = null
        assertEquals(0, nullInt.orZero())
        assertEquals(42, 42.orZero())
    }

    @Test
    fun testToSafeInt() {
        // Test null
        assertEquals(0, null.toSafeInt())
        assertEquals(42, null.toSafeInt(42))
        
        // Test empty string
        assertEquals(0, "".toSafeInt())
        assertEquals(42, "".toSafeInt(42))
        
        // Test blank string
        assertEquals(0, "   ".toSafeInt())
        assertEquals(42, "   ".toSafeInt(42))
        
        // Test valid integers
        assertEquals(123, "123".toSafeInt())
        assertEquals(-123, "-123".toSafeInt())
        
        // Test invalid input
        assertEquals(0, "abc".toSafeInt())
        assertEquals(42, "abc".toSafeInt(42))
        assertEquals(0, "12.34".toSafeInt())
    }

    @Test
    fun testToSafeFloat() {
        // Test null
        assertEquals(0f, null.toSafeFloat(), 0f)
        assertEquals(42f, null.toSafeFloat(42f), 0f)
        
        // Test empty string
        assertEquals(0f, "".toSafeFloat(), 0f)
        assertEquals(42f, "".toSafeFloat(42f), 0f)
        
        // Test blank string
        assertEquals(0f, "   ".toSafeFloat(), 0f)
        assertEquals(42f, "   ".toSafeFloat(42f), 0f)
        
        // Test valid floats
        assertEquals(123.45f, "123.45".toSafeFloat(), 0.001f)
        assertEquals(-123.45f, "-123.45".toSafeFloat(), 0.001f)
        
        // Test invalid input
        assertEquals(0f, "abc".toSafeFloat(), 0f)
        assertEquals(42f, "abc".toSafeFloat(42f), 0f)
    }

    @Test
    fun testToSafeDouble() {
        // Test null
        assertEquals(0.0, null.toSafeDouble(), 0.0)
        assertEquals(42.0, null.toSafeDouble(42.0), 0.0)
        
        // Test valid doubles
        assertEquals(123.45, "123.45".toSafeDouble(), 0.001)
        assertEquals(-123.45, "-123.45".toSafeDouble(), 0.001)
        
        // Test invalid input
        assertEquals(0.0, "abc".toSafeDouble(), 0.0)
        assertEquals(42.0, "abc".toSafeDouble(42.0), 0.0)
    }


    @Test
    fun testToSafeDecimal() {
        // Test null
        assertEquals(BigDecimal.ZERO, null.toSafeDecimal())
        assertEquals(BigDecimal("42"), null.toSafeDecimal(BigDecimal("42")))
        
        // Test empty string
        assertEquals(BigDecimal.ZERO, "".toSafeDecimal())
        assertEquals(BigDecimal("42"), "".toSafeDecimal(BigDecimal("42")))
        
        // Test blank string
        assertEquals(BigDecimal.ZERO, "   ".toSafeDecimal())
        assertEquals(BigDecimal("42"), "   ".toSafeDecimal(BigDecimal("42")))
        
        // Test valid decimals
        assertEquals(BigDecimal("123.45"), "123.45".toSafeDecimal())
        assertEquals(BigDecimal("-123.45"), "-123.45".toSafeDecimal())
        
        // Test invalid input
        assertEquals(BigDecimal.ZERO, "abc".toSafeDecimal())
        assertEquals(BigDecimal("42"), "abc".toSafeDecimal(BigDecimal("42")))
    }
} 