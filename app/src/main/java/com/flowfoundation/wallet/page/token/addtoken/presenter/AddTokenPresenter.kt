package com.flowfoundation.wallet.page.token.addtoken.presenter

import android.graphics.Color
import android.transition.Scene
import android.transition.Slide
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityAddTokenBinding
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.page.token.addtoken.AddTokenActivity
import com.flowfoundation.wallet.page.token.addtoken.AddTokenViewModel
import com.flowfoundation.wallet.page.token.addtoken.adapter.TokenListAdapter
import com.flowfoundation.wallet.page.token.addtoken.model.AddTokenModel
import com.flowfoundation.wallet.utils.extensions.*
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class AddTokenPresenter(
    private val activity: AddTokenActivity,
    private val binding: ActivityAddTokenBinding,
) : BasePresenter<AddTokenModel> {

    private val viewModel by lazy { ViewModelProvider(activity)[AddTokenViewModel::class.java] }
    private val adapter by lazy { TokenListAdapter() }

    init {
        binding.root.addStatusBarTopPadding()
        setupToolbar()
        setupEditText()
        setupFilters()
        setupRecyclerView()
    }

    private fun setupFilters() {
        with(binding) {
            switchVerifiedToken.isChecked = false
            switchVerifiedToken.setOnCheckedChangeListener { _, isChecked ->
                viewModel.switchVerifiedToken(isChecked)
            }
        }
    }

    override fun bind(model: AddTokenModel) {
        model.data?.let { adapter.setNewDiffData(it) }
    }

    private fun setupRecyclerView() {
        with(binding.recyclerView) {
            adapter = this@AddTokenPresenter.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                ColorDividerItemDecoration(Color.TRANSPARENT, 12.dp2px().toInt())
            )
        }
    }

    private fun setupEditText() {
        with(binding.editText) {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    viewModel.search(text.toString().trim())
                    clearFocus()
                }
                return@setOnEditorActionListener false
            }
            doOnTextChanged { text, _, _, _ ->
                viewModel.search(text.toString().trim())
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

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = R.string.add_token.res2String()
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