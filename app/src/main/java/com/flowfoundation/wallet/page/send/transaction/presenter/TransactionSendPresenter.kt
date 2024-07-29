package com.flowfoundation.wallet.page.send.transaction.presenter

import android.net.Uri
import android.transition.Scene
import android.transition.Slide
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.LayoutSendAddressSelectBinding
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.addressVerify
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.address.AddressBookViewModel
import com.flowfoundation.wallet.page.address.isAddressBookAutoSearch
import com.flowfoundation.wallet.page.evm.EnableEVMDialog
import com.flowfoundation.wallet.page.scan.METAMASK_ETH_SCHEME
import com.flowfoundation.wallet.page.send.transaction.SelectSendAddressViewModel
import com.flowfoundation.wallet.page.send.transaction.adapter.TransactionSendPageAdapter
import com.flowfoundation.wallet.page.send.transaction.model.TransactionSendModel
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.SendAmountActivity
import com.flowfoundation.wallet.utils.addressPattern
import com.flowfoundation.wallet.utils.evmAddressPattern
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress

class TransactionSendPresenter(
    private val fragmentManager: FragmentManager,
    private val binding: LayoutSendAddressSelectBinding,
    private val coinSymbol: String? = null,
) : BasePresenter<TransactionSendModel> {
    private val activity by lazy { findActivity(binding.root) as FragmentActivity }

    private val searchViewModel by lazy { ViewModelProvider(activity)[AddressBookViewModel::class.java] }
    private val viewModel by lazy { ViewModelProvider(activity)[SelectSendAddressViewModel::class.java] }
    private val tabTitles by lazy { listOf(R.string.recent, R.string.address_book, R.string.my_accounts) }

    init {
        with(binding) {
            with(binding.viewPager) {
                adapter = TransactionSendPageAdapter(fragmentManager)
                offscreenPageLimit = 3
            }
            tabLayout.setupWithViewPager(viewPager)
            tabTitles.forEachIndexed { index, title ->
                val tab = tabLayout.getTabAt(index) ?: return@forEachIndexed
                tab.setText(title)
                when (title) {
                    R.string.recent -> tab.setIcon(R.drawable.ic_recent)
                    R.string.address_book -> tab.setIcon(R.drawable.ic_address_hashtag)
                    R.string.my_accounts -> tab.setIcon(R.drawable.ic_user)
                }
            }
        }

        setupSearchBox()
    }

    override fun bind(model: TransactionSendModel) {
        model.qrcode?.let {
            if (it.startsWith(METAMASK_ETH_SCHEME)) {
                if (isPreviewnet().not()) {
                    return
                }
                val addressText = Uri.parse(it).schemeSpecificPart
                if (evmAddressPattern.matches(addressText).not()) {
                    return
                }
                if (WalletManager.isChildAccountSelected()) {
                    return
                }
                if (EVMWalletManager.haveEVMAddress()) {
                    binding.editText.setText(addressText.toAddress())
                } else {
                    binding.editText.setText("")
                    EnableEVMDialog.show(fragmentManager)
                }
            } else {
                if (evmAddressPattern.matches(it)) {
                    if (isPreviewnet().not()) {
                        return
                    }
                    if (EVMWalletManager.haveEVMAddress()) {
                        binding.editText.setText(it)
                    } else {
                        binding.editText.setText("")
                        EnableEVMDialog.show(fragmentManager)
                    }
                } else {
                    binding.editText.setText(it)
                }
            }
        }
        model.selectedAddress?.let { onAddressSelected(it) }
        model.isClearInputFocus?.let {
            binding.editText.clearFocus()
            binding.editText.hideKeyboard()
        }
    }

    private fun setupSearchBox() {
        with(binding.editText) {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    searchViewModel.searchRemote(text.toString().trim(), includeLocal = true)
                    clearFocus()
                }
                return@setOnEditorActionListener false
            }
            doOnTextChanged { input, _, _, _ ->
                val text = input.toString().trim()
                if (isAddressBookAutoSearch(text)) {
                    searchViewModel.searchRemote(text.trim(), true, isAutoSearch = true)
                } else {
                    searchViewModel.searchLocal(text.trim())
                }

                binding.searchContainer.setVisible(text.isNotBlank())

                binding.progressBar.setVisible(false)
                binding.searchIconView.setVisible(true)
                checkAddressAutoJump(text)
            }
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> onSearchFocusChange(hasFocus) }
        }
        binding.cancelButton.setOnClickListener {
            onSearchFocusChange(false)
            binding.editText.hideKeyboard()
            binding.editText.setText("")
            binding.editText.clearFocus()
            searchViewModel.clearSearch()
        }
    }

    private fun checkAddressAutoJump(text: String) {
        val isMatched = addressPattern.matches(text)
        val isEVMMatched = evmAddressPattern.matches(text)
        if (isMatched) {
            binding.progressBar.setVisible()
            binding.searchIconView.setVisible(false, invisible = true)
            ioScope {
                val isVerified = addressVerify(text)
                uiScope {
                    if (text == binding.editText.text.toString()) {
                        binding.progressBar.setVisible(false)
                        binding.searchIconView.setVisible()
                        if (isVerified) {
                            viewModel.onAddressSelectedLiveData.postValue(AddressBookContact(address = text))
                        }
                    }
                }
            }
        } else if (isEVMMatched) {
            if (isPreviewnet().not()) {
                return
            }
            if (EVMWalletManager.haveEVMAddress().not()) {
                EnableEVMDialog.show(fragmentManager)
                return
            }
            binding.progressBar.setVisible()
            binding.searchIconView.setVisible(false, invisible = true)
            if (text == binding.editText.text.toString()) {
                binding.progressBar.setVisible(false)
                binding.searchIconView.setVisible()
                viewModel.onAddressSelectedLiveData.postValue(AddressBookContact(address = text))
            }
        }
    }

    private fun onAddressSelected(address: AddressBookContact) {
        if (WalletManager.isChildAccountSelected()) {
            return
        }
        SendAmountActivity.launch(activity, address, coinSymbol)
    }

    private fun onSearchFocusChange(hasFocus: Boolean) {
        val isVisible = hasFocus || !binding.editText.text.isNullOrBlank()
        val isVisibleChange = isVisible != binding.cancelButton.isVisible()

        if (isVisibleChange) {
            TransitionManager.go(Scene(binding.root as ViewGroup), Slide(Gravity.END).apply { duration = 150 })
            binding.cancelButton.setVisible(isVisible)
        }
    }
}