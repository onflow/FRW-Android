package com.flowfoundation.wallet.page.address.presenter

import android.transition.Scene
import android.transition.Slide
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityAddressBookBinding
import com.flowfoundation.wallet.page.address.AddressBookActivity
import com.flowfoundation.wallet.page.address.AddressBookViewModel
import com.flowfoundation.wallet.page.address.isAddressBookAutoSearch
import com.flowfoundation.wallet.page.address.model.AddressBookActivityModel
import com.flowfoundation.wallet.utils.extensions.*

class AddressBookActivityPresenter(
    private val activity: AddressBookActivity,
    private val binding: ActivityAddressBookBinding,
) : BasePresenter<AddressBookActivityModel> {

    private val viewModel by lazy { ViewModelProvider(activity)[AddressBookViewModel::class.java] }

    init {
        setupToolbar()
        binding.root.addStatusBarTopPadding()
        with(binding.editText) {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    viewModel.searchRemote(text.toString().trim(), includeLocal = true)
                    clearFocus()
                }
                return@setOnEditorActionListener false
            }
            doOnTextChanged { text, _, _, _ ->
                if (isAddressBookAutoSearch(text)) {
                    viewModel.searchRemote(text.toString().trim(), true, isAutoSearch = true)
                } else {
                    viewModel.searchLocal(text.toString().trim())
                }
            }
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> onSearchFocusChange(hasFocus) }
        }
        binding.cancelButton.setOnClickListener {
            onSearchFocusChange(false)
            binding.editText.hideKeyboard()
            binding.editText.setText("")
            binding.editText.clearFocus()
            viewModel.clearSearch()
        }
    }

    private fun onSearchFocusChange(hasFocus: Boolean) {
        val isVisible = hasFocus || !binding.editText.text.isNullOrBlank()
        val isVisibleChange = isVisible != binding.cancelButton.isVisible()

        if (isVisibleChange) {
            TransitionManager.go(Scene(binding.root as ViewGroup), Slide(Gravity.END).apply { duration = 150 })
            binding.cancelButton.setVisible(isVisible)
        }
    }

    override fun bind(model: AddressBookActivityModel) {
        model.isClearInputFocus?.let {
            binding.editText.clearFocus()
            binding.editText.hideKeyboard()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = R.string.address_book.res2String()
    }
}