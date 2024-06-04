package com.flowfoundation.wallet.page.browser.presenter

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
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

class BrowserRecommendWordPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<RecommendModel> {

    private val textView by lazy { view.findViewById<TextView>(R.id.text_view) }

    override fun bind(model: RecommendModel) {
        val text = SpannableString(model.text).apply {
            val index = indexOf(model.query)
            if (index >= 0) {
                setSpan(
                    ForegroundColorSpan(R.color.note.res2color()),
                    index,
                    index + model.query.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        textView.text = text
        view.setOnClickListener {
            if (AppConfig.useInAppBrowser().not()) {
                model.text.toSearchUrl().openInSystemBrowser(view.context, true)
                return@setOnClickListener
            }
            model.viewModel.updateUrl(model.text.toSearchUrl())
        }
    }
}