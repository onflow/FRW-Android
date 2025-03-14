package com.flowfoundation.wallet.page.nft.search.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import com.flowfoundation.wallet.databinding.LayoutNftListLoadingBinding
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible


class NFTListLoadingLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = LayoutNftListLoadingBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
    }

    @SuppressLint("SetTextI18n")
    fun setLoadingProgress(progress: Int, total: Int) {
        with(binding) {
            if (total == 0) {
                tvProgress.text = ""
                tvProgress.gone()
                return
            }
            pbLoading.progress = progress
            pbLoading.max = total
            tvProgress.text = "$progress/$total"
            if (tvProgress.isGone) {
                tvProgress.visible()
            }
        }
    }
}