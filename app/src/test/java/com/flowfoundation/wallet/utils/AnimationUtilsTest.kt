package com.flowfoundation.wallet.utils

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnimationUtilsTest {

    private lateinit var mockView: View

    @Before
    fun setup() {
        mockView = mock()
    }

    @Test
    fun `test createScaleObjectAnimation with default parameters`() {
        val animator = createScaleObjectAnimation(mockView, 1.0f, 2.0f)

        // Test duration
        assertThat(animator.duration).isEqualTo(200L)

        // Test interpolator
        assertThat(animator.interpolator).isInstanceOf(FastOutSlowInInterpolator::class.java)

        // Test property value holders
        val values = animator.values
        assertThat(values).hasSize(2)

        val scaleX = values.find { it.propertyName == "scaleX" }
        val scaleY = values.find { it.propertyName == "scaleY" }

        assertThat(scaleX).isNotNull
        assertThat(scaleY).isNotNull

        // Test animation values
        assertThat(getFloatValues(scaleX!!)).containsExactly(1.0f, 2.0f)
        assertThat(getFloatValues(scaleY!!)).containsExactly(1.0f, 2.0f)
    }

    @Test
    fun `test createScaleObjectAnimation with custom parameters`() {
        val customDuration = 500L
        val customInterpolator = LinearInterpolator()
        
        val animator = createScaleObjectAnimation(
            view = mockView,
            from = 0.5f,
            to = 1.5f,
            duration = customDuration,
            interpolator = customInterpolator
        )

        // Test custom duration
        assertThat(animator.duration).isEqualTo(customDuration)

        // Test custom interpolator
        assertThat(animator.interpolator).isEqualTo(customInterpolator)

        // Test property value holders
        val values = animator.values
        assertThat(values).hasSize(2)

        val scaleX = values.find { it.propertyName == "scaleX" }
        val scaleY = values.find { it.propertyName == "scaleY" }

        assertThat(scaleX).isNotNull
        assertThat(scaleY).isNotNull

        // Test animation values
        assertThat(getFloatValues(scaleX!!)).containsExactly(0.5f, 1.5f)
        assertThat(getFloatValues(scaleY!!)).containsExactly(0.5f, 1.5f)
    }

    @Test
    fun `test createScaleObjectAnimation with zero duration`() {
        val animator = createScaleObjectAnimation(
            view = mockView,
            from = 1.0f,
            to = 2.0f,
            duration = 0L
        )

        assertThat(animator.duration).isEqualTo(0L)
    }

    @Test
    fun `test createScaleObjectAnimation with same from and to values`() {
        val value = 1.0f
        val animator = createScaleObjectAnimation(
            view = mockView,
            from = value,
            to = value
        )

        val values = animator.values
        val scaleX = values.find { it.propertyName == "scaleX" }
        val scaleY = values.find { it.propertyName == "scaleY" }

        assertThat(getFloatValues(scaleX!!)).containsExactly(value, value)
        assertThat(getFloatValues(scaleY!!)).containsExactly(value, value)
    }

    @Test
    fun `test createScaleObjectAnimation target object`() {
        val animator = createScaleObjectAnimation(mockView, 1.0f, 2.0f)
        assertThat(animator.target).isEqualTo(mockView)
    }

    private fun getFloatValues(holder: PropertyValuesHolder): FloatArray {
        val view = mock<View>()
        val animator = ObjectAnimator.ofPropertyValuesHolder(view, holder)
        animator.start() // Start the animation

        val startValue = animator.getAnimatedValue(holder.propertyName) as? Float ?: 0f
        animator.currentPlayTime = animator.duration // Move to end
        val endValue = animator.getAnimatedValue(holder.propertyName) as? Float ?: 0f

        return floatArrayOf(startValue, endValue)
    }

} 