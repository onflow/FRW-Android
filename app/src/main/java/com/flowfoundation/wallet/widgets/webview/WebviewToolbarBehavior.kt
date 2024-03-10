package com.flowfoundation.wallet.widgets.webview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.flowfoundation.wallet.utils.logd


class WebviewToolbarBehavior : CoordinatorLayout.Behavior<View> {
    constructor() : super()
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View) = dependency.getWebView() != null

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        val webview = dependency.getWebView() ?: return false
        val scrollY = webview.scrollY
        logd("xxx", "scrollY:$scrollY")
        child.translationY = scrollY.toFloat()
        return true
    }

    private fun View.getWebView(): WebView? {
        return ((this as? ViewGroup)?.getChildAt(0) as? WebView)
    }
}