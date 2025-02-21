package com.flowfoundation.wallet.page.nft.nftlist

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.appbar.AppBarLayout
import com.zackratos.ultimatebarx.ultimatebarx.statusBarHeight
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.cache.NftSelections
import com.flowfoundation.wallet.databinding.FragmentNftListBinding
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftlist.adapter.NFTListAdapter
import com.flowfoundation.wallet.page.nft.nftlist.model.CollectionItemModel
import com.flowfoundation.wallet.page.nft.nftlist.model.CollectionTabsModel
import com.flowfoundation.wallet.page.nft.nftlist.presenter.CollectionTabsPresenter
import com.flowfoundation.wallet.page.nft.nftlist.presenter.CollectionTitlePresenter
import com.flowfoundation.wallet.page.nft.nftlist.presenter.SelectionItemPresenter
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftFavoriteManager
import com.flowfoundation.wallet.utils.extensions.res2dip
import com.flowfoundation.wallet.utils.extensions.res2pix
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import jp.wasabeef.glide.transformations.BlurTransformation

internal class NftListFragment : Fragment() {

    private lateinit var binding: FragmentNftListBinding
    private lateinit var viewModel: NftViewModel

    private val nftAdapter by lazy { NFTListAdapter() }
    private val collectionsAdapter by lazy { NFTListAdapter() }

    private val dividerSize by lazy { R.dimen.nft_list_divider_size.res2dip().toDouble() }

    private val selectionPresenter by lazy { SelectionItemPresenter(binding.topSelectionHeader) }

    private val collectionTabsPresenter by lazy { CollectionTabsPresenter(binding.collectionTabs) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNftListBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupNftRecyclerView()
        setupCollectionsRecyclerView()
        setupScrollView()
        binding.root.setBackgroundResource(R.color.background)

        viewModel = ViewModelProvider(requireActivity())[NftViewModel::class.java].apply {
            if (WalletManager.isEVMAccountSelected()) {
                requestList()
            } else {
                requestList()
                requestChildAccountCollectionList()
            }
            listNftLiveData.observe(viewLifecycleOwner) { data -> updateListData(data) }
            collectionsLiveData.observe(viewLifecycleOwner) { data -> updateCollections(data) }
            favoriteLiveData.observe(viewLifecycleOwner) { updateFavorite(it) }
            favoriteIndexLiveData.observe(viewLifecycleOwner) { updateSelection(it) }
        }
        binding.topSelectionHeader.setVisible(WalletManager.isChildAccountSelected().not())
    }

    private fun updateFavorite(nfts: List<Nft>) {
        if (WalletManager.isChildAccountSelected()) {
            return
        }
        selectionPresenter.bind(NftSelections(nfts.toMutableList()))
        binding.backgroundWrapper.setVisible(nfts.isNotEmpty())
        if (nfts.isEmpty()) {
            Glide.with(binding.backgroundImage).clear(binding.backgroundImage)
        }
    }

    private fun updateListData(data: List<Any>) {
        nftAdapter.setNewDiffData(data)
        binding.nftRecyclerView.setVisible(!viewModel.isCollectionExpanded())
    }

    private fun updateCollections(data: List<CollectionItemModel>) {
        collectionsAdapter.setNewDiffData(data)
        collectionTabsPresenter.bind(CollectionTabsModel(data))
        binding.collectionRecyclerView.setVisible(viewModel.isCollectionExpanded())
        collectionTabsPresenter.bind(CollectionTabsModel(isExpand = !viewModel.isCollectionExpanded()))
    }

    private fun setupScrollView() {
        var preOffset = 0
        binding.appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            binding.backgroundWrapper.translationY = verticalOffset.toFloat()
            viewModel.onListScrollChange(-verticalOffset)
            if (preOffset != verticalOffset) {
                findSwipeRefreshLayout(binding.root)?.isEnabled = verticalOffset >= 0
            }
            preOffset = verticalOffset
        })
        with(binding.scrollView) {
            setPadding(paddingLeft, R.dimen.nft_tool_bar_height.res2pix() + statusBarHeight, paddingRight, paddingBottom)
        }
    }

    private fun setupNftRecyclerView() {
        with(binding.nftRecyclerView) {
            adapter = this@NftListFragment.nftAdapter
            layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (this@NftListFragment.nftAdapter.isSingleLineItem(position)) spanCount else 1
                    }
                }
            }
            addItemDecoration(GridSpaceItemDecoration(vertical = dividerSize, horizontal = dividerSize, start = dividerSize, end = dividerSize))
        }
    }

    private fun setupCollectionsRecyclerView() {
        with(binding.collectionRecyclerView) {
            adapter = this@NftListFragment.collectionsAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, dividerSize.toInt()))
        }
    }

    private fun updateSelection(index: Int) {
        if (WalletManager.isChildAccountSelected()) {
            return
        }
        if (index < 0) {
            Glide.with(binding.backgroundImage).clear(binding.backgroundImage)
        }
        ioScope {
            val nft = NftFavoriteManager.favoriteList().getOrNull(index) ?: return@ioScope
            uiScope {
                if (viewModel.favoriteIndexLiveData.value != index) {
                    return@uiScope
                }
                val oldUrl = binding.backgroundImage.tag as? String
                Glide.with(binding.backgroundImage)
                    .load(nft.getNFTCover())
                    .thumbnail(Glide.with(requireContext()).load(oldUrl).transform(BlurTransformation(10, 20)))
                    .transition(DrawableTransitionOptions.withCrossFade(250))
                    .transform(BlurTransformation(10, 20))
                    .into(binding.backgroundImage)
                binding.backgroundImage.tag = nft.cover()
            }
        }
    }

    companion object {
        fun newInstance(): NftListFragment {
            return NftListFragment()
        }
    }
}