package com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.presenter

import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.WalletRestoreMnemonicViewModel
import com.flowfoundation.wallet.utils.findActivity

class MnemonicSuggestItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<String> {
    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[WalletRestoreMnemonicViewModel::class.java] }

    private val textView by lazy { view.findViewById<TextView>(R.id.text_view) }

    override fun bind(model: String) {
        textView.text = model

        view.setOnClickListener { viewModel.selectSuggest(model) }
    }
}