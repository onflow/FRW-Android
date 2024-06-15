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
    private var isMoveToEVM = true
    private val viewModel by lazy { ViewModelProvider(this)[SelectNFTViewModel::class.java] }
    private val listAdapter by lazy {
        SelectNFTListAdapter(viewModel)
    }
    private var selectedCollection: CollectionInfo? = null
    private var result: Continuation<Boolean>? = null

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
        isMoveToEVM = WalletManager.isEVMAccountSelected().not()
        with(binding) {
            ivClose.setOnClickListener {
                result?.resume(false)
                dismissAllowingStateLoss()
            }
            layoutFromAccount.setAccountInfo(WalletManager.selectedWalletAddress())
            if (isMoveToEVM) {
                layoutToAccount.setAccountInfo(EVMWalletManager.getEVMAddress() ?: "")
            } else {
                layoutToAccount.setAccountInfo(WalletManager.wallet()?.walletAddress() ?: "")
            }
            btnMove.isEnabled = false
            btnMove.setOnClickListener {
                if (btnMove.isProgressVisible()) {
                    return@setOnClickListener
                }
                btnMove.setProgressVisible(true)
                ioScope {
                    viewModel.moveSelectedNFT(isMoveToEVM) { isSuccess ->
                        uiScope {
                            btnMove.setProgressVisible(false)
                            if (isSuccess) {
                                result?.resume(true)
                                dismissAllowingStateLoss()
                            } else {
                                toast(msg = "move nft failure")
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
            if (WalletManager.isEVMAccountSelected()) {
                loadEVMCollections()
            } else {
                loadCollections()
            }
        }

    }

    @SuppressLint("SetTextI18n")
    private fun showEmptyCollection() {
        with(binding) {
            ivCollectionVm.setImageResource(R.drawable.ic_switch_vm_cadence)
            tvCollectionName.text = "Collection List"
            ivCollectionLogo.setImageResource(R.drawable.bg_empty_placeholder)
            listAdapter.setNewDiffData(emptyList())
            tvEmpty.setVisible()
        }
    }

    private fun setCollectionInfo(collection: CollectionInfo) {
        this.selectedCollection = collection
        with(binding) {
            ivCollectionVm.setImageResource(
                if (isMoveToEVM) {
                    R.drawable.ic_switch_vm_cadence
                } else {
                    R.drawable.ic_switch_vm_evm
                }
            )
            tvCollectionName.text = collection.name.ifEmpty {
                "Collection List"
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