package com.flowfoundation.wallet.utils

import org.junit.Assert.*
import org.junit.Test

class ByteUtilsTest {

    @Test
    fun testToHumanReadableBinaryPrefixes() {
        // Test bytes
        assertEquals("1 Bytes", toHumanReadableBinaryPrefixes(1))
        assertEquals("1023 Bytes", toHumanReadableBinaryPrefixes(1023))
        
        // Test KiB
        assertEquals("1 KiB", toHumanReadableBinaryPrefixes(1024))
        assertEquals("1.5 KiB", toHumanReadableBinaryPrefixes(1536))  // 1.5 * 1024
        
        // Test MiB
        assertEquals("1 MiB", toHumanReadableBinaryPrefixes(1024 * 1024))
        assertEquals("1.5 MiB", toHumanReadableBinaryPrefixes((1.5 * 1024 * 1024).toLong()))
        
        // Test GiB
        assertEquals("1 GiB", toHumanReadableBinaryPrefixes(1024L * 1024L * 1024L))
        assertEquals("1.5 GiB", toHumanReadableBinaryPrefixes((1.5 * 1024 * 1024 * 1024).toLong()))
        
        // Test TiB
        assertEquals("1 TiB", toHumanReadableBinaryPrefixes(1024L * 1024L * 1024L * 1024L))
        
        // Test invalid input
        assertThrows(IllegalArgumentException::class.java) {
            toHumanReadableBinaryPrefixes(-1)
        }
    }

    @Test
    fun testToHumanReadableSIPrefixes() {
        // Test bytes
        assertEquals("1 Bytes", toHumanReadableSIPrefixes(1))
        assertEquals("999 Bytes", toHumanReadableSIPrefixes(999))
        
        // Test KB
        assertEquals("1 KB", toHumanReadableSIPrefixes(1000))
        assertEquals("1.5 KB", toHumanReadableSIPrefixes(1500))
        
        // Test MB
        assertEquals("1 MB", toHumanReadableSIPrefixes(1000 * 1000))
        assertEquals("1.5 MB", toHumanReadableSIPrefixes((1.5 * 1000 * 1000).toLong()))
        
        // Test GB
        assertEquals("1 GB", toHumanReadableSIPrefixes(1000L * 1000L * 1000L))
        assertEquals("1.5 GB", toHumanReadableSIPrefixes((1.5 * 1000 * 1000 * 1000).toLong()))
        
        // Test TB
        assertEquals("1 TB", toHumanReadableSIPrefixes(1000L * 1000L * 1000L * 1000L))
        
        // Test invalid input
        assertThrows(IllegalArgumentException::class.java) {
            toHumanReadableSIPrefixes(-1)
        }
    }
} 