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
import com.flowfoundation.wallet.page.nft.search.viewmodel.NFTInfoListViewModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.visible
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

enum class CollectionEmptyState {
    LOADING,
    NO_COLLECTIONS,        // There are no collections at all.
    COLLECTION_EMPTY       // A collection exists but has no items.
}

class SelectNFTDialog: BottomSheetDialogFragment() {
    private lateinit var binding: DialogSelectNftBinding
    private val viewModel by lazy { ViewModelProvider(this)[SelectNFTViewModel::class.java] }
    private val loadNFTViewModel by lazy { ViewModelProvider(this)[NFTInfoListViewModel::class.java] }
    private val listAdapter by lazy {
        SelectNFTListAdapter(viewModel)
    }
    private var selectedCollection: CollectionInfo? = null
    private var result: Continuation<Boolean>? = null
    private val fromAddress by lazy {
        WalletManager.selectedWalletAddress()
    }
    private var needMoveFee = false
    private var moveFromAddress: String = WalletManager.selectedWalletAddress()

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
            configureFromAccount()
            configureToAccount()
            binding.ivArrow.setOnClickListener {
                swapAddresses()
            }
            uiScope {
                storageTip.setInsufficientTip(AccountInfoManager.validateOtherTransaction(true))
            }
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
                        fromAddress = moveFromAddress, // pass the updated from address here
                        fragmentManager = childFragmentManager
                    )?.let { info ->
                        viewModel.setCollectionInfo(moveFromAddress, info)
                    }
                }
            }
        }
        with(binding.rvNftList) {
            adapter = listAdapter
            layoutManager = GridLayoutManager(context, 3, GridLayoutManager.VERTICAL, false)
            addItemDecoration(GridSpaceItemDecoration(vertical = 4.0, horizontal = 4.0))
        }
        binding.searchLayout.setOnSearchListener {
            loadNFTViewModel.searchNFT(it)
        }
        with(loadNFTViewModel) {
            nftListLiveData.observe(this@SelectNFTDialog) { list ->
                if (list == null) {
                    binding.errorLayout.visible()
                    binding.errorLayout.setOnRefreshClickListener {
                        retry()
                    }
                    binding.loadingLayout.gone()
                    binding.searchLayout.gone()
                    listAdapter.setNewDiffData(emptyList())
                    return@observe
                }
                listAdapter.setNewDiffData(list)
                if (list.isEmpty()) {
                    // Check whether a collection is defined.
                    binding.tvEmpty.text = if (viewModel.collectionLiveData.value == null) {
                        getString(R.string.no_collections_available)
                    } else {
                        getString(R.string.select_nft_empty)
                    }
                    binding.tvEmpty.visible()
                    binding.loadingLayout.gone()
                    binding.searchLayout.gone()
                } else {
                    binding.tvEmpty.gone()
                    binding.loadingLayout.gone()
                    binding.searchLayout.visible()
                }
            }
            isLoadingLiveData.observe(this@SelectNFTDialog) { isLoading ->
                configureLoadingState(isLoading)
            }
            loadingProgressLiveData.observe(this@SelectNFTDialog) { (current, total) ->
                binding.loadingLayout.setLoadingProgress(current, total)
            }
        }
        with(viewModel) {
            collectionLiveData.observe(this@SelectNFTDialog) { collection ->
                if (collection == null) {
                    showEmptyCollection(CollectionEmptyState.NO_COLLECTIONS)
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
            loadCollections(moveFromAddress)
        }

    }

    private fun configureLoadingState(loading: Boolean) {
        with(binding) {
            if (loading) {
                errorLayout.gone()
                tvEmpty.gone()
                listAdapter.setNewDiffData(emptyList())
                loadingLayout.visible()
                searchLayout.gone()
                ivArrow.isEnabled = false
                clCollectionLayout.isEnabled = false
                layoutFromAccount.isEnabled = false
                layoutToAccount.isEnabled = false
            } else {
                loadingLayout.gone()
                ivArrow.isEnabled = true
                clCollectionLayout.isEnabled = true
                layoutFromAccount.isEnabled = true
                layoutToAccount.isEnabled = true
            }

        }
    }

    private fun swapAddresses() {
        val currentFrom = binding.layoutFromAccount.getAccountAddress()
        val currentTo = binding.layoutToAccount.getAccountAddress()

        // Swap the addresses in the UI.
        binding.layoutFromAccount.setAccountInfo(currentTo)
        binding.layoutToAccount.setAccountInfo(currentFrom)

        moveFromAddress = currentTo
        moveToAddress = currentFrom

        // Rebuild the eligible lists based on the new state.
        configureFromAccount()
        configureToAccount()

        needMoveFee = EVMWalletManager.isEVMWalletAddress(currentTo) xor EVMWalletManager.isEVMWalletAddress(currentFrom)
        configureMoveFeeLayout()

        selectedCollection = null
        showEmptyCollection(CollectionEmptyState.LOADING)

        viewModel.loadCollections(moveFromAddress)
    }



    private val initialFromAddress = WalletManager.selectedWalletAddress()
    private var moveToAddress: String = WalletManager.wallet()?.walletAddress().orEmpty()


    private fun configureFromAccount() {
        with(binding) {
            val walletAddress = WalletManager.wallet()?.walletAddress() ?: return@with
            val allAccounts = mutableSetOf<String>().apply {
                add(walletAddress)
                WalletManager.childAccountList(walletAddress)
                    ?.get()
                    ?.forEach { add(it.address) }
                add(initialFromAddress)
                // Include the EVM address if available.
                val evmAddress = EVMWalletManager.getEVMAddress().orEmpty()
                if (evmAddress.isNotEmpty()) {
                    add(evmAddress)
                }
            }

            // Remove the current To account from the eligible From accounts.
            allAccounts.remove(moveToAddress)
            val addressList = allAccounts.toMutableList()

            // Ensure currently selected From account is at the top.
            if (moveFromAddress !in addressList) {
                addressList.add(0, moveFromAddress)
            }

            layoutFromAccount.setAccountInfo(moveFromAddress)

            if (addressList.isNotEmpty()) {
                layoutFromAccount.setSelectMoreAccount(true)
                layoutFromAccount.setOnClickListener {
                    uiScope {
                        SelectAccountDialog().show(
                            layoutFromAccount.getAccountAddress(),
                            addressList,
                            childFragmentManager
                        )?.let { newAddress ->
                            moveFromAddress = newAddress
                            layoutFromAccount.setAccountInfo(newAddress)
                            // After changing the From account, update the To account selection options.
                            configureToAccount()
                            viewModel.loadCollections(moveFromAddress)
                        }
                    }
                }
            } else {
                layoutFromAccount.setSelectMoreAccount(false)
            }
        }
    }


    private fun configureToAccount() {
        with(binding) {
            // Build eligible list of To accounts based on current wallet state.
            val eligibleList = mutableListOf<String>()
            val walletAddress = WalletManager.wallet()?.walletAddress() ?: return@with

            // Only add the parent's address if it's not the current From.
            if (walletAddress != moveFromAddress) {
                eligibleList.add(walletAddress)
            }

            // Add child accounts, excluding the current From.
            WalletManager.childAccountList(walletAddress)?.get()?.forEach { child ->
                if (child.address != moveFromAddress) {
                    eligibleList.add(child.address)
                }
            }

            // Add the EVM address if present and not the current From.
            val evmAddress = EVMWalletManager.getEVMAddress().orEmpty()
            if (evmAddress.isNotEmpty() && evmAddress != moveFromAddress) {
                eligibleList.add(evmAddress)
            }

            // Add the initial From address if it’s not the current From.
            if (initialFromAddress != moveFromAddress && !eligibleList.contains(initialFromAddress)) {
                eligibleList.add(initialFromAddress)
            }

            // Preserve the current To if it's still eligible; otherwise, choose a default candidate.
            val currentTo = layoutToAccount.getAccountAddress()
            val newTo = if (eligibleList.contains(currentTo)) {
                currentTo
            } else {
                eligibleList.firstOrNull() ?: ""
            }
            layoutToAccount.setAccountInfo(newTo)
            moveToAddress = newTo

            configureToLayoutAction(eligibleList)
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
                            needMoveFee = EVMWalletManager.isEVMWalletAddress(fromAddress) xor EVMWalletManager.isEVMWalletAddress(address)
                            layoutToAccount.setAccountInfo(address)
                            // Update moveToAddress so the new To account is stored
                            moveToAddress = address
                            configureMoveFeeLayout()
                            // Trigger reload of the From account list
                            configureFromAccount()
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
            } + " FLOW"
            tvMoveFeeTips.text = (if (needMoveFee) R.string.move_fee_tips else R.string.no_move_fee_tips).res2String()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showEmptyCollection(state: CollectionEmptyState) {
        with(binding) {
            ivCollectionVm.setImageResource(R.drawable.ic_switch_vm_cadence)
            tvCollectionName.text = getString(R.string.collection_list)
            ivCollectionLogo.setImageResource(R.drawable.bg_empty_placeholder)
            listAdapter.setNewDiffData(emptyList())

            when(state) {
                CollectionEmptyState.LOADING -> {
                    tvEmpty.text = ""
                    clCollectionLayout.visibility = View.VISIBLE
                }
                CollectionEmptyState.NO_COLLECTIONS -> {
                    tvEmpty.text = getString(R.string.no_collections_available)
                    clCollectionLayout.visibility = View.GONE
                    searchLayout.gone()
                }
                CollectionEmptyState.COLLECTION_EMPTY -> {
                    tvEmpty.text = getString(R.string.select_nft_empty)
                    clCollectionLayout.visibility = View.VISIBLE
                    searchLayout.gone()
                }
            }
            tvEmpty.visibility = View.VISIBLE
        }
    }

    private fun setCollectionInfo(collection: CollectionInfo) {
        this.selectedCollection = collection
        with(binding) {
            clCollectionLayout.visibility = View.VISIBLE
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
            Glide.with(ivCollectionLogo)
                .load(collection.logo)
                .transform(CenterCrop(), RoundedCorners(8.dp2px().toInt()))
                .into(ivCollectionLogo)
        }
        loadNFTViewModel.loadAllNFTs(moveFromAddress, collection.id)
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