package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.presenter

import android.annotation.SuppressLint
import android.view.View
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemAccessibleCoinBinding
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.CoinData
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.loadAvatar
import java.math.RoundingMode

/**
 * Created by Mengxy on 8/15/23.
 */
class AccessibleCoinPresenter(private val view: View): BaseViewHolder(view), BasePresenter<CoinData> {

    private val binding by lazy { ItemAccessibleCoinBinding.bind(view) }

    @SuppressLint("SetTextI18n")
    override fun bind(model: CoinData) {
        with(binding) {
            iconView.loadAvatar(model.icon)
            titleView.text = model.name
            coinBalanceView.text = "${model.balance.formatNum(roundingMode = RoundingMode.HALF_UP)} " + model.symbol.uppercase()
        }
    }
}