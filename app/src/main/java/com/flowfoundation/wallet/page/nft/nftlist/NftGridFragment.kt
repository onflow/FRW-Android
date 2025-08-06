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

    // Responsive grid spacing dimensions
    private val horizontalSpacing by lazy { R.dimen.nft_grid_horizontal_spacing.res2dip().toDouble() }
    private val verticalSpacing by lazy { R.dimen.nft_grid_vertical_spacing.res2dip().toDouble() }
    private val startSpacing by lazy { R.dimen.nft_grid_start_spacing.res2dip().toDouble() }
    private val endSpacing by lazy { R.dimen.nft_grid_end_spacing.res2dip().toDouble() }
    private val topSpacing by lazy { R.dimen.nft_grid_top_spacing.res2dip().toDouble() }
    private val bottomSpacing by lazy { R.dimen.nft_grid_bottom_spacing.res2dip().toDouble() }

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
                    top = topSpacing,
                    bottom = bottomSpacing,
                    vertical = verticalSpacing,
                    horizontal = horizontalSpacing,
                    start = startSpacing,
                    end = endSpacing
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