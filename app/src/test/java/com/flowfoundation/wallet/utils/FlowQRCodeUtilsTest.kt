package com.flowfoundation.wallet.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.ContextCompat
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2color
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.assertj.core.api.Assertions.assertThat

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class FlowQRCodeUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockResources: Resources
    private lateinit var mockDrawable: Drawable

    @Before
    fun setup() {
        mockDrawable = mock {
            on { intrinsicWidth } doReturn 100
            on { intrinsicHeight } doReturn 100
        }

        mockResources = mock {
            on { getColor(any(), any()) } doReturn 0xFF000000.toInt()
            on { getColor(any()) } doReturn 0xFF000000.toInt()
        }

        mockContext = mock {
            on { resources } doReturn mockResources
            on { getString(R.string.app_name) } doReturn "Test App"
        }

        whenever(ContextCompat.getDrawable(any(), any())).thenReturn(mockDrawable)
        whenever(mockContext.getColor(any())).thenReturn(0xFF000000.toInt())

        Env.init(mockContext)
    }

    @Test
    fun `test toQRDrawable creates valid drawable`() {
        val testAddress = "0x1234567890abcdef"
        val drawable = testAddress.toQRDrawable()
        assertThat(drawable).isInstanceOf(Drawable::class.java)
        assertThat(drawable.intrinsicWidth).isGreaterThan(0)
        assertThat(drawable.intrinsicHeight).isGreaterThan(0)
    }

    @Test
    fun `test toQRDrawable with scale parameter`() {
        val testAddress = "0x1234567890abcdef"
        val drawable = testAddress.toQRDrawable(withScale = true)
        assertThat(drawable).isInstanceOf(Drawable::class.java)
        assertThat(drawable.intrinsicWidth).isGreaterThan(0)
        assertThat(drawable.intrinsicHeight).isGreaterThan(0)
    }

    @Test
    fun `test toQRDrawable with EVM parameter`() {
        val testAddress = "0x1234567890abcdef"
        val drawable = testAddress.toQRDrawable(isEVM = true)
        assertThat(drawable).isInstanceOf(Drawable::class.java)
        assertThat(drawable.intrinsicWidth).isGreaterThan(0)
        assertThat(drawable.intrinsicHeight).isGreaterThan(0)
    }

    @Test
    fun `test toQRDrawable with empty string`() {
        val drawable = "".toQRDrawable()
        assertThat(drawable).isInstanceOf(Drawable::class.java)
        assertThat(drawable.intrinsicWidth).isGreaterThan(0)
        assertThat(drawable.intrinsicHeight).isGreaterThan(0)
    }

    @Test
    fun `test toQRDrawable with null string`() {
        val drawable = null?.toQRDrawable()
        assertThat(drawable).isInstanceOf(Drawable::class.java)
        assertThat(drawable?.intrinsicWidth).isGreaterThan(0)
        assertThat(drawable?.intrinsicHeight).isGreaterThan(0)
    }

    @Test
    fun `test toQRDrawable with long address`() {
        val testAddress = "0x" + "1234567890abcdef".repeat(4) // 64 characters
        val drawable = testAddress.toQRDrawable()
        assertThat(drawable).isInstanceOf(Drawable::class.java)
        assertThat(drawable.intrinsicWidth).isGreaterThan(0)
        assertThat(drawable.intrinsicHeight).isGreaterThan(0)
    }
} 