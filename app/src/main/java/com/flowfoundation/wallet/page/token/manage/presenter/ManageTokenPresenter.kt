package com.flowfoundation.wallet.page.token.manage.presenter

import android.graphics.Color
import android.transition.Scene
import android.transition.Slide
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityManageTokenBinding
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.page.token.manage.HideDustTokenTipDialog
import com.flowfoundation.wallet.page.token.manage.ManageTokenActivity
import com.flowfoundation.wallet.page.token.manage.adapter.ManageTokenAdapter
import com.flowfoundation.wallet.page.token.manage.viewmodel.ManageTokenViewModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding


class ManageTokenPresenter(
    private val activity: ManageTokenActivity,
    private val binding: ActivityManageTokenBinding,
): BasePresenter<List<FungibleToken>> {

    private val viewModel by lazy { ViewModelProvider(activity)[ManageTokenViewModel::class.java] }
    private val tokenAdapter by lazy { ManageTokenAdapter() }

    init {
        binding.root.addStatusBarTopPadding()
        setupToolbar()
        setupFilters()
        setupEditText()
        setupRecyclerView()
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = R.string.manage_tokens.res2String()
    }

    private fun setupFilters() {
        with(binding) {
            switchDustToken.isChecked = FungibleTokenListManager.isHideDustTokens()
            switchDustToken.setOnCheckedChangeListener { _, isChecked ->
                FungibleTokenListManager.setHideDustTokens(isChecked)
            }

            switchVerifiedToken.isChecked = FungibleTokenListManager.isOnlyShowVerifiedTokens()
            switchVerifiedToken.setOnCheckedChangeListener { _, isChecked ->
                FungibleTokenListManager.setOnlyShowVerifiedTokens(isChecked)
            }
            ivHideDustTip.setOnClickListener {
                HideDustTokenTipDialog(activity).show()
            }
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

    private fun setupRecyclerView() {
        with(binding.recyclerView) {
            adapter = tokenAdapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(
                ColorDividerItemDecoration(
                    Color.TRANSPARENT,
                    2.dp2px().toInt(),
                    LinearLayout.VERTICAL
                )
            )
        }
    }

    override fun bind(model: List<FungibleToken>) {
        tokenAdapter.setNewDiffData(model)
    }

    fun notifyTokenItemChanged(index: Int) {
        if (index < 0) {
            return
        }
        tokenAdapter.notifyItemChanged(index)
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