package com.flowfoundation.wallet.page.nft.nftlist.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.getNFTCover

class NftCardView : MaterialCardView {

    private val imageView by lazy { findViewById<ImageView>(R.id.image_view) }

    private var nft: Nft? = null

    private var cover: String? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
      : super(context, attrs, defStyleAttr)

    init {
        LayoutInflater.from(context).inflate(R.layout.item_nft_selections, this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return false
    }

    fun bindData(nft: Nft) {
        if (this.nft == nft) {
            return
        }
        this.nft = nft
        cover = nft.cover()
        Glide.with(imageView)
            .load(nft.getNFTCover())
            .placeholder(getSameDrawable())
            .transition(DrawableTransitionOptions.withCrossFade(50))
            .into(imageView)
    }

    private fun getSameDrawable(): Drawable? {
        (parent as ViewGroup).children.forEach {
            if (it is NftCardView && it.id() == id() && it != this) {
                return it.findViewById<ImageView>(R.id.image_view).drawable
            }
        }
        return null
    }

    fun id() = cover
}