package com.flowfoundation.wallet.page.nft.nftdetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.flowfoundation.wallet.page.ar.ArActivity
import com.flowfoundation.wallet.page.nft.nftdetail.model.NftDetailModel
import com.flowfoundation.wallet.page.nft.nftdetail.presenter.NftDetailPresenter
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.video
import com.flowfoundation.wallet.page.send.nft.NftSendModel
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.toast
import com.google.gson.Gson
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class NftDetailActivity : BaseActivity(), OnTransactionStateChange {

    private val uniqueId by lazy { intent.getStringExtra(EXTRA_NFT_UNIQUE_ID)!! }
    private val collectionContract by lazy { intent.getStringExtra(EXTRA_COLLECTION_CONTRACT) }
    private val fromAddress by lazy { intent.getStringExtra(EXTRA_FROM_ADDRESS) }
    private lateinit var binding: ActivityNftDetailBinding
    private lateinit var presenter: NftDetailPresenter
    private lateinit var viewModel: NftDetailViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNftDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()
        TransactionStateManager.addOnTransactionStateChange(this)
        presenter = NftDetailPresenter(this, binding)
        viewModel = ViewModelProvider(this)[NftDetailViewModel::class.java].apply {
            nftLiveData.observe(this@NftDetailActivity) { presenter.bind(NftDetailModel(nft = it, fromAddress = fromAddress)) }
            load(uniqueId, collectionContract)
        }
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

    companion object {
        private const val EXTRA_NFT_UNIQUE_ID = "extra_nft_unique_id"
        private const val EXTRA_FROM_ADDRESS = "extra_from_address"
        private const val EXTRA_COLLECTION_CONTRACT = "extra_collection_contract"

        fun launch(context: Context, uniqueId: String, collectionContract: String,
                   fromAddress: String? = WalletManager.selectedWalletAddress()) {
            val finalFromAddress = fromAddress?.takeIf { it.isNotEmpty() } ?: WalletManager.selectedWalletAddress()
            val intent = Intent(context, NftDetailActivity::class.java)
            intent.putExtra(EXTRA_NFT_UNIQUE_ID, uniqueId)
            intent.putExtra(EXTRA_COLLECTION_CONTRACT, collectionContract)
            intent.putExtra(EXTRA_FROM_ADDRESS, finalFromAddress)
            context.startActivity(intent)
        }
    }

    override fun onTransactionStateChange() {
        val transaction = TransactionStateManager.getLastVisibleTransaction() ?: return
        when (transaction.type) {
            TransactionState.TYPE_MOVE_NFT -> {
                if (uniqueId == transaction.data) {
                    if (transaction.isSuccess()) {
                        toast(msgRes = R.string.move_nft_success)
                        finish()
                    } else if (transaction.isFailed()) {
                        toast(msgRes = R.string.move_nft_failed)
                    }
                }
            }
            TransactionState.TYPE_TRANSFER_NFT -> {
                try {
                    val model = Gson().fromJson(transaction.data, NftSendModel::class.java)
                    if (uniqueId == model.nft.uniqueId()) {
                        if (transaction.isSuccess()) {
                            toast(msgRes = R.string.send_nft_success)
                            finish()
                        } else if (transaction.isFailed()) {
                            toast(msgRes = R.string.send_nft_failed)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}