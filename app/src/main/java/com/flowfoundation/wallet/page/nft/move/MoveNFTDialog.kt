package com.flowfoundation.wallet.page.nft.move

import android.annotation.SuppressLint
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
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceMoveNFTFromChildToParent
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTFromChildToChild
import com.flowfoundation.wallet.manager.flowjvm.cadenceSendNFTFromParentToChild
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.TransferAccountType
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftlist.getNFTCover
import com.flowfoundation.wallet.page.nft.nftlist.name
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftCache
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nftco.flow.sdk.FlowTransactionStatus


class MoveNFTDialog : BottomSheetDialogFragment() {
    private val uniqueId by lazy { arguments?.getString(EXTRA_UNIQUE_ID) ?: "" }
    private val contractName by lazy { arguments?.getString(EXTRA_COLLECTION_CONTRACT) ?: "" }
    private val fromAddress by lazy {
        arguments?.getString(EXTRA_FROM_ADDRESS) ?: WalletManager.selectedWalletAddress()
    }

    private val isEVMAccountSelected by lazy {
        EVMWalletManager.isEVMWalletAddress(fromAddress)
    }
    private val isChildAccountSelected by lazy {
        WalletManager.isChildAccount(fromAddress)
    }
    private var needMoveFee = false
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

            layoutFromAccount.setAccountInfo(fromAddress)
            if (isChildAccountSelected) {
                val parentAddress = WalletManager.wallet()?.walletAddress() ?: return@with
                layoutToAccount.setAccountInfo(parentAddress)

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
                needMoveFee = false
            } else if (isEVMAccountSelected) {
                val walletAddress = WalletManager.wallet()?.walletAddress().orEmpty()
                layoutToAccount.setAccountInfo(walletAddress)
                val addressList =
                    WalletManager.childAccountList(walletAddress)?.get()?.map { child ->
                        child.address
                    }?.toMutableList() ?: mutableListOf()
                addressList.add(0, walletAddress)
                configureToLayoutAction(addressList)
                needMoveFee = true
            } else {
                val walletAddress = WalletManager.wallet()?.walletAddress()
                nft?.let {
                    val addressList =
                        WalletManager.childAccountList(walletAddress)?.get()?.map { child ->
                            child.address
                        }?.toMutableList() ?: mutableListOf()

                    if (it.canBridgeToEVM()) {
                        val evmAddress = EVMWalletManager.getEVMAddress().orEmpty()
                        addressList.add(0, evmAddress)
                        needMoveFee = true
                        layoutToAccount.setAccountInfo(evmAddress)

                    } else {
                        val childAccount = WalletManager.childAccount(addressList[0]) ?: return@with
                        needMoveFee = false
                        layoutToAccount.setAccountInfo(childAccount.address)
                    }
                    configureToLayoutAction(addressList)
                }
            }
            configureMoveFeeLayout()
            Glide.with(ivNftImage).load(nft?.getNFTCover())
                .transform(RoundedCorners(16.dp2px().toInt()))
                .placeholder(R.drawable.ic_placeholder).into(ivNftImage)
            ivCollectionVm.setImageResource(
                if (isEVMAccountSelected.not()) {
                    R.drawable.ic_switch_vm_cadence
                } else {
                    R.drawable.ic_switch_vm_evm
                }
            )
            tvNftName.text = nft?.name()
            tvCollectionName.text = nft?.collectionName.orEmpty()
            Glide.with(ivCollectionLogo).load(nft?.collectionSquareImage).transform(
                CenterCrop(),
                RoundedCorners(8.dp2px().toInt())
            ).into(ivCollectionLogo)
        }
    }

    private fun configureToLayoutAction(addressList: List<String>) {
        with(binding) {
            if (addressList.size > 1) {
                layoutToAccount.setSelectMoreAccount(true)
                layoutToAccount.setOnClickListener {
                    uiScope {
                        SelectAccountDialog().show(
                            layoutToAccount.getAccountAddress(),
                            addressList,
                            childFragmentManager
                        )?.let { address ->
                            configureToLayout(address)
                        }
                    }
                }
            } else {
                layoutToAccount.setSelectMoreAccount(false)
            }
        }
    }

    private fun configureToLayout(address: String) {
        with(binding) {
            needMoveFee =
                EVMWalletManager.isEVMWalletAddress(fromAddress) || EVMWalletManager.isEVMWalletAddress(
                    address
                )
            layoutToAccount.setAccountInfo(address)
            configureMoveFeeLayout()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun configureMoveFeeLayout() {
        with(binding) {
            tvMoveFee.text = if (needMoveFee) {
                "0.001"
            } else {
                "0.00"
            } + "FLOW"
            tvMoveFeeTips.text =
                (if (needMoveFee) R.string.move_fee_tips else R.string.no_move_fee_tips).res2String()
        }
    }

    private fun moveNFT() {
        nft?.let {
            if (binding.btnMove.isProgressVisible()) {
                return
            }
            binding.btnMove.setProgressVisible(true)
            ioScope {
                val toAddress = binding.layoutToAccount.getAccountAddress()
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
                        EVMWalletManager.moveChildNFT(it, fromAddress, true) { isSuccess ->
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
                        sendNFTFromChildToChild(fromAddress, toAddress, it) { isSuccess ->
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
                    if (toAddress == WalletManager.wallet()?.walletAddress()) {
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
                        EVMWalletManager.moveChildNFT(it, toAddress, false) { isSuccess ->
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

    private fun trackMoveNFT(
        fromAddress: String, toAddress: String, nftIdentifier: String, txId: String,
        fromType: TransferAccountType, toType: TransferAccountType
    ) {
        MixpanelManager.transferNFT(
            fromAddress, toAddress, nftIdentifier, txId, fromType, toType,
            true
        )
    }

    private suspend fun sendNFTFromChildToChild(
        childAddress: String,
        toAddress: String,
        nft: Nft,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val collection = NftCollectionConfig.get(nft.collectionAddress, nft.contractName())
            val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
            val txId = cadenceSendNFTFromChildToChild(
                childAddress,
                toAddress,
                identifier,
                nft
            )
            trackMoveNFT(childAddress, toAddress, nft.getNFTIdentifier(), txId.orEmpty(), TransferAccountType.CHILD, TransferAccountType.CHILD)
            postTransaction(nft, txId, callback)
        } catch (e: Exception) {
            callback.invoke(false)
            e.printStackTrace()
        }
    }

    private suspend fun moveNFTFromChildToParent(
        childAddress: String,
        nft: Nft,
        callback: (isSuccess: Boolean) -> Unit
    ) {
        try {
            val collection = NftCollectionConfig.get(nft.collectionAddress, nft.contractName())
            val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
            val txId = cadenceMoveNFTFromChildToParent(
                childAddress,
                identifier,
                nft
            )
            trackMoveNFT(childAddress, WalletManager.wallet()?.walletAddress().orEmpty(), nft.getNFTIdentifier(), txId.orEmpty(), TransferAccountType.CHILD, TransferAccountType.FLOW)
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
            val collection = NftCollectionConfig.get(nft.collectionAddress, nft.contractName())
            val identifier = collection?.path?.privatePath?.removePrefix("/private/") ?: ""
            val txId = cadenceSendNFTFromParentToChild(
                toAddress,
                identifier,
                nft
            )
            trackMoveNFT(WalletManager.wallet()?.walletAddress().orEmpty(), toAddress, nft
                .getNFTIdentifier(), txId.orEmpty(), TransferAccountType.FLOW, TransferAccountType.CHILD)
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
        fun show(
            fragmentManager: FragmentManager, uniqueId: String, contractName: String,
            fromAddress: String
        ) {
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
