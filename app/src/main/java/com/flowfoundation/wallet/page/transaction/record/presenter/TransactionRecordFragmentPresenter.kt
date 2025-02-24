package com.flowfoundation.wallet.page.transaction.record.presenter

import android.annotation.SuppressLint
import android.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentTransactionRecordBinding
import com.flowfoundation.wallet.page.transaction.record.adapter.TransactionRecordListAdapter
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class TransactionRecordFragmentPresenter(
    private val binding: FragmentTransactionRecordBinding,
    private val activity: FragmentActivity
) : BasePresenter<Int> {

    private val adapter by lazy { TransactionRecordListAdapter() }

    init {
        binding.refreshLayout.isEnabled = false
        binding.refreshLayout.setColorSchemeColors(R.color.salmon_primary.res2color())

        with(binding.recyclerView) {
            adapter = this@TransactionRecordFragmentPresenter.adapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 4.dp2px().toInt()))
        }
    }

    @SuppressLint("SetTextI18n")
    override fun bind(model: Int) {
        if (model > 0) {
            binding.tvTitle.text = R.string.transactions.res2String() + " $model"
        }
    }

    fun setListData(list: List<Any>) {
        adapter.setNewDiffData(list)
    }
}
