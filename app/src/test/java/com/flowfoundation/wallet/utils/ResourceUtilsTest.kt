package com.flowfoundation.wallet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.MockedStatic
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ResourceUtilsTest {

    private lateinit var context: Context
    @Mock
    private lateinit var mockDrawable: Drawable

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `test drawableResToBitmap with valid resource`() {
        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val testDrawable = BitmapDrawable(context.resources, testBitmap)

        // Mock ContextCompat inside the test to avoid static mock conflicts
        val mockedStatic: MockedStatic<ContextCompat> = mockStatic(ContextCompat::class.java)
        try {
            `when`(ContextCompat.getDrawable(eq(context), anyInt())).thenReturn(testDrawable)

            val result = drawableResToBitmap(context, 123)

            assertNotNull(result)
            result?.let {
                assertEquals(100, it.width)
                assertEquals(100, it.height)
                assertEquals(Bitmap.Config.ARGB_8888, it.config)
            }
        } finally {
            mockedStatic.close() // Always close static mocks after the test
        }
    }

    @Test
    fun `test drawableResToBitmap with invalid resource returns null`() {
        val mockedStatic: MockedStatic<ContextCompat> = mockStatic(ContextCompat::class.java)
        try {
            `when`(ContextCompat.getDrawable(eq(context), anyInt())).thenReturn(null)

            val result = drawableResToBitmap(context, 123)

            assertNull(result)
        } finally {
            mockedStatic.close()
        }
    }

    @Test
    fun `test toBitmap with BitmapDrawable`() {
        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val bitmapDrawable = BitmapDrawable(context.resources, testBitmap)

        val result = bitmapDrawable.toBitmap()

        assertNotNull(result)
        result.let {
            assertEquals(100, it.width)
            assertEquals(100, it.height)
            assertEquals(Bitmap.Config.ARGB_8888, it.config)
        }
    }

    @Test
    fun `test toBitmap with ColorDrawable`() {
        val colorDrawable = ColorDrawable(Color.RED)
        colorDrawable.setBounds(0, 0, 100, 100)

        val result = colorDrawable.toBitmap()

        assertNotNull(result)
        result.let {
            assertEquals(100, it.width)
            assertEquals(100, it.height)
            assertEquals(Bitmap.Config.ARGB_8888, it.config)

            // Verify the color
            val pixels = IntArray(100 * 100)
            it.getPixels(pixels, 0, 100, 0, 0, 100, 100)
            assertTrue(pixels.all { pixel -> pixel == Color.RED })
        }
    }
}
