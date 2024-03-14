package com.flowfoundation.wallet.page.explore.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemDappCategoryBinding
import com.flowfoundation.wallet.page.explore.ExploreViewModel
import com.flowfoundation.wallet.page.explore.model.DAppTagModel
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.findActivity

class ExploreDAppTagItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<DAppTagModel> {
    private val binding by lazy { ItemDappCategoryBinding.bind(view) }

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[ExploreViewModel::class.java] }

    override fun bind(model: DAppTagModel) {
        with(binding) {
            textView.text = model.category
            textView.setTextColor(if (model.isSelected) R.color.text.res2color() else R.color.text_sub.res2color())

            root.strokeColor = if (model.isSelected) R.color.violet1.res2color() else R.color.bg_icon.res2color()
            view.setOnClickListener {
                viewModel.selectDappTag(model.category)
            }
        }
    }
}