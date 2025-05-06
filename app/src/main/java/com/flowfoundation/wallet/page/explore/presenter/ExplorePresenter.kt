package com.flowfoundation.wallet.page.explore.presenter

import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentExploreBinding
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.explore.ExploreFragment
import com.flowfoundation.wallet.page.explore.adapter.ExploreDAppAdapter
import com.flowfoundation.wallet.page.explore.adapter.ExploreDAppTagsAdapter
import com.flowfoundation.wallet.page.explore.model.ExploreModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.location
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import kotlinx.coroutines.delay

class ExplorePresenter(
    private val fragment: ExploreFragment,
    private val binding: FragmentExploreBinding,
) : BasePresenter<ExploreModel> {
    private val dappAdapter by lazy { ExploreDAppAdapter() }
    private val dappTagAdapter by lazy { ExploreDAppTagsAdapter() }

    private val activity by lazy { fragment.requireActivity() }

    init {
        binding.root.addStatusBarTopPadding()

        with(binding.dappListView) {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 12.dp2px().toInt(), LinearLayoutManager.VERTICAL))
            adapter = dappAdapter
        }

        with(binding.dappTabs) {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 4.dp2px().toInt(), LinearLayoutManager.HORIZONTAL))
            adapter = dappTagAdapter
        }

        with(binding) {
            searchBox.root.setOnClickListener {
                uiScope {
                    searchBoxWrapper.setVisible(false, invisible = true)
                    delay(800)
                    searchBoxWrapper.setVisible(true)
                }
                openBrowser(activity, searchBoxPosition = searchBox.root.location())
            }
        }
        // observeMeowDomainClaimedStateChange(this)
    }

    override fun bind(model: ExploreModel) {
        model.dAppList?.let {
            dappAdapter.setNewDiffData(it)
            binding.dappWrapper.setVisible(it.isNotEmpty() && AppConfig.showDappList())
        }

        model.dAppTagList?.let {
            dappTagAdapter.setNewDiffData(it)
        }
    }
}