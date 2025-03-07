package com.flowfoundation.wallet.page.explore.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemExploreDappBinding
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.explore.ExploreViewModel
import com.flowfoundation.wallet.page.explore.model.DAppModel
import com.flowfoundation.wallet.utils.findActivity

class ExploreDAppItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<DAppModel> {
    private val binding by lazy { ItemExploreDappBinding.bind(view) }

    private val activity = findActivity(view)

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[ExploreViewModel::class.java] }

    override fun bind(model: DAppModel) {
        with(binding) {
            Glide.with(iconView).load(model.logo).into(iconView)
            titleView.text = model.name
            descView.text = model.description
            categoryChip.text = model.category.uppercase()

            view.setOnClickListener {
                val url = if (isTestnet()) model.testnetUrl else model.url
                viewModel.onDAppClick(url!!)
                openBrowser(activity!!, url)
            }
        }
    }
}