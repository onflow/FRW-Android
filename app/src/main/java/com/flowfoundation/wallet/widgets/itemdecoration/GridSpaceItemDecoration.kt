package com.flowfoundation.wallet.widgets.itemdecoration

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.annotation.Dimension
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.utils.extensions.dp2px
import java.util.*
import kotlin.math.roundToInt

class GridSpaceItemDecoration(
    @Dimension(unit = Dimension.DP) private val start: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val top: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val end: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val bottom: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val horizontal: Double = 0.0,
    @Dimension(unit = Dimension.DP) private val vertical: Double = 0.0,
) : RecyclerView.ItemDecoration() {

    private var dividerVisibleCheck: DividerVisibleCheck? = null

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)

        val gridLayoutManager = parent.layoutManager as? GridLayoutManager ?: return
        val itemCount = parent.adapter?.itemCount ?: return
        val spanCount = gridLayoutManager.spanCount
        val position = parent.getChildLayoutPosition(view)
        
        if (position < 0) return
        
        val spanSizeLookup = gridLayoutManager.spanSizeLookup

        if (dividerVisibleCheck?.dividerVisible(position) == false) {
            return
        }

        val spanSize = spanSizeLookup.getSpanSize(position)
        val spanIndex = spanSizeLookup.getSpanIndex(position, spanCount)
        val spanGroupIndex = spanSizeLookup.getSpanGroupIndex(position, spanCount)

        val isRtl = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_RTL

        // Simplified spacing calculation to prevent overlapping
        val leftSpacing = if (spanIndex == 0) {
            start.dp2px()
        } else {
            (horizontal / 2).dp2px()
        }
        
        val rightSpacing = if (spanIndex + spanSize == spanCount) {
            end.dp2px()
        } else {
            (horizontal / 2).dp2px()
        }
        
        val topSpacing = if (spanGroupIndex == 0) {
            top.dp2px()
        } else {
            (vertical / 2).dp2px()
        }
        
        val bottomSpacing = if (isLastRow(position, itemCount, spanSizeLookup, spanCount)) {
            bottom.dp2px()
        } else {
            (vertical / 2).dp2px()
        }

        val (left, right) = if (isRtl) {
            rightSpacing to leftSpacing
        } else {
            leftSpacing to rightSpacing
        }

        outRect.set(
            left.toInt(),
            topSpacing.toInt(),
            right.toInt(),
            bottomSpacing.toInt()
        )
    }

    private fun isLastRow(
        position: Int,
        itemCount: Int,
        spanSizeLookup: GridLayoutManager.SpanSizeLookup,
        spanCount: Int
    ): Boolean {
        val currentRow = spanSizeLookup.getSpanGroupIndex(position, spanCount)
        val lastItemRow = spanSizeLookup.getSpanGroupIndex(itemCount - 1, spanCount)
        return currentRow == lastItemRow
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        // Empty implementation - no drawing needed for spacing decoration
    }
}

interface DividerVisibleCheck {
    fun dividerVisible(position: Int): Boolean
}