package com.flowfoundation.wallet.page.nft.move.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.databinding.ItemSelectNftAccountInfoBinding
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.shortenEVMString


class AccountInfoItem @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ItemSelectNftAccountInfoBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
    }

    fun setAccountInfo(address: String) {
        with(binding) {
            val isEVMAccount = EVMWalletManager.isEVMWalletAddress(address)
            tvEvmLabel.setVisible(isEVMAccount)
            val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)
            tvAccountIcon.text = Emoji.getEmojiById(emojiInfo.emojiId)
            tvAccountIcon.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(emojiInfo.emojiId))
            tvAccountName.text = emojiInfo.emojiName
            tvAccountAddress.text = if (isEVMAccount) {
                shortenEVMString(address)
            } else {
                address
            }
        }
    }
}