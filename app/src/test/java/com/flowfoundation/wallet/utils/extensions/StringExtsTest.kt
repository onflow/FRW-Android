package com.flowfoundation.wallet.utils.extensions

import org.junit.Assert.*
import org.junit.Test

class StringExtsTest {

    @Test
    fun testIndexOfAll() {
        // Test single occurrence
        assertEquals(listOf(1), "abc".indexOfAll("b"))
        
        // Test multiple occurrences
        assertEquals(listOf(0, 2, 4), "ababab".indexOfAll("a"))
        
        // Test no occurrences
        assertEquals(emptyList<Int>(), "abc".indexOfAll("x"))
        
        // Test empty string
        assertEquals(emptyList<Int>(), "".indexOfAll("a"))
        
        // Test longer substring
        assertEquals(listOf(1), "abcd".indexOfAll("bc"))
        
        // Test overlapping matches
        assertEquals(listOf(0, 1), "aaa".indexOfAll("aa"))
    }

    @Test
    fun testCapitalizeV2() {
        // Test lowercase first letter
        assertEquals("Hello", "hello".capitalizeV2())
        
        // Test already capitalized
        assertEquals("Hello", "Hello".capitalizeV2())
        
        // Test empty string
        assertEquals("", "".capitalizeV2())
        
        // Test single character
        assertEquals("A", "a".capitalizeV2())
        
        // Test non-letter first character
        assertEquals("123abc", "123abc".capitalizeV2())
        
        // Test special characters
        assertEquals("@hello", "@hello".capitalizeV2())
    }

    @Test
    fun testRemoveUrlParams() {
        // Test URL with single parameter
        assertEquals("https://example.com/path", "https://example.com/path?param=value".removeUrlParams())
        
        // Test URL with multiple parameters
        assertEquals("https://example.com/path", "https://example.com/path?param1=value1&param2=value2".removeUrlParams())
        
        // Test URL without parameters
        assertEquals("https://example.com/path", "https://example.com/path".removeUrlParams())
        
        // Test empty string
        assertEquals("", "".removeUrlParams())
        
        // Test URL with only question mark
        assertEquals("https://example.com/path", "https://example.com/path?".removeUrlParams())
        
        // Test URL with hash
        assertEquals("https://example.com/path", "https://example.com/path?param=value#hash".removeUrlParams())
    }

    @Test
    fun testUrlHost() {
        // Test simple URL
        assertEquals("example.com", "https://example.com".urlHost())
        
        // Test URL with path
        assertEquals("example.com", "https://example.com/path".urlHost())
        
        // Test URL with subdomain
        assertEquals("sub.example.com", "https://sub.example.com".urlHost())
        
        // Test URL with parameters
        assertEquals("example.com", "https://example.com?param=value".urlHost())
        
        // Test invalid URL
        assertEquals("not-a-url", "not-a-url".urlHost())
        
        // Test empty string
        assertEquals("", "".urlHost())
    }

    @Test
    fun testMd5() {
        // Test simple string
        assertEquals("5d41402abc4b2a76b9719d911017c592", "hello".md5())
        
        // Test empty string
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", "".md5())
        
        // Test string with special characters
        assertEquals("96c05e63f5343c93781d6ed541c7c5f8", "!@#$%^&*()".md5())
        
        // Test numbers
        assertEquals("202cb962ac59075b964b07152d234b70", "123".md5())
        
        // Test longer string
        assertEquals("d3b07384d113edec49eaa6238ad5ff00", "Hello, this is a test string".md5())
    }
} 