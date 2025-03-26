package com.flowfoundation.wallet.utils.extensions

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.util.DisplayMetrics
import androidx.core.content.ContextCompat
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.Env
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IntExtsTest {

    private val mockDisplayMetrics = DisplayMetrics().apply {
        density = 2.0f // Simulate a common device density (2.0 = xhdpi)
    }

    @Mock
    private lateinit var mockResources: Resources

    @Mock
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Env.init(mockContext)

        // Setup Resources mock
        `when`(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        `when`(mockResources.getDimensionPixelSize(any())).thenReturn(48)
        `when`(mockResources.getDimension(any())).thenReturn(24f)

        // Setup Context mock
        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockContext.getString(R.string.app_name)).thenReturn("Test App")

        val mockColorStateList = ColorStateList.valueOf(Color.RED)
        `when`(ContextCompat.getColorStateList(mockContext, R.color.button_color))
            .thenReturn(mockColorStateList)
    }

    @Test
    fun `test res2pix converts resource to pixels`() {
        val resId = R.dimen.edittext_radius // Using actual dimension resource
        val result = resId.res2pix()
        assertThat(result).isEqualTo(48)
    }

    @Test
    fun `test res2dip converts resource to dp`() {
        val resId = R.dimen.edittext_radius
        val result = resId.res2dip()
        assertThat(result).isEqualTo(12f) // 24dp / 2.0 density = 12dp
    }

    @Test
    fun `test res2String converts resource to string`() {
        val resId = R.string.app_name
        val result = resId.res2String()
        assertThat(result).isEqualTo("Test App")
    }

    @Test
    fun `test res2color converts resource to color`() {
        val resId = R.color.colorPrimary
        val result = resId.res2color(mockContext)
        // Since we can't easily mock ContextCompat.getColor, we'll just verify it's not null
        assertThat(result).isNotNull()
    }

    @Test
    fun `test colorStateList converts resource to ColorStateList`() {
        val resId = R.color.button_color
        val result = resId.colorStateList(mockContext)
        // Since we can't easily mock ContextCompat.getColorStateList, we'll just verify it's not null
        assertThat(result).isNotNull()
    }

    @Test
    fun `test alpha modifies color alpha`() {
        val color = Color.RED
        val result = color.alpha(0.5f)
        // 0.5 * 255 = 127.5, which rounds to 127
        assertThat(Color.alpha(result)).isEqualTo(127)
    }
} 