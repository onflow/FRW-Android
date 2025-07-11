package com.flowfoundation.wallet.page.nft.nftdetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.crowdin.platform.util.inflateWithCrowdin
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityNftDetailBinding
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.ar.ArActivity
import com.flowfoundation.wallet.page.main.HomeTab
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.main.MainActivityViewModel
import com.flowfoundation.wallet.page.nft.nftdetail.model.NftDetailModel
import com.flowfoundation.wallet.page.nft.nftdetail.presenter.NftDetailPresenter
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.video
import com.flowfoundation.wallet.page.collection.CollectionActivity
import com.flowfoundation.wallet.utils.isNightMode
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import org.onflow.flow.models.TransactionStatus


class NftDetailActivity : BaseActivity(), OnTransactionStateChange {

    private val uniqueId by lazy { intent.getStringExtra(EXTRA_UNIQUE_ID)!! }
    private val collectionAddress by lazy { intent.getStringExtra(EXTRA_COLLECTION_CONTRACT_ID)!! }
    private val fromAddress by lazy { intent.getStringExtra(EXTRA_FROM_ADDRESS) }
    private val collectionContract by lazy { intent.getStringExtra(EXTRA_COLLECTION_CONTRACT) }
    private val sourceTabIndex by lazy { intent.getIntExtra(EXTRA_SOURCE_TAB, -1) }

    private var hasNavigatedBack = false

    private lateinit var binding: ActivityNftDetailBinding
    private lateinit var presenter: NftDetailPresenter
    private lateinit var viewModel: NftDetailViewModel
    private lateinit var mainActivityViewModel: MainActivityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNftDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()
        TransactionStateManager.addOnTransactionStateChange(this)
        presenter = NftDetailPresenter(this, binding)
        viewModel = ViewModelProvider(this)[NftDetailViewModel::class.java].apply {
            nftLiveData.observe(this@NftDetailActivity) { presenter.bind(NftDetailModel(nft = it)) }
            load(uniqueId, collectionAddress, collectionContract)
        }
        mainActivityViewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]
    }

    override fun onPause() {
        super.onPause()
        presenter.bind(NftDetailModel(onPause = true))
    }

    override fun onRestart() {
        super.onRestart()
        presenter.bind(NftDetailModel(onRestart = true))
    }

    override fun onDestroy() {
        super.onDestroy()
        TransactionStateManager.removeOnTransactionStateCallback(this)
        presenter.bind(NftDetailModel(onDestroy = true))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflateWithCrowdin(R.menu.nft_detail, menu, resources)
        val menuItem = menu.findItem(R.id.view_in_ar)
        if (isARCameraSupported()) {
            menuItem.actionView?.setOnClickListener { openInAr() }
        } else {
            menuItem.setVisible(false)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.view_in_ar -> openInAr()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun openInAr() {
        val nft = viewModel.nftLiveData.value ?: return
        ArActivity.launch(this, nft.cover(), nft.video())
    }

    private fun isARCameraSupported(): Boolean {
        return false
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_AR)
//        } else {
//            false
//        }
    }

    override fun onTransactionStateChange() {
        val transaction = TransactionStateManager.getLastVisibleTransaction() ?: return
        
        if (transaction.type == TransactionState.TYPE_TRANSFER_NFT || transaction.type == TransactionState.TYPE_NFT || transaction.type == TransactionState.TYPE_MOVE_NFT) {
            // Check if transaction is either processing, finalized, executed, or sealed
            if (!hasNavigatedBack && (transaction.isProcessing() || transaction.isExecuted() || transaction.isSealed())) {
                hasNavigatedBack = true
                
                // Navigate back to the collection page instead of the home tab
                navigateToCollectionPage()
                return
            }
        }
    }

    private fun TransactionState.isExecuted(): Boolean = state == TransactionStatus.EXECUTED.ordinal
    private fun TransactionState.isSealed(): Boolean = state == TransactionStatus.SEALED.ordinal

    private fun navigateToTab(tab: HomeTab) {
        // Check if we're already finishing to avoid duplicate finish requests
        if (isFinishing || isDestroyed) {
            return
        }
        
        // Always launch MainActivity with the target tab and use safer flags
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("extra_target_tab", tab.index)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        try {
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            finish()
        }
    }

    private fun navigateToCollectionPage() {
        // Check if we're already finishing to avoid duplicate finish requests
        if (isFinishing || isDestroyed) {
            return
        }
        
        try {
            // Navigate back to the main NFTs tab
            MainActivity.launch(this, HomeTab.NFT)
            finish()
        } catch (e: Exception) {
            // Fallback to original home tab navigation if main NFTs navigation fails
            if (sourceTabIndex != -1) {
                val sourceTab = HomeTab.values().getOrNull(sourceTabIndex)
                if (sourceTab != null) {
                    navigateToTab(sourceTab)
                    return
                }
            }
            finish()
        }
    }

    fun onTransactionSubmitted() {
        // Called when a transaction is submitted to trigger delayed navigation
        setupNavigationOnTransactionSubmit()
    }

    private fun setupNavigationOnTransactionSubmit() {
        // Set up a delayed navigation that will trigger shortly after transaction submission
        // This ensures we navigate back even if the activity becomes inactive before transaction completion
        
        // Post with a short delay to allow transaction to be submitted
        Handler(Looper.getMainLooper()).postDelayed({
            if (!hasNavigatedBack && !isFinishing && !isDestroyed) {
                hasNavigatedBack = true
                
                if (sourceTabIndex != -1) {
                    val sourceTab = HomeTab.values().getOrNull(sourceTabIndex)
                    if (sourceTab != null) {
                        navigateToTab(sourceTab)
                        return@postDelayed
                    }
                }
                
                finish()
            }
        }, 3000) // Increased to 3 seconds to reduce race condition chance
    }

    companion object {
        private const val EXTRA_UNIQUE_ID = "unique_id"
        private const val EXTRA_FROM_ADDRESS = "extra_from_address"
        private const val EXTRA_COLLECTION_CONTRACT_ID = "extra_collection_contract_id"
        private const val EXTRA_COLLECTION_CONTRACT = "extra_collection_contract"
        private const val EXTRA_SOURCE_TAB = "extra_source_tab"

        fun launch(context: Context, nft: Nft, sourceTab: HomeTab? = null) {
            context.startActivity(Intent(context, NftDetailActivity::class.java).apply {
                putExtra(EXTRA_UNIQUE_ID, nft.uniqueId())
                putExtra(EXTRA_COLLECTION_CONTRACT_ID, nft.collectionAddress)
                putExtra(EXTRA_COLLECTION_CONTRACT, nft.contractName())
                putExtra(EXTRA_FROM_ADDRESS, WalletManager.selectedWalletAddress())
                sourceTab?.let { putExtra(EXTRA_SOURCE_TAB, it.index) }
            })
        }

        // Backward compatibility method
        fun launch(context: Context, uniqueId: String, collectionContractId: String,
                   collectionContract: String, fromAddress: String? = WalletManager.selectedWalletAddress(), sourceTab: HomeTab? = null) {
            val finalFromAddress = fromAddress?.takeIf { it.isNotEmpty() } ?: WalletManager.selectedWalletAddress()
            val intent = Intent(context, NftDetailActivity::class.java)
            intent.putExtra(EXTRA_UNIQUE_ID, uniqueId)
            intent.putExtra(EXTRA_COLLECTION_CONTRACT_ID, collectionContractId)
            intent.putExtra(EXTRA_COLLECTION_CONTRACT, collectionContract)
            intent.putExtra(EXTRA_FROM_ADDRESS, finalFromAddress)
            sourceTab?.let { intent.putExtra(EXTRA_SOURCE_TAB, it.index) }
            context.startActivity(intent)
        }
    }
}