package com.flowfoundation.wallet.page.browser.presenter

import android.view.View
import android.widget.TextView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.page.browser.model.RecommendModel
import com.flowfoundation.wallet.page.browser.toSearchUrl
import com.flowfoundation.wallet.utils.extensions.openInSystemBrowser
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setSpannableText

class BrowserRecommendWordPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<RecommendModel> {

    private val textView by lazy { view.findViewById<TextView>(R.id.text_view) }

    override fun bind(model: RecommendModel) {
        textView.setSpannableText(
            model.text,
            model.query,
            R.color.note.res2color()
        )
        view.setOnClickListener {
            if (AppConfig.useInAppBrowser().not()) {
                model.text.toSearchUrl().openInSystemBrowser(view.context, true)
                return@setOnClickListener
            }
            model.viewModel.updateUrl(model.text.toSearchUrl())
        }
    }
}