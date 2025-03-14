package com.flowfoundation.wallet.page.nft.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityNftSearchBinding
import com.flowfoundation.wallet.page.nft.search.presenter.NFTSearchPresenter
import com.flowfoundation.wallet.page.nft.search.viewmodel.NFTItemListViewModel
import com.flowfoundation.wallet.utils.isNightMode
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class NFTSearchActivity : BaseActivity() {

    private lateinit var binding: ActivityNftSearchBinding
    private lateinit var presenter: NFTSearchPresenter
    private lateinit var viewModel: NFTItemListViewModel
    private val accountAddress by lazy { intent.getStringExtra(EXTRA_ACCOUNT_ADDRESS).orEmpty() }
    private val collectionId by lazy { intent.getStringExtra(EXTRA_COLLECTION_ID).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNftSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()

        presenter = NFTSearchPresenter(this, binding)
        viewModel = ViewModelProvider(this)[NFTItemListViewModel::class.java].apply {
            nftListLiveData.observe(this@NFTSearchActivity) { pair ->
                presenter.bind(pair)
            }
            isLoadingLiveData.observe(this@NFTSearchActivity) {
                presenter.configureLoadingState(it)
            }
            loadingProgressLiveData.observe(this@NFTSearchActivity) { (current, total) ->
                presenter.updateLoadingProgress(current, total)
            }
            loadAllNFTs(fromAddress = accountAddress, collectionId = collectionId)
        }
    }


    companion object {
        private const val EXTRA_ACCOUNT_ADDRESS = "extra_account_address"
        private const val EXTRA_COLLECTION_ID = "extra_collection_id"
        fun launch(
            context: Context,
            collectionId: String,
            accountAddress: String
        ) {
            context.startActivity(
                Intent(context, NFTSearchActivity::class.java).apply {
                    putExtra(EXTRA_ACCOUNT_ADDRESS, accountAddress)
                    putExtra(EXTRA_COLLECTION_ID, collectionId)
                }
            )
        }
    }
}