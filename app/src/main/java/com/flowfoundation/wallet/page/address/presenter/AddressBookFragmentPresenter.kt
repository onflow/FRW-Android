package com.flowfoundation.wallet.page.address.presenter

import android.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentAddressBookBinding
import com.flowfoundation.wallet.page.address.AddressBookFragment
import com.flowfoundation.wallet.page.address.AddressBookViewModel
import com.flowfoundation.wallet.page.address.adapter.AddressBookAdapter
import com.flowfoundation.wallet.page.address.model.AddressBookFragmentModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setSpannableText
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class AddressBookFragmentPresenter(
    private val fragment: AddressBookFragment,
    private val binding: FragmentAddressBookBinding,
) : BasePresenter<AddressBookFragmentModel> {

    private val adapter by lazy { AddressBookAdapter() }

    private val activity = fragment.requireActivity()

    private val viewModel by lazy { ViewModelProvider(fragment.requireActivity())[AddressBookViewModel::class.java] }

    init {
        with(binding.recyclerView) {
            adapter = this@AddressBookFragmentPresenter.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                ColorDividerItemDecoration(Color.TRANSPARENT, 4.dp2px().toInt())
            )
        }

        binding.localEmptyWrapper.setOnClickListener {
            viewModel.searchRemote(viewModel.searchKeyword())
            viewModel.clearInputFocus()
            binding.localEmptyWrapper.setVisible(false)
        }
    }

    override fun bind(model: AddressBookFragmentModel) {
        model.data?.let {
            adapter.setNewDiffData(it)
            updateLocalEmptyState(false)
            updateRemoteEmptyState(false)
        }
        model.isRemoteEmpty?.let { updateRemoteEmptyState(it) }
        model.isLocalEmpty?.let { updateLocalEmptyState(it) }
        model.isSearchStart?.let { binding.progressBar.setVisible(it) }
    }

    private fun updateLocalEmptyState(isEmpty: Boolean) {
        binding.localEmptyWrapper.setVisible(isEmpty)
        if (isEmpty) {
            binding.localEmptyTextView.setSpannableText(
                activity.getString(
                    R.string.search_the_id, viewModel.searchKeyword()
                ), viewModel.searchKeyword(), R.color.colorSecondary.res2color()
            )
        }
        updateRemoteEmptyState(false)
    }

    private fun updateRemoteEmptyState(isEmpty: Boolean) {
        binding.emptyTipWrapper.setVisible(isEmpty)
        binding.progressBar.setVisible(false)
    }
}