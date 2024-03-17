package com.flowfoundation.wallet.page.inbox.presenter

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityInboxBinding
import com.flowfoundation.wallet.page.inbox.InboxActivity
import com.flowfoundation.wallet.page.inbox.adapter.InboxPageAdapter
import com.flowfoundation.wallet.page.inbox.model.InboxPageModel
import com.flowfoundation.wallet.widgets.ProgressDialog

class InboxPagePresenter(
    private val binding: ActivityInboxBinding,
    private val activity: InboxActivity,
) : BasePresenter<InboxPageModel> {

    private val titles = listOf(R.string.token_with_count, R.string.nft_with_count)

    private var progressDialog: ProgressDialog? = null

    init {
        setupViewPager()
        setupTabLayout()
    }

    override fun bind(model: InboxPageModel) {
        model.tokenList?.let { updateTabTitle(0, it.size) }
        model.nftList?.let { updateTabTitle(1, it.size) }
        model.claimExecuting?.let { if (it) showProgressDialog() else dismissProgressDialog() }
    }

    private fun updateTabTitle(index: Int, size: Int) {
        binding.tabLayout.getTabAt(index)?.text = activity.getString(titles[index], size)
    }

    private fun setupViewPager() {
        with(binding.viewPager) {
            adapter = InboxPageAdapter(activity.supportFragmentManager)
        }
    }

    private fun showProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = ProgressDialog(activity).apply { show() }
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
    }

    private fun setupTabLayout() {
        binding.tabLayout.setupWithViewPager(binding.viewPager)
        for (i in 0 until binding.tabLayout.tabCount) {
            binding.tabLayout.getTabAt(i)?.text = activity.getString(titles[i], 0)
        }
    }
}