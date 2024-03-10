package com.flowfoundation.wallet.utils

import android.view.View
import android.view.ViewGroup
import com.facebook.shimmer.ShimmerFrameLayout
import com.flowfoundation.wallet.utils.extensions.setVisible


fun startShimmer(root: ViewGroup) {
    if (root.parent == null) {
        return
    }

    root.setVisible(true)

    startShimmerInternal(root)
}

fun stopShimmer(root: ViewGroup) {
    (root.parent as? ViewGroup)?.removeView(root)
}

private fun startShimmerInternal(root: View): ViewGroup? {
    if (root is ShimmerFrameLayout) {
        root.startShimmer()
        return null
    }

    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            startShimmerInternal(child)
        }
    }

    return null
}