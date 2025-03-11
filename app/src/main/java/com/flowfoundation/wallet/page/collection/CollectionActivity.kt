package com.flowfoundation.wallet.page.collection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivityCollectionBinding
import com.flowfoundation.wallet.page.collection.model.CollectionContentModel
import com.flowfoundation.wallet.page.collection.presenter.CollectionContentPresenter
import com.flowfoundation.wallet.utils.isNightMode

class CollectionActivity : BaseActivity() {

    private lateinit var presenter: CollectionContentPresenter
    private lateinit var viewModel: CollectionViewModel
    private lateinit var binding: ActivityCollectionBinding

    private val contractName by lazy { intent.getStringExtra(EXTRA_CONTRACT_NAME).orEmpty() }
    private val collectionLogo by lazy { intent.getStringExtra(EXTRA_COLLECTION_LOGO).orEmpty() }
    private val collectionName by lazy { intent.getStringExtra(EXTRA_COLLECTION_NAME).orEmpty() }
    private val collectionSize by lazy { intent.getIntExtra(EXTRA_COLLECTION_SIZE, 0) }
    private val accountAddress by lazy { intent.getStringExtra(EXTRA_ACCOUNT_ADDRESS).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()

        presenter = CollectionContentPresenter(this, binding).apply {
            bindInfo(collectionLogo, collectionName, collectionSize)
        }
        viewModel = ViewModelProvider(this)[CollectionViewModel::class.java].apply {
            dataLiveData.observe(this@CollectionActivity) {
                presenter.bind(
                    CollectionContentModel(
                        data = it
                    )
                )
            }
            collectionLiveData.observe(this@CollectionActivity) {
                presenter.bind(
                    CollectionContentModel(collection = it)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load(contractName, accountAddress, collectionSize)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        private const val EXTRA_CONTRACT_NAME = "extra_address"
        private const val EXTRA_COLLECTION_LOGO = "extra_collection_logo"
        private const val EXTRA_COLLECTION_NAME = "extra_collection_name"
        private const val EXTRA_COLLECTION_SIZE = "extra_collection_size"
        private const val EXTRA_ACCOUNT_ADDRESS = "extra_account_address"

        fun launch(
            context: Context,
            contractName: String,
            logo: String? = "",
            name: String? = "",
            size: Int? = 0,
            accountAddress: String? = "",
        ) {
            context.startActivity(Intent(context, CollectionActivity::class.java).apply {
                putExtra(EXTRA_CONTRACT_NAME, contractName)
                putExtra(EXTRA_ACCOUNT_ADDRESS, accountAddress)
                putExtra(EXTRA_COLLECTION_LOGO, logo)
                putExtra(EXTRA_COLLECTION_NAME, name)
                putExtra(EXTRA_COLLECTION_SIZE, size)
            })
        }
    }
}