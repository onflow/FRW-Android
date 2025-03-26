package com.flowfoundation.wallet.utils

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class QRCodeUtilsTest {

    @Test
    fun `test toQRBitmap with valid input`() {
        val input = "https://example.com"
        val bitmap = input.toQRBitmap()
        
        assertNotNull(bitmap)
        assertEquals(500, bitmap?.width)
        assertEquals(500, bitmap?.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap?.config)
    }

    @Test
    fun `test toQRBitmap with custom dimensions`() {
        val input = "https://example.com"
        val width = 300
        val height = 300
        val bitmap = input.toQRBitmap(width = width, height = height)
        
        assertNotNull(bitmap)
        assertEquals(width, bitmap?.width)
        assertEquals(height, bitmap?.height)
    }

    @Test
    fun `test toQRBitmap with custom colors`() {
        val input = "https://example.com"
        val foregroundColor = Color.RED
        val backgroundColor = Color.BLUE
        val bitmap = input.toQRBitmap(
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor
        )
        
        assertNotNull(bitmap)
        // Check if the bitmap contains the custom colors
        val pixels = IntArray(bitmap!!.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        assertTrue(pixels.contains(foregroundColor))
        assertTrue(pixels.contains(backgroundColor))
    }

    @Test
    fun `test toQRBitmap with empty string returns null`() {
        val input = ""
        val bitmap = input.toQRBitmap()
        
        assertNull(bitmap)
    }

    @Test
    fun `test toQRBitmap with null string returns null`() {
        val input: String? = null
        val bitmap = input?.toQRBitmap()
        
        assertNull(bitmap)
    }

    @Test
    fun `test toQRBitmap with very long string`() {
        val input = "a".repeat(1000) // Very long string
        val bitmap = input.toQRBitmap()
        
        assertNotNull(bitmap)
        assertEquals(500, bitmap?.width)
        assertEquals(500, bitmap?.height)
    }

    @Test
    fun `test toQRBitmap with special characters`() {
        val input = "!@#$%^&*()_+{}|:\"<>?~`-=[]\\;',./"
        val bitmap = input.toQRBitmap()
        
        assertNotNull(bitmap)
        assertEquals(500, bitmap?.width)
        assertEquals(500, bitmap?.height)
    }

    @Test
    fun `test toQRBitmap with unicode characters`() {
        val input = "Hello 世界 🌍"
        val bitmap = input.toQRBitmap()
        
        assertNotNull(bitmap)
        assertEquals(500, bitmap?.width)
        assertEquals(500, bitmap?.height)
    }
} 