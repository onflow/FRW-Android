package com.flowfoundation.wallet.page.nft.nftlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentNftGridBinding
import com.flowfoundation.wallet.page.nft.nftlist.adapter.NFTListAdapter
import com.flowfoundation.wallet.utils.extensions.res2dip
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration

internal class NftGridFragment : Fragment() {

    private lateinit var binding: FragmentNftGridBinding
    private lateinit var viewModel: NftViewModel

    private val adapter by lazy { NFTListAdapter() }

    private val dividerSize by lazy { R.dimen.nft_list_divider_size.res2dip().toDouble() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNftGridBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        binding.root.setBackgroundResource(R.color.background)
        viewModel = ViewModelProvider(requireActivity())[NftViewModel::class.java].apply {
            gridNftLiveData.observe(viewLifecycleOwner) { adapter.setNewDiffData(it) }
            requestGrid()
        }
    }

    private fun setupRecyclerView() {
        with(binding.recyclerView) {
            adapter = this@NftGridFragment.adapter
            layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (this@NftGridFragment.adapter.isSingleLineItem(position)) spanCount else 1
                    }
                }
            }
            addItemDecoration(
                GridSpaceItemDecoration(
                    vertical = dividerSize,
                    horizontal = dividerSize,
                    start = dividerSize,
                    end = dividerSize
                )
            )
        }
    }

    companion object {
        fun newInstance(): NftGridFragment {
            return NftGridFragment()
        }
    }
}