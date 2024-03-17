package com.flowfoundation.wallet.utils.extensions

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**
 * @author wangkai
 */
/**
 * 强制滚动到指定位置，解决偶尔 scrollToPosition() 无作用问题
 * isCenterPosition: true 表示指定 position 在屏幕中居中，需要配合 CenterLayoutManager 使用
 */
fun RecyclerView.scrollToPositionForce(position: Int, isSmooth: Boolean = false, isCenterPosition: Boolean = false) {
    val layoutManager = this.layoutManager

    var firstItem = 0
    var lastItem = 0
    if (layoutManager is LinearLayoutManager) {
        firstItem = layoutManager.findFirstVisibleItemPosition()
        lastItem = layoutManager.findLastVisibleItemPosition()
    } else if (layoutManager is StaggeredGridLayoutManager) {
        firstItem = layoutManager.findFirstVisibleItemPositions(null).getOrElse(0) { 0 }
        lastItem = layoutManager.findLastVisibleItemPositions(null).getOrElse(0) { 0 }
    }

    when {
        position <= firstItem -> scrollToPosition(position, isSmooth, isCenterPosition)
        position <= lastItem -> {
            if (isCenterPosition) {
                scrollToPosition(position, isSmooth, true)
            } else {
                val top = getChildAt(position - firstItem).top
                if (isSmooth) smoothScrollBy(0, top) else scrollBy(0, top)
            }
        }
        else -> {
            // 当要置顶的项在当前显示的最后一项的后面时
            scrollToPosition(position)
            post {
                (getLayoutManager() as? LinearLayoutManager)?.let { manager ->
                    val offsetPosition = position - manager.findFirstVisibleItemPosition()
                    if (offsetPosition in 0 until childCount) {
                        val top = getChildAt(offsetPosition).top
                        if (isSmooth) smoothScrollBy(0, top) else scrollBy(0, top)
                    }
                }
            }
        }
    }
}


private fun RecyclerView.scrollToPosition(position: Int, isSmooth: Boolean = false, isCenterPosition: Boolean = false) {
    if (isCenterPosition) {
        if (isSmooth) layoutManager?.smoothScrollToPosition(this, RecyclerView.State(), position) else layoutManager?.scrollToPosition(position)
    } else {
        if (isSmooth) smoothScrollToPosition(position) else scrollToPosition(position)
    }
}