package com.flowfoundation.wallet.widgets.easyfloat.core

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.WindowManager.LayoutParams.*
import android.widget.EditText
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.widgets.easyfloat.anim.AnimatorManager
import com.flowfoundation.wallet.widgets.easyfloat.data.FloatConfig
import com.flowfoundation.wallet.widgets.easyfloat.enums.ShowPattern
import com.flowfoundation.wallet.widgets.easyfloat.interfaces.OnFloatTouchListener
import com.flowfoundation.wallet.widgets.easyfloat.utils.DisplayUtils
import com.flowfoundation.wallet.widgets.easyfloat.utils.InputMethodUtils
import com.flowfoundation.wallet.widgets.easyfloat.utils.LifecycleUtils
import com.flowfoundation.wallet.widgets.easyfloat.utils.Logger
import com.flowfoundation.wallet.widgets.easyfloat.widget.ParentFrameLayout

/**
 * @author: Liuzhenfeng
 * @date: 12/1/20  23:40
 * @Description:
 */
internal class FloatingWindowHelper(val context: Context, var config: FloatConfig) {

    lateinit var windowManager: WindowManager
    lateinit var params: WindowManager.LayoutParams
    var frameLayout: ParentFrameLayout? = null
    private lateinit var touchUtils: TouchUtils
    private var enterAnimator: Animator? = null

    private var focusableFlag = FLAG_NOT_FOCUSABLE

    fun createWindow() = try {
        touchUtils = TouchUtils(context, config)
        initParams()
        addView()
        config.isShow = true
    } catch (e: Exception) {
        loge(e)
        config.callbacks?.createdResult(false, "$e", null)
        config.floatCallbacks?.builder?.createdResult?.invoke(false, "$e", null)
    }

    private fun initParams() {
        focusableFlag = if (config.hardKeyEventEnable) 0 else FLAG_NOT_FOCUSABLE
        windowManager = context.getSystemService(Service.WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams().apply {
            if (config.showPattern == ShowPattern.CURRENT_ACTIVITY) {
                // 设置窗口类型为应用子窗口，和PopupWindow同类型
                type = TYPE_APPLICATION_PANEL
                // 子窗口必须和创建它的Activity的windowToken绑定
                token = getToken()
            } else {
                // 系统全局窗口，可覆盖在任何应用之上，以及单独显示在桌面上
                // 安卓6.0 以后，全局的Window类别，必须使用TYPE_APPLICATION_OVERLAY
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) TYPE_APPLICATION_OVERLAY
                else TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.START or Gravity.TOP
            // 设置浮窗以外的触摸事件可以传递给后面的窗口、不自动获取焦点
            flags = if (config.immersionStatusBar)
            // 没有边界限制，允许窗口扩展到屏幕外
                FLAG_NOT_TOUCH_MODAL or focusableFlag or FLAG_LAYOUT_NO_LIMITS
            else FLAG_NOT_TOUCH_MODAL or focusableFlag

            if (!config.isTouchEnable) {
                flags = flags or FLAG_NOT_TOUCHABLE
            }

            width = if (config.widthMatch) MATCH_PARENT else WRAP_CONTENT
            height = if (config.windowHeight != 0) {
                config.windowHeight
            } else {
                if (config.heightMatch) MATCH_PARENT else WRAP_CONTENT
            }

            if (config.immersionStatusBar && config.heightMatch) {
                height = DisplayUtils.getScreenSize(context).y
            }

            // 如若设置了固定坐标，直接定位
            if (config.locationPair != Pair(0, 0)) {
                x = config.locationPair.first
                y = config.locationPair.second
            }
        }
    }

    private fun getToken(): IBinder? {
        val activity = if (context is Activity) context else LifecycleUtils.getTopActivity()
        return activity?.window?.decorView?.windowToken
    }

    /**
     * 将自定义的布局，作为xml布局的父布局，添加到windowManager中，
     * 重写自定义布局的touch事件，实现拖拽效果。
     */
    private fun addView() {
        // 创建一个frameLayout作为浮窗布局的父容器
        frameLayout = ParentFrameLayout(context, config)
        frameLayout?.tag = config.floatTag
        // 将浮窗布局文件添加到父容器frameLayout中，并返回该浮窗文件
        val floatingView =
            LayoutInflater.from(context).inflate(config.layoutId!!, frameLayout, true)
        // 为了避免创建的时候闪一下，我们先隐藏视图，不能直接设置GONE，否则定位会出现问题
        floatingView.visibility = View.INVISIBLE
        // 将frameLayout添加到系统windowManager中
        windowManager.addView(frameLayout, params)

        // 通过重写frameLayout的Touch事件，实现拖拽效果
        frameLayout?.touchListener = object : OnFloatTouchListener {
            override fun onTouch(event: MotionEvent) =
                touchUtils.updateFloat(frameLayout!!, event, windowManager, params)
        }

        // 在浮窗绘制完成的时候，设置初始坐标、执行入场动画
        frameLayout?.layoutListener = object : ParentFrameLayout.OnLayoutListener {
            override fun onLayout() {
                setGravity(frameLayout)
                config.apply {
                    // 如果设置了过滤当前页，或者后台显示前台创建、前台显示后台创建，隐藏浮窗，否则执行入场动画
                    if (filterSelf
                        || (showPattern == ShowPattern.BACKGROUND && LifecycleUtils.isForeground())
                        || (showPattern == ShowPattern.FOREGROUND && !LifecycleUtils.isForeground())
                    ) {
                        setVisible(View.GONE)
                        initEditText()
                    } else enterAnim(floatingView)

                    // 设置callbacks
                    layoutView = floatingView
                    invokeView?.invoke(floatingView)
                    callbacks?.createdResult(true, null, floatingView)
                    floatCallbacks?.builder?.createdResult?.invoke(true, null, floatingView)
                }
            }
        }
    }

    private fun initEditText() {
        if (config.hasEditText) frameLayout?.let { traverseViewGroup(it) }
    }

    private fun traverseViewGroup(view: View?) {
        view?.let {
            // 遍历ViewGroup，是子view判断是否是EditText，是ViewGroup递归调用
            if (it is ViewGroup) for (i in 0 until it.childCount) {
                val child = it.getChildAt(i)
                if (child is ViewGroup) traverseViewGroup(child) else checkEditText(child)
            } else checkEditText(it)
        }
    }

    private fun checkEditText(view: View) {
        if (view is EditText) InputMethodUtils.initInputMethod(view, config.floatTag)
    }


    /**
     * 设置浮窗的对齐方式，支持上下左右、居中、上中、下中、左中和右中，默认左上角
     * 支持手动设置的偏移量
     */
    @SuppressLint("RtlHardcoded")
    private fun setGravity(view: View?) {
        if (config.locationPair != Pair(0, 0) || view == null) return
        val parentRect = Rect()
        // 获取浮窗所在的矩形
        windowManager.defaultDisplay.getRectSize(parentRect)
        val location = IntArray(2)
        // 获取在整个屏幕内的绝对坐标
        view.getLocationOnScreen(location)
        // 通过绝对高度和相对高度比较，判断包含顶部状态栏
        val statusBarHeight = if (location[1] > params.y) DisplayUtils.statusBarHeight(view) else 0
        val parentBottom =
            config.displayHeight.getDisplayRealHeight(context) - statusBarHeight
        when (config.gravity) {
            // 右上
            Gravity.END, Gravity.END or Gravity.TOP, Gravity.RIGHT, Gravity.RIGHT or Gravity.TOP ->
                params.x = parentRect.right - view.width
            // 左下
            Gravity.START or Gravity.BOTTOM, Gravity.BOTTOM, Gravity.LEFT or Gravity.BOTTOM ->
                params.y = parentBottom - view.height
            // 右下
            Gravity.END or Gravity.BOTTOM, Gravity.RIGHT or Gravity.BOTTOM -> {
                params.x = parentRect.right - view.width
                params.y = parentBottom - view.height
            }
            // 居中
            Gravity.CENTER -> {
                params.x = (parentRect.right - view.width).shr(1)
                params.y = (parentBottom - view.height).shr(1)
            }
            // 上中
            Gravity.CENTER_HORIZONTAL, Gravity.TOP or Gravity.CENTER_HORIZONTAL ->
                params.x = (parentRect.right - view.width).shr(1)
            // 下中
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL -> {
                params.x = (parentRect.right - view.width).shr(1)
                params.y = parentBottom - view.height
            }
            // 左中
            Gravity.CENTER_VERTICAL, Gravity.START or Gravity.CENTER_VERTICAL, Gravity.LEFT or Gravity.CENTER_VERTICAL ->
                params.y = (parentBottom - view.height).shr(1)
            // 右中
            Gravity.END or Gravity.CENTER_VERTICAL, Gravity.RIGHT or Gravity.CENTER_VERTICAL -> {
                params.x = parentRect.right - view.width
                params.y = (parentBottom - view.height).shr(1)
            }
            // 其他情况，均视为左上
            else -> {
            }
        }

        // 设置偏移量
        params.x += config.offsetPair.first
        params.y += config.offsetPair.second

        if (config.immersionStatusBar) {
            if (config.showPattern != ShowPattern.CURRENT_ACTIVITY) {
                params.y -= statusBarHeight
            }
        } else {
            if (config.showPattern == ShowPattern.CURRENT_ACTIVITY) {
                params.y += statusBarHeight
            }
        }
        // 更新浮窗位置信息
        windowManager.updateViewLayout(view, params)
    }

    /**
     * 设置浮窗的可见性
     */
    fun setVisible(visible: Int, needShow: Boolean = true) {
        if (frameLayout == null || frameLayout!!.childCount < 1) return
        // 如果用户主动隐藏浮窗，则该值为false
        config.needShow = needShow
        frameLayout!!.visibility = visible
        val view = frameLayout!!.getChildAt(0)
        if (visible == View.VISIBLE) {
            config.isShow = true
            config.callbacks?.show(view)
            config.floatCallbacks?.builder?.show?.invoke(view)
        } else {
            config.isShow = false
            config.callbacks?.hide(view)
            config.floatCallbacks?.builder?.hide?.invoke(view)
        }
    }

    /**
     * 入场动画
     */
    private fun enterAnim(floatingView: View) {
        if (frameLayout == null || config.isAnim) return
        enterAnimator = AnimatorManager(frameLayout!!, params, windowManager, config)
            .enterAnim()?.apply {
                // 可以延伸到屏幕外，动画结束按需去除该属性，不然旋转屏幕可能置于屏幕外部
                params.flags =
                    FLAG_NOT_TOUCH_MODAL or focusableFlag or FLAG_LAYOUT_NO_LIMITS

                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        config.isAnim = false
                        if (!config.immersionStatusBar) {
                            // 不需要延伸到屏幕外了，防止屏幕旋转的时候，浮窗处于屏幕外
                            params.flags = FLAG_NOT_TOUCH_MODAL or focusableFlag
                        }
                        initEditText()
                    }

                    override fun onAnimationCancel(animation: Animator) {}

                    override fun onAnimationStart(animation: Animator) {
                        floatingView.visibility = View.VISIBLE
                        config.isAnim = true
                    }
                })
                start()
            }
        if (enterAnimator == null) {
            floatingView.visibility = View.VISIBLE
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    /**
     * 退出动画
     */
    fun exitAnim() {
        if (frameLayout == null || (config.isAnim && enterAnimator == null)) return
        enterAnimator?.cancel()
        val animator: Animator? =
            AnimatorManager(frameLayout!!, params, windowManager, config).exitAnim()
        if (animator == null) remove() else {
            // 二次判断，防止重复调用引发异常
            if (config.isAnim) return
            config.isAnim = true
            params.flags = FLAG_NOT_TOUCH_MODAL or focusableFlag or FLAG_LAYOUT_NO_LIMITS
            animator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    remove()
                }

                override fun onAnimationCancel(animation: Animator) {}

                override fun onAnimationStart(animation: Animator) {}
            })
            animator.start()
        }
    }

    /**
     * 退出动画执行结束/没有退出动画，进行回调、移除等操作
     */
    fun remove(force: Boolean = false) = try {
        config.isAnim = false
        frameLayout?.setVisible(false)
        FloatingWindowManager.remove(config.floatTag)
        // removeView是异步删除，在Activity销毁的时候会导致窗口泄漏，所以使用removeViewImmediate直接删除view
        windowManager.run { if (force) removeViewImmediate(frameLayout) else removeView(frameLayout) }
    } catch (e: Exception) {
        Logger.e("浮窗关闭出现异常：$e")
    }

}