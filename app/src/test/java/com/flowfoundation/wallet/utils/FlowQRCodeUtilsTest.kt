package com.flowfoundation.wallet.utils

import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.assertj.core.api.Assertions.assertThat

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class FlowQRCodeUtilsTest {

    @Before
    fun setup() {
        // Setup Env singleton with test context
        val context = ApplicationProvider.getApplicationContext()
        Env.setup(context)
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