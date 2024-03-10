package io.outblock.lilico.page.transaction.record.presenter

import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.databinding.ActivityTransactionRecordBinding
import io.outblock.lilico.page.transaction.record.TransactionRecordActivity
import io.outblock.lilico.page.transaction.record.adapter.TransactionRecordListAdapter
import io.outblock.lilico.utils.extensions.dp2px
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.extensions.res2color
import io.outblock.lilico.widgets.itemdecoration.ColorDividerItemDecoration

class TransactionRecordPresenter(
    private val binding: ActivityTransactionRecordBinding,
    private val activity: TransactionRecordActivity,
) : BasePresenter<Int> {

    private val adapter by lazy { TransactionRecordListAdapter() }

    init {
        binding.refreshLayout.isEnabled = false
        binding.refreshLayout.setColorSchemeColors(R.color.salmon_primary.res2color())
//        binding.refreshLayout.post { binding.refreshLayout.isRefreshing = true }
        with(binding.recyclerView) {
            adapter = this@TransactionRecordPresenter.adapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 4.dp2px().toInt()))
        }
    }

    override fun bind(model: Int) {
        if (model > 0) {
            binding.toolbar.title = R.string.transactions.res2String() + " $model"
        }
    }

    fun setListData(list: List<Any>) {
        adapter.setNewDiffData(list)
    }

}