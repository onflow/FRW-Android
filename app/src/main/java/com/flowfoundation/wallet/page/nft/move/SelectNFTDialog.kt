package com.flowfoundation.wallet.page.nft.move

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogSelectNftBinding
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.nft.move.model.CollectionInfo
import com.flowfoundation.wallet.page.nft.move.widget.SelectNFTListAdapter
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class SelectNFTDialog: BottomSheetDialogFragment() {
    private lateinit var binding: DialogSelectNftBinding
    private val viewModel by lazy { ViewModelProvider(this)[SelectNFTViewModel::class.java] }
    private val listAdapter by lazy {
        SelectNFTListAdapter(viewModel)
    }
    private var selectedCollection: CollectionInfo? = null
    private var result: Continuation<Boolean>? = null
    private val fromAddress by lazy {
        WalletManager.selectedWalletAddress()
    }
    private var needMoveFee = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSelectNftBinding.inflate(inflater)
        return binding.rootView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface: DialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            setupFullHeight(bottomSheetDialog)
        }
        return dialog
    }

    private fun setupFullHeight(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet =
            bottomSheetDialog.findViewById<ViewGroup>(R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            bottomSheet.requestLayout()
        }
    }


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            ivClose.setOnClickListener {
                result?.resume(false)
                dismissAllowingStateLoss()
            }
            layoutFromAccount.setAccountInfo(WalletManager.selectedWalletAddress())
            configureToAccount()
            storageTip.setInsufficientTip(AccountInfoManager.validateOtherTransaction(true))
            btnMove.isEnabled = false
            btnMove.setOnClickListener {
                if (btnMove.isProgressVisible()) {
                    return@setOnClickListener
                }
                btnMove.setProgressVisible(true)
                ioScope {
                    viewModel.moveSelectedNFT(layoutToAccount.getAccountAddress()) { isSuccess ->
                        uiScope {
                            btnMove.setProgressVisible(false)
                            if (isSuccess) {
                                toast(msgRes = R.string.move_nft_success)
                                result?.resume(true)
                                dismissAllowingStateLoss()
                            } else {
                                toast(msgRes = R.string.move_nft_failed)
                            }
                        }
                    }
                }
            }
            clCollectionLayout.setOnClickListener {
                uiScope {
                    SelectCollectionDialog().show(
                        selectedCollectionId = selectedCollection?.id,
                        childFragmentManager
                    )?.let { info ->
                        viewModel.setCollectionInfo(info)
                    }
                }
            }
        }
        with(binding.rvNftList) {
            adapter = listAdapter
            layoutManager = GridLayoutManager(context, 3, GridLayoutManager.VERTICAL, false)
            addItemDecoration(GridSpaceItemDecoration(vertical = 4.0, horizontal = 4.0))
        }
        with(viewModel) {
            nftListLiveData.observe(this@SelectNFTDialog) { list ->
                listAdapter.setNewDiffData(list)
                binding.tvEmpty.setVisible(list.isEmpty())
            }
            collectionLiveData.observe(this@SelectNFTDialog) { collection ->
                if (collection == null) {
                    showEmptyCollection()
                    return@observe
                }
                setCollectionInfo(collection)
            }
            moveCountLiveData.observe(this@SelectNFTDialog) { count ->
                with(binding.btnMove) {
                    isEnabled = count > 0
                    text = if (count > 0) {
                        R.string.move.res2String() + " $count NFTs"
                    } else {
                        R.string.move.res2String()
                    }
                }
            }
            loadCollections()
        }

    }

    private fun configureToAccount() {
        with(binding) {
            if (WalletManager.isChildAccountSelected()) {
                val parentAddress = WalletManager.wallet()?.walletAddress() ?: return@with
                layoutToAccount.setAccountInfo(parentAddress)
                val addressList = WalletManager.childAccountList(parentAddress)?.get()?.mapNotNull { child ->
                    child.address.takeIf { address -> address != fromAddress }
                }?.toMutableList() ?: mutableListOf()
                addressList.add(0, parentAddress)
                val evmAddress = EVMWalletManager.getEVMAddress().orEmpty()
                if (evmAddress.isNotEmpty()) {
                    addressList.add(evmAddress)
                }
                configureToLayoutAction(addressList)
            } else if (WalletManager.isEVMAccountSelected()) {
                val parentAddress = WalletManager.wallet()?.walletAddress() ?: return@with
                layoutToAccount.setAccountInfo(parentAddress)
                val addressList = WalletManager.childAccountList(parentAddress)?.get()?.map { child ->
                    child.address
                }?.toMutableList() ?: mutableListOf()
                addressList.add(0, parentAddress)
                configureToLayoutAction(addressList)
                needMoveFee = true
            } else {
                val addressList = WalletManager.childAccountList(WalletManager.wallet()?.walletAddress())?.get()?.map { child ->
                    child.address
                }?.toMutableList() ?: mutableListOf()
                val evmAddress = EVMWalletManager.getEVMAddress().orEmpty()
                if (evmAddress.isNotEmpty()) {
                    layoutToAccount.setAccountInfo(evmAddress)
                    addressList.add(evmAddress)
                    needMoveFee = true
                } else {
                    val childAddress = addressList.firstOrNull() ?: return@with
                    val childAccount = WalletManager.childAccount(childAddress) ?: return@with
                    layoutToAccount.setAccountInfo(childAccount.address)
                }
                configureToLayoutAction(addressList)
            }
            configureMoveFeeLayout()
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
                            needMoveFee = EVMWalletManager.isEVMWalletAddress(fromAddress) || EVMWalletManager.isEVMWalletAddress(address)
                            layoutToAccount.setAccountInfo(address)
                            configureMoveFeeLayout()
                        }
                    }
                }
            } else {
                layoutToAccount.setSelectMoreAccount(false)
            }
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
            tvMoveFeeTips.text = (if (needMoveFee) R.string.move_fee_tips else R.string.no_move_fee_tips).res2String()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showEmptyCollection() {
        with(binding) {
            ivCollectionVm.setImageResource(R.drawable.ic_switch_vm_cadence)
            tvCollectionName.text = R.string.collection_list.res2String()
            ivCollectionLogo.setImageResource(R.drawable.bg_empty_placeholder)
            listAdapter.setNewDiffData(emptyList())
            tvEmpty.setVisible()
        }
    }

    private fun setCollectionInfo(collection: CollectionInfo) {
        this.selectedCollection = collection
        with(binding) {
            ivCollectionVm.setImageResource(
                if (WalletManager.isEVMAccountSelected()) {
                    R.drawable.ic_switch_vm_evm
                } else {
                    R.drawable.ic_switch_vm_cadence
                }
            )
            tvCollectionName.text = collection.name.ifEmpty {
                R.string.collection_list.res2String()
            }
            Glide.with(ivCollectionLogo).load(collection.logo).transform(CenterCrop(),
                RoundedCorners(8.dp2px().toInt())).into(ivCollectionLogo)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        result?.resume(false)
    }

    suspend fun show(activity: FragmentActivity) = suspendCoroutine {
        this.result = it
        show(activity.supportFragmentManager, "")
    }

}