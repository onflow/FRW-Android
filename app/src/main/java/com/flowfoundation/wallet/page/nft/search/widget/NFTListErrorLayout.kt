package com.flowfoundation.wallet.page.nft.search.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.databinding.LayoutNftListErrorBinding


class NFTListErrorLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = LayoutNftListErrorBinding.inflate(LayoutInflater.from(context))
    init {
        addView(binding.root)
    }

    fun setOnRefreshClickListener(onRefreshClickListener: () -> Unit) {
        binding.clRefresh.setOnClickListener {
            onRefreshClickListener.invoke()
        }
    }
}