package com.flowfoundation.wallet.page.emoji

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.databinding.ViewEmojiAvatarBinding
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.emoji.model.WalletEmojiInfo
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.loadAvatar

class EmojiAvatarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewEmojiAvatarBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
    }

    fun setAvatarInfo(emojiInfo: WalletEmojiInfo? = null, iconUrl: String? = null) {
        with(binding) {
            emojiInfo?.let {
                tvAvatar.text = Emoji.getEmojiById(it.emojiId)
                tvAvatar.backgroundTintList =
                    ColorStateList.valueOf(Emoji.getEmojiColorRes(it.emojiId))
                tvAvatar.visible()
                ivAvatar.gone()
            }
            if (iconUrl.isNullOrEmpty()) {
                return
            }
            ivAvatar.loadAvatar(iconUrl)
            ivAvatar.visible()
            tvAvatar.gone()
        }
    }

    fun setAvatarIcon(iconId: Int) {
        with(binding) {
            Glide.with(ivAvatar).load(iconId).into(ivAvatar)
            ivAvatar.visible()
            tvAvatar.gone()
        }
    }
}