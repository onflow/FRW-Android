package com.flowfoundation.wallet.page.send.transaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityTransactionSendBinding
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.page.address.AddressBookFragment
import com.flowfoundation.wallet.page.address.AddressBookViewModel
import com.flowfoundation.wallet.page.send.transaction.model.TransactionSendModel
import com.flowfoundation.wallet.page.send.transaction.presenter.TransactionSendPresenter
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.launch
import com.flowfoundation.wallet.utils.registerBarcodeLauncher

class TransactionSendActivity : BaseActivity() {

    private lateinit var binding: ActivityTransactionSendBinding
    private lateinit var presenter: TransactionSendPresenter
    private lateinit var viewModel: SelectSendAddressViewModel

    private val coinContractId by lazy { intent.getStringExtra(EXTRA_COIN_CONTRACT_ID)!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionSendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        supportFragmentManager.beginTransaction().replace(R.id.search_container, AddressBookFragment()).commit()

        binding.root.addStatusBarTopPadding()
        presenter = TransactionSendPresenter(supportFragmentManager, binding.addressContent, coinContractId)
        viewModel = ViewModelProvider(this)[SelectSendAddressViewModel::class.java].apply {
            onAddressSelectedLiveData.observe(this@TransactionSendActivity) { presenter.bind(TransactionSendModel(selectedAddress = it)) }
        }
        ViewModelProvider(this)[AddressBookViewModel::class.java].apply {
            clearEditTextFocusLiveData.observe(this@TransactionSendActivity) { presenter.bind(TransactionSendModel(isClearInputFocus = it)) }
        }

        setupToolbar()
        val barcodeLauncher = registerBarcodeLauncher { presenter.bind(TransactionSendModel(qrcode = it)) }
        binding.scanButton.setOnClickListener { barcodeLauncher.launch() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.send_to.res2String()
    }

    companion object {
        private const val EXTRA_COIN_CONTRACT_ID = "extra_coin_contract_id"
        fun launch(context: Context, coinContractId: String = FlowCoinListManager.getFlowCoinContractId()) {
            context.startActivity(Intent(context, TransactionSendActivity::class.java).apply {
                putExtra(EXTRA_COIN_CONTRACT_ID, coinContractId)
            })
        }
    }
}