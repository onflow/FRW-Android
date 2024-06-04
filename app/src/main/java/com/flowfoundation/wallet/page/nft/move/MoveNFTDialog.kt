package com.flowfoundation.wallet.page.nft.move

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogMoveNftBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.name
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftCache
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class MoveNFTDialog: BottomSheetDialogFragment() {
    private val uniqueId by lazy { arguments?.getString(EXTRA_UNIQUE_ID) ?: ""}
    private val isMoveToEVM by lazy { WalletManager.isEVMAccountSelected().not() }
    private lateinit var binding: DialogMoveNftBinding
    private var nft: Nft? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogMoveNftBinding.inflate(inflater)
        return binding.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.nft = NftCache(WalletManager.selectedWalletAddress()).findNftById(uniqueId)
        with(binding) {
            btnMove.isEnabled = nft != null
            btnMove.setOnClickListener {
                moveNFT()
            }
            ivClose.setOnClickListener {
                dismissAllowingStateLoss()
            }
            val userInfo = AccountManager.userInfo()
            val walletAddress = WalletManager.wallet()?.walletAddress()
            val evmAddress = EVMWalletManager.getEVMAddress()
            val walletEmoji = AccountEmojiManager.getEmojiByAddress(walletAddress)
            val evmEmoji = AccountEmojiManager.getEmojiByAddress(evmAddress)
            if (isMoveToEVM) {
                tvFromAvatar.text = Emoji.getEmojiById(walletEmoji.emojiId)
                tvFromAvatar.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(walletEmoji.emojiId))
                tvFromName.text = walletEmoji.emojiName
                tvFromAddress.text = walletAddress ?: ""
                tvFromEvmLabel.gone()

                tvToAvatar.text = Emoji.getEmojiById(evmEmoji.emojiId)
                tvToAvatar.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(evmEmoji.emojiId))
                tvToName.text = evmEmoji.emojiName
                tvToAddress.text = evmAddress
                tvToEvmLabel.visible()
            } else {
                tvFromAvatar.text = Emoji.getEmojiById(evmEmoji.emojiId)
                tvFromAvatar.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(evmEmoji.emojiId))
                tvFromEvmLabel.visible()
                tvFromName.text = evmEmoji.emojiName
                tvFromAddress.text = evmAddress

                tvToAvatar.text = Emoji.getEmojiById(walletEmoji.emojiId)
                tvToAvatar.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(walletEmoji.emojiId))
                tvToName.text = walletEmoji.emojiName
                tvToAddress.text = walletAddress ?: ""
                tvToEvmLabel.gone()
            }
            Glide.with(ivNftImage).load(nft?.cover()).transform(RoundedCorners(16.dp2px().toInt()))
                .placeholder(R.drawable.ic_placeholder).into(ivNftImage)
            ivCollectionVm.setImageResource(
                if (isMoveToEVM) {
                    R.drawable.ic_switch_vm_cadence
                } else {
                    R.drawable.ic_switch_vm_evm
                }
            )
            tvNftName.text = nft?.name()
            tvCollectionName.text = nft?.collectionName
            Glide.with(ivCollectionLogo).load(nft?.collectionSquareImage).transform(
                CenterCrop(),
                RoundedCorners(8.dp2px().toInt())).into(ivCollectionLogo)
        }
    }

    private fun moveNFT() {
        nft?.let {
            if (binding.btnMove.isProgressVisible()) {
                return
            }
            binding.btnMove.setProgressVisible(true)
            ioScope {
                EVMWalletManager.moveNFT(it, isMoveToEVM) { isSuccess ->
                    uiScope {
                        binding.btnMove.setProgressVisible(false)
                        if (isSuccess) {
                            dismissAllowingStateLoss()
                        } else {
                            toast(R.string.move_nft_to_evm_failed)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_UNIQUE_ID = "extra_unique_id"
        fun show(fragmentManager: FragmentManager, uniqueId: String) {
            MoveNFTDialog().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_UNIQUE_ID, uniqueId)
                }
            }.show(fragmentManager, "")
        }
    }
}
