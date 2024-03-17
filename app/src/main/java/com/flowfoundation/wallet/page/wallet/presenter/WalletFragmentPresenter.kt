package com.flowfoundation.wallet.page.wallet.presenter

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentWalletBinding
import com.flowfoundation.wallet.firebase.analytics.reportEvent
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.main.presenter.openDrawerLayout
import com.flowfoundation.wallet.page.wallet.WalletFragmentViewModel
import com.flowfoundation.wallet.page.wallet.adapter.WalletFragmentAdapter
import com.flowfoundation.wallet.page.wallet.model.WalletFragmentModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class WalletFragmentPresenter(
    private val fragment: Fragment,
    private val binding: FragmentWalletBinding,
) : BasePresenter<WalletFragmentModel> {

    private val recyclerView = binding.recyclerView
    private val adapter by lazy { WalletFragmentAdapter() }

    private val viewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletFragmentViewModel::class.java] }

    init {
        with(recyclerView) {
            this.adapter = this@WalletFragmentPresenter.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 10.dp2px().toInt(), LinearLayout.VERTICAL))
        }
        with(binding.refreshLayout) {
            setOnRefreshListener { viewModel.load() }
            setColorSchemeColors(R.color.colorSecondary.res2color())
        }

        binding.avatarView.setOnClickListener { openDrawerLayout(fragment.requireContext()) }
        with(binding.networkView) {
            setVisible(!isMainnet())
            if (!isMainnet()) {
                val color = if (isTestnet()) R.color.testnet.res2color() else R.color.sandbox.res2color()
                backgroundTintList =
                    ColorStateList.valueOf(color).withAlpha(16)
                setTextColor(color)
                setText(if (isTestnet()) R.string.testnet else R.string.sandbox)
            }
        }
        bindUserInfo()
    }

    override fun bind(model: WalletFragmentModel) {
        model.data?.let {
            reportEvent("wallet_coin_list_loaded", mapOf("count" to it.size.toString()))
            adapter.setNewDiffData(it)
            binding.refreshLayout.isRefreshing = false
            bindUserInfo()
        }
    }

    private fun bindUserInfo() {
        ioScope {
            val userInfo = AccountManager.userInfo() ?: return@ioScope
            uiScope {
                binding.titleView.text = userInfo.nickname
                binding.avatarView.loadAvatar(userInfo.avatar)
            }
        }
    }

}