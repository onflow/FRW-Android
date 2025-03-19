package com.flowfoundation.wallet.widgets.itemdecoration

import android.graphics.Rect
import android.view.View
import androidx.annotation.Dimension
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.utils.extensions.dp2px

class GridLayoutItemDecoration(
    @Dimension(unit = Dimension.DP) private val start: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val top: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val end: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val bottom: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val horizontal: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val vertical: Double = 0.0,
) : RecyclerView.ItemDecoration() {

    private var dividerVisibleCheck: GridDividerVisibleCheck? = null

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position < 0) return

        if (dividerVisibleCheck?.dividerVisible(position) == false) {
            return
        }

        val layoutManager = parent.layoutManager as? GridLayoutManager ?: return
        val spanCount = layoutManager.spanCount
        val spanSizeLookup = layoutManager.spanSizeLookup
        val spanSize = spanSizeLookup.getSpanSize(position)
        val spanIndex = spanSizeLookup.getSpanIndex(position, spanCount)

        val row = spanSizeLookup.getSpanGroupIndex(position, spanCount)

        val leftSpacing = if (spanIndex == 0) start.dp2px() else (horizontal / 2).dp2px()
        val rightSpacing = if (spanIndex + spanSize == spanCount) end.dp2px() else (horizontal / 2).dp2px()

        val topSpacing = if (row == 0) top.dp2px() else (vertical / 2).dp2px()
        val bottomSpacing = bottom.dp2px()

        outRect.set(leftSpacing, topSpacing, rightSpacing, bottomSpacing)
    }

    fun setDividerVisibleCheck(dividerVisibleCheck: GridDividerVisibleCheck) {
        this.dividerVisibleCheck = dividerVisibleCheck
    }
}

interface GridDividerVisibleCheck {
    fun dividerVisible(position: Int): Boolean
}