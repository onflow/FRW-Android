package com.flowfoundation.wallet.utils.extensions

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.util.DisplayMetrics
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.Env
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntExtsTest {

    private val mockDisplayMetrics = DisplayMetrics().apply {
        density = 2.0f // Simulate a common device density (2.0 = xhdpi)
    }

    private val mockResources: Resources = mock {
        on { displayMetrics } doReturn mockDisplayMetrics
        on { getDimensionPixelSize(any()) } doReturn 48 // Example pixel size
        on { getDimension(any()) } doReturn 24f // Example dimension in dp
    }

    private val mockContext: Context = mock {
        on { resources } doReturn mockResources
        on { getString(R.string.app_name) } doReturn "Test App"
        on { getColor(any()) } doReturn Color.RED
    }

    @Before
    fun setup() {
        Env.init(mockContext)
    }

    @Test
    fun `test res2pix converts resource to pixels`() {
        val resId = R.dimen.some_dimension // Example dimension resource ID
        val result = resId.res2pix()
        assertThat(result).isEqualTo(48)
    }

    @Test
    fun `test res2dip converts resource to dp`() {
        val resId = R.dimen.some_dimension
        val result = resId.res2dip()
        assertThat(result).isEqualTo(12f) // 24dp / 2.0 density = 12dp
    }

    @Test
    fun `test dp2px converts dp to pixels`() {
        val dpValue = 10
        val expectedPx = dpValue * mockDisplayMetrics.density + 0.5f
        val result = dpValue.dp2px()
        assertThat(result).isEqualTo(expectedPx)
    }

    @Test
    fun `test res2String converts resource to string`() {
        val resId = R.string.app_name
        val result = resId.res2String()
        assertThat(result).isEqualTo("Test App")
    }

    @Test
    fun `test res2color returns color with context`() {
        val colorResId = R.color.some_color
        val result = colorResId.res2color(mockContext)
        assertThat(result).isEqualTo(Color.RED)
    }

    @Test
    fun `test res2color returns color without context`() {
        val colorResId = R.color.some_color
        val result = colorResId.res2color()
        assertThat(result).isEqualTo(Color.RED)
    }

    @Test
    fun `test alpha modifies color alpha channel`() {
        val color = Color.RED // Original color
        val alpha = 0.5f // 50% opacity
        
        val result = color.alpha(alpha)
        
        val expectedAlpha = (alpha * 255).toInt()
        assertThat(Color.alpha(result)).isEqualTo(expectedAlpha)
        assertThat(Color.red(result)).isEqualTo(Color.red(color))
        assertThat(Color.green(result)).isEqualTo(Color.green(color))
        assertThat(Color.blue(result)).isEqualTo(Color.blue(color))
    }

    @Test
    fun `test alpha with full opacity`() {
        val color = Color.BLUE
        val result = color.alpha(1.0f)
        
        assertThat(Color.alpha(result)).isEqualTo(255)
        assertThat(Color.blue(result)).isEqualTo(Color.blue(color))
    }

    @Test
    fun `test alpha with zero opacity`() {
        val color = Color.GREEN
        val result = color.alpha(0.0f)
        
        assertThat(Color.alpha(result)).isEqualTo(0)
        assertThat(Color.green(result)).isEqualTo(Color.green(color))
    }

    @Test
    fun `test Float dp2px converts dp to pixels`() {
        val dpValue = 10.5f
        val expectedPx = dpValue * mockDisplayMetrics.density + 0.5f
        val result = dpValue.dp2px()
        assertThat(result).isEqualTo(expectedPx)
    }
} 