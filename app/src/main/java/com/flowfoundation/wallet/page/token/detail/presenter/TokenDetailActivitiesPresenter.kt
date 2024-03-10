package com.flowfoundation.wallet.page.token.detail.presenter

import android.transition.TransitionManager
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.LayoutTokenDetailActivitiesBinding
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.network.flowscan.contractId
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailActivitiesModel
import com.flowfoundation.wallet.page.transaction.record.TransactionRecordActivity
import com.flowfoundation.wallet.page.transaction.record.adapter.TransactionRecordListAdapter
import com.flowfoundation.wallet.utils.extensions.setVisible


class TokenDetailActivitiesPresenter(
    private val activity: AppCompatActivity,
    private val binding: LayoutTokenDetailActivitiesBinding,
    private val coin: FlowCoin,
) : BasePresenter<TokenDetailActivitiesModel> {

    private val adapter by lazy { TransactionRecordListAdapter() }

    init {
        with(binding.recyclerView) {
            adapter = this@TokenDetailActivitiesPresenter.adapter
            layoutManager = LinearLayoutManager(activity)
        }
        binding.activitiesMoreButton.setOnClickListener { TransactionRecordActivity.launch(activity, coin.contractId()) }
    }

    override fun bind(model: TokenDetailActivitiesModel) {
        model.recordList?.let {
            TransitionManager.beginDelayedTransition(binding.root.parent as ViewGroup)
            binding.root.setVisible(it.isNotEmpty())
            adapter.setNewDiffData(it)
        }
    }

}