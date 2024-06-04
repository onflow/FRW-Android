package com.flowfoundation.wallet.page.token.addtoken.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemTokenListBinding
import com.flowfoundation.wallet.page.token.addtoken.AddTokenConfirmDialog
import com.flowfoundation.wallet.page.token.addtoken.model.TokenItem
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity

class TokenItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<TokenItem> {

    private val binding by lazy { ItemTokenListBinding.bind(view) }

    override fun bind(model: TokenItem) {
        with(binding) {
            nameView.text = model.coin.name
            symbolView.text = model.coin.symbol.uppercase()
            Glide.with(iconView).load(model.coin.icon()).into(iconView)
            stateButton.setOnClickListener {
                if (model.isNormalState()) {
                    AddTokenConfirmDialog.show((findActivity(view) as FragmentActivity).supportFragmentManager, model.coin)
                }
            }
            progressBar.setVisible(model.isAdding == true)
            stateButton.setVisible(model.isAdding != true)
            stateButton.setImageResource(if (model.isNormalState()) R.drawable.ic_add_circle else R.drawable.ic_check_round)
        }
    }
}