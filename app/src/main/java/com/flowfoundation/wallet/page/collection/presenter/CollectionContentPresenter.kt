package com.flowfoundation.wallet.page.collection.presenter

import android.animation.ArgbEvaluator
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityCollectionBinding
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.NftCollectionWrapper
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.collection.CollectionActivity
import com.flowfoundation.wallet.page.collection.model.CollectionContentModel
import com.flowfoundation.wallet.page.nft.nftlist.adapter.NFTListAdapter
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.res2dip
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlin.math.min

class CollectionContentPresenter(
    private val activity: CollectionActivity,
    private val binding: ActivityCollectionBinding,
) : BasePresenter<CollectionContentModel> {
    private val adapter by lazy { NFTListAdapter() }

    private val dividerSize by lazy { R.dimen.nft_list_divider_size.res2dip().toDouble() }

    private val screenHeight by lazy { ScreenUtils.getScreenHeight() }

    init {
        with(binding.recyclerView) {
            adapter = this@CollectionContentPresenter.adapter
            layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (this@CollectionContentPresenter.adapter.isSingleLineItem(position)) spanCount else 1
                    }
                }
            }
            addItemDecoration(
                GridSpaceItemDecoration(vertical = dividerSize, horizontal = dividerSize, start = dividerSize, end = dividerSize)
            )
            minimumHeight = screenHeight - 222.dp2px().toInt()
        }

        setupToolbar()

        binding.scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            updateToolbarColor(scrollY)
        })
        binding.backgroundImage.layoutParams.height = ScreenUtils.getScreenHeight()
    }

    override fun bind(model: CollectionContentModel) {
        model.data?.let {
            binding.progressBar.setVisible(it.isEmpty())
            adapter.setNewDiffData(it)
        }
        model.collection?.let { bindHeader(it) }
    }

    fun bindInfo(logo: String, name: String, size: Int) {
        with(binding) {
            Glide.with(coverView).load(logo).transform(CenterCrop(), RoundedCorners(16.dp2px().toInt())).into(coverView)
            Glide.with(backgroundImage).load(logo)
                .transition(DrawableTransitionOptions.withCrossFade(100))
                .transform(BlurTransformation(15, 30))
                .into(backgroundImage)

            titleView.text = name
            subtitleView.text = activity.getString(R.string.collectibles_count, size)

            toolbar.title = name

            exploreButton.setOnClickListener {  }
        }
    }

    private fun bindHeader(collectionWrapper: NftCollectionWrapper) {
        val collection = collectionWrapper.collection ?: return
        with(binding) {
            Glide.with(coverView).load(collection.logo()).transform(CenterCrop(), RoundedCorners(16.dp2px().toInt())).into(coverView)
            Glide.with(backgroundImage).load(collection.logo())
                .transition(DrawableTransitionOptions.withCrossFade(100))
                .transform(BlurTransformation(15, 30))
                .into(backgroundImage)

            titleView.text = collection.name
            subtitleView.text = activity.getString(R.string.collectibles_count, collectionWrapper.count)

            toolbar.title = collection.name

            collection.officialWebsite.let { url ->
                exploreButton.setOnClickListener { openBrowser(activity, url) }
            }
        }
        bindAccessible(collection)
    }

    private fun bindAccessible(collection: NftCollection) {
        if (ChildAccountCollectionManager.isNFTCollectionAccessible(collection.id)) {
            binding.inaccessibleTip.gone()
            return
        }
        val accountName = WalletManager.childAccount(WalletManager.selectedWalletAddress())?.name ?: R.string.default_child_account_name.res2String()
        binding.tvInaccessibleTip.text = activity.getString(R.string.inaccessible_token_tip, collection.name, accountName)
        binding.inaccessibleTip.visible()
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        binding.toolbar.addStatusBarTopPadding()
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = ""
    }

    private fun updateToolbarColor(scrollY: Int) {
        val progress = min(scrollY / (screenHeight * 0.15f), 1.0f)
        binding.toolbar.setBackgroundColor(
            ArgbEvaluator().evaluate(
                progress,
                R.color.transparent.res2color(),
                R.color.background.res2color(),
            ) as Int
        )
        binding.toolbar.setTitleTextColor(
            ArgbEvaluator().evaluate(
                progress,
                R.color.transparent.res2color(),
                R.color.text.res2color(),
            ) as Int
        )
    }
}