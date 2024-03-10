package com.flowfoundation.wallet.widgets.easyfloat.anim

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.flowfoundation.wallet.utils.extensions.scale
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.widgets.easyfloat.enums.SidePattern
import com.flowfoundation.wallet.widgets.easyfloat.interfaces.OnFloatAnimator
import com.flowfoundation.wallet.widgets.easyfloat.utils.DisplayUtils

/**
 * @author: liuzhenfeng
 * @function: 系统浮窗的默认效果，选择靠近左右侧的一边进行出入
 * @date: 2019-07-22  17:22
 */
open class ScaleAlphaAnimator : OnFloatAnimator {

    override fun enterAnim(
        view: View,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        sidePattern: SidePattern
    ): Animator? = getAnimator(view, false)

    override fun exitAnim(
        view: View,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        sidePattern: SidePattern
    ): Animator? = getAnimator(view, true)

    private fun getAnimator(
        view: View,
        isExit: Boolean
    ): Animator {
        val childView = (view as ViewGroup).getChildAt(0)
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 150
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                try {
                    val value = it.animatedValue as Float
                    val progress = if (isExit) 1 - value else value

                    view.scale(if (progress < 0.9f) 0.9f else progress)

                    val alpha = if (progress + 0.1f > 1) 1f else progress + 0.1f
                    childView.alpha = alpha
                    // 动画执行过程中页面关闭，出现异常
//                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    loge(e)
                }
            }
        }
    }


    /**
     * 单页面浮窗（popupWindow），坐标从顶部计算，需要加上状态栏的高度
     */
    private fun getCompensationHeight(view: View, params: WindowManager.LayoutParams): Int {
        val location = IntArray(2)
        // 获取在整个屏幕内的绝对坐标
        view.getLocationOnScreen(location)
        // 绝对高度和相对高度相等，说明是单页面浮窗（popupWindow），计算底部动画时需要加上状态栏高度
        return if (location[1] == params.y) DisplayUtils.statusBarHeight(view) else 0
    }

}