package com.flowfoundation.wallet.utils.extensions

import android.app.Activity
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewExtensionsTest {
    private lateinit var view: View
    private lateinit var activity: Activity

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        view = View(activity)
    }

    @Test
    fun `test isVisible extension`() {
        view.visibility = View.VISIBLE
        assertThat(view.isVisible()).isTrue()

        view.visibility = View.GONE
        assertThat(view.isVisible()).isFalse()

        view.visibility = View.INVISIBLE
        assertThat(view.isVisible()).isFalse()
    }

    @Test
    fun `test gone extension`() {
        view.gone()
        assertThat(view.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `test visible extension`() {
        view.visible()
        assertThat(view.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `test invisible extension`() {
        view.invisible()
        assertThat(view.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    fun `test setVisible extension`() {
        view.setVisible(visible = true)
        assertThat(view.visibility).isEqualTo(View.VISIBLE)

        view.setVisible(visible = false)
        assertThat(view.visibility).isEqualTo(View.GONE)

        view.setVisible(visible = false, invisible = true)
        assertThat(view.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    fun `test location extension`() {
        val container = FrameLayout(activity)
        container.addView(view)
        activity.setContentView(container)
        
        // Force a layout pass
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, container.measuredWidth, container.measuredHeight)

        val location = view.location()
        assertThat(location).isInstanceOf(Point::class.java)
        assertThat(location.x).isEqualTo(0)
        assertThat(location.y).isEqualTo(0)
    }

    @Test
    fun `test rect extension`() {
        val container = FrameLayout(activity)
        val layoutParams = FrameLayout.LayoutParams(100, 200)
        container.addView(view, layoutParams)
        activity.setContentView(container)
        
        // Force a layout pass
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, container.measuredWidth, container.measuredHeight)

        val rect = view.rect()
        assertThat(rect).isInstanceOf(Rect::class.java)
        assertThat(rect.left).isEqualTo(0)
        assertThat(rect.top).isEqualTo(0)
        assertThat(rect.right).isEqualTo(100)
        assertThat(rect.bottom).isEqualTo(200)
    }

    @Test
    fun `test removeFromParent extension`() {
        val parent = FrameLayout(activity)
        parent.addView(view)
        assertThat(parent.childCount).isEqualTo(1)

        view.removeFromParent()
        assertThat(parent.childCount).isEqualTo(0)
    }

    @Test
    fun `test fadeTransition extension`() {
        val viewGroup = FrameLayout(activity)
        viewGroup.fadeTransition(duration = 100)
        // Note: We can only verify that the call doesn't crash since the actual animation
        // effects are difficult to test in a unit test environment
    }
}