package com.flowfoundation.wallet.page.inbox

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.fragment.BaseFragment
import com.flowfoundation.wallet.page.inbox.adapter.InboxNftAdapter
import com.flowfoundation.wallet.page.inbox.adapter.InboxTokenAdapter
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class InboxListFragment : BaseFragment() {

    private val type by lazy { arguments?.getInt(EXTRA_TYPE)!! }
    private val recyclerView by lazy { view?.findViewById<RecyclerView>(R.id.recycler_view)!! }

    private val viewModel by lazy { ViewModelProvider(requireActivity())[InboxViewModel::class.java] }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_inbox_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(recyclerView) {
            adapter = if (type == TYPE_TOKEN) InboxTokenAdapter() else InboxNftAdapter()
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 8.dp2px().toInt(), LinearLayout.VERTICAL))
        }
        with(viewModel) {
            tokenListLiveData.observe(viewLifecycleOwner) { (recyclerView.adapter as? InboxTokenAdapter)?.setNewDiffData(it) }
            nftListLiveData.observe(viewLifecycleOwner) { (recyclerView.adapter as? InboxNftAdapter)?.setNewDiffData(it) }
        }
    }

    companion object {
        private const val EXTRA_TYPE = "type"

        const val TYPE_TOKEN = 0
        fun newInstance(type: Int): InboxListFragment {
            return InboxListFragment().apply {
                arguments = Bundle().apply { putInt(EXTRA_TYPE, type) }
            }
        }
    }
}