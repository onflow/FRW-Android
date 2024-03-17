package com.flowfoundation.wallet.page.explore.presenter

import android.graphics.Color
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import androidx.transition.Visibility
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentExploreBinding
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.explore.ExploreFragment
import com.flowfoundation.wallet.page.explore.adapter.ExploreBookmarkAdapter
import com.flowfoundation.wallet.page.explore.adapter.ExploreDAppAdapter
import com.flowfoundation.wallet.page.explore.adapter.ExploreDAppTagsAdapter
import com.flowfoundation.wallet.page.explore.adapter.ExploreRecentAdapter
import com.flowfoundation.wallet.page.explore.model.ExploreModel
import com.flowfoundation.wallet.page.explore.subpage.BookmarkListDialog
import com.flowfoundation.wallet.page.explore.subpage.DAppListDialog
import com.flowfoundation.wallet.page.explore.subpage.RecentHistoryDialog
import com.flowfoundation.wallet.page.profile.subpage.claimdomain.ClaimDomainActivity
import com.flowfoundation.wallet.page.profile.subpage.claimdomain.MeowDomainClaimedStateChangeListener
import com.flowfoundation.wallet.page.profile.subpage.claimdomain.observeMeowDomainClaimedStateChange
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.location
import com.flowfoundation.wallet.utils.extensions.scrollToPositionForce
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isMeowDomainClaimed
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import kotlinx.coroutines.delay

class ExplorePresenter(
    private val fragment: ExploreFragment,
    private val binding: FragmentExploreBinding,
) : BasePresenter<ExploreModel>, MeowDomainClaimedStateChangeListener {

    private val recentAdapter by lazy { ExploreRecentAdapter(true) }
    private val bookmarkAdapter by lazy { ExploreBookmarkAdapter() }
    private val dappAdapter by lazy { ExploreDAppAdapter() }
    private val dappTagAdapter by lazy { ExploreDAppTagsAdapter() }

    private val activity by lazy { fragment.requireActivity() }

    init {
        binding.root.addStatusBarTopPadding()
        with(binding.recentListView) {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 9.dp2px().toInt(), LinearLayoutManager.HORIZONTAL))
            adapter = recentAdapter
        }

        with(binding.bookmarkListView) {
            layoutManager = GridLayoutManager(context, 5)
            addItemDecoration(
                GridSpaceItemDecoration(
                    start = 18.0,
                    end = 18.0,
                    horizontal = 14.0,
                    vertical = 16.0,
                )
            )
            adapter = bookmarkAdapter
        }

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
            recentMoreButton.setOnClickListener { RecentHistoryDialog.show(activity.supportFragmentManager) }
            bookmarkMoreButton.setOnClickListener { BookmarkListDialog.show(activity.supportFragmentManager) }
            dappMoreButton.setOnClickListener { DAppListDialog.show(activity.supportFragmentManager) }
            searchBox.root.setOnClickListener {
                uiScope {
                    searchBoxWrapper.setVisible(false, invisible = true)
                    delay(800)
                    searchBoxWrapper.setVisible(true)
                }
                openBrowser(activity, searchBoxPosition = searchBox.root.location())
            }
            claimButton.setOnClickListener { ClaimDomainActivity.launch(activity) }
        }
        observeMeowDomainClaimedStateChange(this)
        updateClaimDomainState()
    }

    override fun bind(model: ExploreModel) {
        model.recentList?.let {
            recentAdapter.setNewDiffData(it)
            binding.recentWrapper.setVisible(it.isNotEmpty())
            binding.recentListView.post { binding.recentListView.scrollToPositionForce(0) }
        }

        model.bookmarkList?.let {
            bookmarkAdapter.setNewDiffData(it)
            binding.bookmarkWrapper.setVisible(it.isNotEmpty())
        }

        model.dAppList?.let {
            dappAdapter.setNewDiffData(it)
            binding.dappWrapper.setVisible(it.isNotEmpty())
        }

        model.dAppTagList?.let {
            dappTagAdapter.setNewDiffData(it)
        }
    }

    override fun onDomainClaimedStateChange(isClaimed: Boolean) {
        updateClaimDomainState()
    }

    private fun updateClaimDomainState() {
        ioScope {
            binding.claimDomainWrapper.gone()
//            todo hide domain entrance for rebranding
//            val isClaimedDomain = isMeowDomainClaimed()
//            val isVisibleChange = binding.claimDomainWrapper.isVisible() == isClaimedDomain
//            if (isVisibleChange) {
//                uiScope {
//                    TransitionManager.beginDelayedTransition(binding.contentWrapper)
//                    binding.claimDomainWrapper.setVisible(!isClaimedDomain)
//                }
//            }
        }
    }
}