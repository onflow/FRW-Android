package com.flowfoundation.wallet.page.nft.move.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.ItemSelectNftAccountInfoBinding
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.shortenEVMString

class AccountInfoItem @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ItemSelectNftAccountInfoBinding.inflate(LayoutInflater.from(context))
    private var backgroundColor = R.color.bg_3

    private var address = ""

    init {
        attrs?.apply {
            initAttrs(this)
        }
        addView(binding.root)
    }

    private fun initAttrs(attributeSet: AttributeSet) {
        context.theme.obtainStyledAttributes(attributeSet, R.styleable.AccountInfoItem,0,0).apply {
            backgroundColor = getResourceId(R.styleable.AccountInfoItem_layout_background_color, backgroundColor)
        }.recycle()
    }

    fun setAccountInfo(address: String) {
        this.address = address
        with(binding) {
            rootView.backgroundTintList = ColorStateList.valueOf(backgroundColor.res2color())
            if (WalletManager.isChildAccount(address)) {
                tvEvmLabel.gone()
                WalletManager.childAccount(address)?.let {
                    viewAccountAvatar.setAvatarInfo(iconUrl = it.icon)
                    tvAccountName.text = it.name
                    tvAccountAddress.text = it.address
                }
            } else {
                val isEVMAccount = EVMWalletManager.isEVMWalletAddress(address)
                tvEvmLabel.setVisible(isEVMAccount)
                val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)
                viewAccountAvatar.setAvatarInfo(emojiInfo = emojiInfo)
                tvAccountName.text = emojiInfo.emojiName
                tvAccountAddress.text = if (isEVMAccount) {
                    shortenEVMString(address)
                } else {
                    address
                }
            }
        }
    }

    fun getAccountAddress(): String {
        return this.address
    }

    fun setSelectMoreAccount(selectMore: Boolean) {
        binding.ivArrowDown.setVisible(selectMore)
    }
}