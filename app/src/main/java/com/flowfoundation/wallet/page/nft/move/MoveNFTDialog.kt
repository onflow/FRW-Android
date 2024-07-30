package com.flowfoundation.wallet.page.nft.move

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
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceMoveNFTFromChildToParent
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTFromChildToFlow
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTFromParentToChild
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.name
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftCache
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nftco.flow.sdk.FlowTransactionStatus


class MoveNFTDialog : BottomSheetDialogFragment() {
    private val uniqueId by lazy { arguments?.getString(EXTRA_UNIQUE_ID) ?: "" }
    private val contractName by lazy { arguments?.getString(EXTRA_COLLECTION_CONTRACT) ?: "" }
    private val fromAddress by lazy { arguments?.getString(EXTRA_FROM_ADDRESS) ?: WalletManager.selectedWalletAddress() }

    private val isEVMAccountSelected by lazy {
        EVMWalletManager.isEVMWalletAddress(fromAddress)
    }
    private val isChildAccountSelected by lazy {
        WalletManager.isChildAccount(fromAddress)
    }
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
        this.nft = NftCache(nftWalletAddress()).findNFTByIdAndContractName(uniqueId, contractName)
        with(binding) {
            btnMove.isEnabled = nft != null
            btnMove.setOnClickListener {
                moveNFT()
            }
            ivClose.setOnClickListener {
                dismissAllowingStateLoss()
            }

            if (isChildAccountSelected) {
                val childAccount = WalletManager.childAccount(fromAddress) ?: return@with
                viewFromAvatar.setAvatarInfo(iconUrl = childAccount.icon)
                tvFromName.text = childAccount.name
                tvFromAddress.text = childAccount.address
                tvFromEvmLabel.gone()

                val parentAddress = WalletManager.wallet()?.walletAddress() ?: return@with
                val parentWalletEmoji = AccountEmojiManager.getEmojiByAddress(parentAddress)
                viewToAvatar.setAvatarInfo(emojiInfo = parentWalletEmoji)
                tvToName.text = parentWalletEmoji.emojiName
                tvToAddress.text = parentAddress
                tvToEvmLabel.gone()

                nft?.let {
                    val addressList =
                        WalletManager.childAccountList(parentAddress)?.get()?.mapNotNull { child ->
                            child.address.takeIf { address -> address != fromAddress }
                        }?.toMutableList() ?: mutableListOf()
                    addressList.add(parentAddress)
                    if (it.canBridgeToEVM()) {
                        EVMWalletManager.getEVMAddress()?.let { evmAddress ->
                            addressList.add(evmAddress)
                        }
                    }
                    configureToLayoutAction(addressList)
                }
            } else if (isEVMAccountSelected) {
                val evmAddress = fromAddress
                val evmEmoji = AccountEmojiManager.getEmojiByAddress(evmAddress)
                viewFromAvatar.setAvatarInfo(emojiInfo = evmEmoji)
                tvFromName.text = evmEmoji.emojiName
                tvFromAddress.text = evmAddress
                tvFromEvmLabel.visible()

                val walletAddress = WalletManager.wallet()?.walletAddress()
                val walletEmoji = AccountEmojiManager.getEmojiByAddress(walletAddress)
                viewToAvatar.setAvatarInfo(emojiInfo = walletEmoji)
                tvToName.text = walletEmoji.emojiName
                tvToAddress.text = walletAddress ?: ""
                tvToEvmLabel.gone()
                configureToLayoutAction(emptyList())
            } else {
                val walletAddress = WalletManager.wallet()?.walletAddress()
                val walletEmoji = AccountEmojiManager.getEmojiByAddress(walletAddress)
                viewFromAvatar.setAvatarInfo(emojiInfo = walletEmoji)
                tvFromName.text = walletEmoji.emojiName
                tvFromAddress.text = walletAddress ?: ""
                tvFromEvmLabel.gone()
                nft?.let {
                    val addressList = WalletManager.childAccountList(walletAddress)?.get()?.map { child ->
                        child.address
                    }?.toMutableList() ?: mutableListOf()

                    if (it.canBridgeToEVM()) {
                        val evmAddress = EVMWalletManager.getEVMAddress()
                        val evmEmoji = AccountEmojiManager.getEmojiByAddress(evmAddress)
                        viewToAvatar.setAvatarInfo(emojiInfo = evmEmoji)
                        tvToName.text = evmEmoji.emojiName
                        tvToAddress.text = evmAddress
                        tvToEvmLabel.visible()
                        addressList.add(0, evmAddress ?: "")
                    } else {
                        val childAccount = WalletManager.childAccount(addressList[0]) ?: return@with
                        viewToAvatar.setAvatarInfo(iconUrl = childAccount.icon)
                        tvToName.text = childAccount.name
                        tvToAddress.text = childAccount.address
                        tvToEvmLabel.gone()
                    }
                    configureToLayoutAction(addressList)
                }
            }
            Glide.with(ivNftImage).load(nft?.cover()).transform(RoundedCorners(16.dp2px().toInt()))
                .placeholder(R.drawable.ic_placeholder).into(ivNftImage)
            ivCollectionVm.setImageResource(
                if (isEVMAccountSelected.not()) {
                    R.drawable.ic_switch_vm_cadence
                } else {
                    R.drawable.ic_switch_vm_evm
                }
            )
            tvNftName.text = nft?.name()
            tvCollectionName.text = nft?.collectionName
            Glide.with(ivCollectionLogo).load(nft?.collectionSquareImage).transform(
                CenterCrop(),
                RoundedCorners(8.dp2px().toInt())
            ).into(ivCollectionLogo)
        }
    }

    private fun configureToLayoutAction(addressList: List<String>) {
        with(binding) {
            if (addressList.size > 1) {
                ivArrowDown.visible()
                clToLayout.setOnClickListener {
                    uiScope {
                        SelectAccountDialog().show(
                            tvToAddress.text.toString(),
                            addressList,
                            childFragmentManager
                        )?.let { address ->
                            configureToLayout(address)
                        }
                    }
                }
            } else {
                ivArrowDown.gone()
            }
        }
    }

    private fun configureToLayout(address: String) {
        with(binding) {
            tvToAddress.text = address

            if (WalletManager.childAccount(address) != null) {
                val childAccount = WalletManager.childAccount(address)!!
                viewToAvatar.setAvatarInfo(iconUrl = childAccount.icon)
                tvToName.text = childAccount.name
                tvToEvmLabel.gone()
            } else {
                val emoji = AccountEmojiManager.getEmojiByAddress(address)
                viewToAvatar.setAvatarInfo(emojiInfo = emoji)
                tvToName.text = emoji.emojiName
                tvToEvmLabel.setVisible(EVMWalletManager.isEVMWalletAddress(address))
            }
        }
    }

    private fun moveNFT() {
        nft?.let {
            if (binding.btnMove.isProgressVisible()) {
                return
            }
            binding.btnMove.setProgressVisible(true)
            ioScope {
                val toAddress = binding.tvToAddress.text.toString()
                if (isChildAccountSelected) {
                    if (toAddress == WalletManager.wallet()?.walletAddress()) {
                        moveNFTFromChildToParent(fromAddress, it) { isSuccess ->
                            uiScope {
                                binding.btnMove.setProgressVisible(false)
                                if (isSuccess) {
                                    dismissAllowingStateLoss()
                                } else {
                                    toast(R.string.move_nft_failed)
                                }
                            }
                        }
                    } else if (it.canBridgeToEVM() && EVMWalletManager.isEVMWalletAddress(toAddress)) {
                        EVMWalletManager.moveNFT(it, true) { isSuccess ->
                            uiScope {
                                binding.btnMove.setProgressVisible(false)
                                if (isSuccess) {
                                    dismissAllowingStateLoss()
                                } else {
                                    toast(R.string.move_nft_to_evm_failed)
                                }
                            }
                        }
                    } else {
                        sendNFTFromChildToFlow(fromAddress, toAddress, it) { isSuccess ->
                            uiScope {
                                binding.btnMove.setProgressVisible(false)
                                if (isSuccess) {
                                    dismissAllowingStateLoss()
                                } else {
                                    toast(R.string.move_nft_failed)
                                }
                            }
                        }
                    }
                } else if (isEVMAccountSelected) {
                    EVMWalletManager.moveNFT(it, false) { isSuccess ->
                        uiScope {
                            binding.btnMove.setProgressVisible(false)
                            if (isSuccess) {
                                dismissAllowingStateLoss()
                            } else {
                                toast(R.string.move_nft_to_evm_failed)
                            }
                        }
                    }
                } else {
                    if (it.canBridgeToEVM() && EVMWalletManager.isEVMWalletAddress(toAddress)) {
                        EVMWalletManager.moveNFT(it, true) { isSuccess ->
                            uiScope {
                                binding.btnMove.setProgressVisible(false)
                                if (isSuccess) {
                                    dismissAllowingStateLoss()
                                } else {
                                    toast(R.string.move_nft_to_evm_failed)
                                }
                            }
                        }
                    } else {
                        sendNFTFromParentToChild(toAddress, it) { isSuccess ->
                            uiScope {
                                binding.btnMove.setProgressVisible(false)
                                if (isSuccess) {
                                    dismissAllowingStateLoss()
                                } else {
                                    toast(R.string.move_nft_failed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun sendNFTFromChildToFlow(childAddress: String, toAddress: String, nft: Nft, callback: (isSuccess: Boolean) -> Unit) {
        try {
            val collection = NftCollectionConfig.get(nft.collectionAddress, nft.collectionContractName)
            val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
            val txId = cadenceSendNFTFromChildToFlow(
                childAddress,
                toAddress,
                identifier,
                nft
            )
            postTransaction(nft, txId, callback)
        } catch (e: Exception) {
            callback.invoke(false)
            e.printStackTrace()
        }
    }

    private suspend fun moveNFTFromChildToParent(childAddress: String, nft: Nft, callback: (isSuccess: Boolean) -> Unit) {
        try {
            val collection = NftCollectionConfig.get(nft.collectionAddress, nft.collectionContractName)
            val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
            val txId = cadenceMoveNFTFromChildToParent(
                childAddress,
                identifier,
                nft
            )
            postTransaction(nft, txId, callback)
        } catch (e: Exception) {
            callback.invoke(false)
            e.printStackTrace()
        }
    }

    private suspend fun sendNFTFromParentToChild(
        toAddress: String,
        nft: Nft,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val collection = NftCollectionConfig.get(nft.collectionAddress, nft.collectionContractName)
            val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
            val txId = cadenceSendNFTFromParentToChild(
                toAddress,
                identifier,
                nft
            )
            postTransaction(nft, txId, callback)
        } catch (e: Exception) {
            callback.invoke(false)
            e.printStackTrace()
        }
    }

    private fun postTransaction(nft: Nft, txId: String?, callback: (isSuccess: Boolean) -> Unit) {
        callback.invoke(txId != null)
        if (txId.isNullOrBlank()) {
            return
        }
        val transactionState = TransactionState(
            transactionId = txId,
            time = System.currentTimeMillis(),
            state = FlowTransactionStatus.PENDING.num,
            type = TransactionState.TYPE_MOVE_NFT,
            data = nft.uniqueId(),
        )
        TransactionStateManager.newTransaction(transactionState)
        pushBubbleStack(transactionState)
    }

    companion object {
        private const val EXTRA_UNIQUE_ID = "extra_unique_id"
        private const val EXTRA_FROM_ADDRESS = "extra_from_address"
        private const val EXTRA_COLLECTION_CONTRACT = "extra_collection_contract"
        fun show(fragmentManager: FragmentManager, uniqueId: String, contractName: String,
                 fromAddress: String) {
            MoveNFTDialog().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_UNIQUE_ID, uniqueId)
                    putString(EXTRA_FROM_ADDRESS, fromAddress)
                    putString(EXTRA_COLLECTION_CONTRACT, contractName)
                }
            }.show(fragmentManager, "")
        }
    }
}
