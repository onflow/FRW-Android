package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.presenter

import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityChildAccountDetailBinding
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.ChildAccountDetailActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.CoinData
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.CollectionData
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.adapter.AccessibleListAdapter
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.dialog.ChildAccountUnlinkDialog
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.model.ChildAccountDetailModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class ChildAccountDetailPresenter(
    private val binding: ActivityChildAccountDetailBinding,
    private val activity: ChildAccountDetailActivity,
) : BasePresenter<ChildAccountDetailModel> {

    private val accessibleAdapter by lazy { AccessibleListAdapter() }
    private val nftCollections = mutableListOf<CollectionData>()
    private val coinList = mutableListOf<CoinData>()
    private var isShowEmptyCollection = false

    init {
        with(binding.accessibleListView) {
            adapter = accessibleAdapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 8.dp2px().toInt()))
        }
        binding.clHideEmpty.setOnClickListener {
            isShowEmptyCollection = isShowEmptyCollection.not()
            binding.switchEmpty.isChecked = isShowEmptyCollection
            with(binding.switchEmpty) {
                isChecked = isShowEmptyCollection
                jumpDrawablesToCurrentState()
            }
            updateNFTCollections()
        }
        binding.tvTabCollection.setOnClickListener { changeTabStatus(collectionSelected = true) }
        binding.tvTabCoin.setOnClickListener { changeTabStatus(collectionSelected = false) }
    }

    private fun updateNFTCollections() {
        accessibleAdapter.setNewDiffData(
            if (isShowEmptyCollection) nftCollections else nftCollections.filter { it.idList.isNotEmpty() }
        )
    }

    override fun bind(model: ChildAccountDetailModel) {
        model.account?.let { updateAccount(it) }
        model.nftCollections?.let { list ->
            nftCollections.clear()
            nftCollections.addAll(list.sortedByDescending { it.idList.size })
            changeTabStatus(collectionSelected = true)
        }
        model.coinList?.let {
            coinList.clear()
            coinList.addAll(it)
        }
    }

    private fun changeTabStatus(collectionSelected: Boolean) {
        binding.tvTabCollection.isSelected = collectionSelected
        binding.tvTabCoin.isSelected = collectionSelected.not()
        if (collectionSelected) {
            updateNFTCollections()
            binding.clHideEmpty.visible()
            binding.accessibleListView.visible()
            binding.tvCoinEmpty.gone()
        } else {
            accessibleAdapter.setNewDiffData(coinList)
            binding.clHideEmpty.gone()
            if (coinList.isEmpty()) {
                binding.tvCoinEmpty.visible()
                binding.accessibleListView.gone()
            } else {
                binding.accessibleListView.visible()
                binding.tvCoinEmpty.gone()
            }
        }
    }

    private fun updateAccount(account: ChildAccount) {
        with(binding) {
            logoView.loadAvatar(account.icon)
            nameView.text = account.name
            addressView.text = account.address

            descriptionView.text = account.description
            descriptionTitleView.setVisible(!account.description.isNullOrEmpty())

            addressCopyButton.setOnClickListener {
                textToClipboard(account.address)
                toast(msgRes = R.string.copy_address_toast)
            }

            unlinkButton.setOnClickListener {
                ChildAccountUnlinkDialog.show(
                    activity.supportFragmentManager,
                    account
                )
            }
        }
    }

}