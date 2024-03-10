package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.view.View
import android.view.ViewGroup
import com.zackratos.ultimatebarx.ultimatebarx.statusBarHeight
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.nft.nftlist.model.HeaderPlaceholderModel
import com.flowfoundation.wallet.utils.extensions.res2pix

class HeaderPlaceholderPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<HeaderPlaceholderModel> {

    private val baseHeight by lazy { R.dimen.nft_tool_bar_height.res2pix() + statusBarHeight }
    private val dividerHeight by lazy { R.dimen.nft_list_divider_size.res2pix() }

    override fun bind(model: HeaderPlaceholderModel) {
        val layoutParam =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (model.addDivider) baseHeight else baseHeight - dividerHeight)
        view.layoutParams = layoutParam
    }
}