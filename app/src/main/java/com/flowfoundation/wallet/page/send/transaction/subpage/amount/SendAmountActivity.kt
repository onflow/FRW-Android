package com.flowfoundation.wallet.page.send.transaction.subpage.amount

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivitySendAmountBinding
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.SendAmountModel
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.presenter.SendAmountPresenter
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.toast

class SendAmountActivity : BaseActivity(), OnTransactionStateChange {

    private val contact by lazy { intent.getParcelableExtra<AddressBookContact>(EXTRA_CONTACT)!! }
    private val coinSymbol by lazy { intent.getStringExtra(EXTRA_COIN_SYMBOL) }

    private lateinit var binding: ActivitySendAmountBinding
    private lateinit var presenter: SendAmountPresenter
    private lateinit var viewModel: SendAmountViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendAmountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()
        TransactionStateManager.addOnTransactionStateChange(this)
        presenter = SendAmountPresenter(this, binding, contact)
        viewModel = ViewModelProvider(this)[SendAmountViewModel::class.java].apply {
            setContact(contact)
            FlowCoinListManager.getCoin(coinSymbol.orEmpty())?.let { changeCoin(it) }
            balanceLiveData.observe(this@SendAmountActivity) { presenter.bind(SendAmountModel(balance = it)) }
            onCoinSwap.observe(this@SendAmountActivity) { presenter.bind(SendAmountModel(onCoinSwap = true)) }
            load()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TransactionStateManager.removeOnTransactionStateCallback(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        private const val EXTRA_CONTACT = "extra_contact"
        private const val EXTRA_COIN_SYMBOL = "coin_symbol"

        fun launch(context: Context, contact: AddressBookContact, coinSymbol: String?) {
            context.startActivity(Intent(context, SendAmountActivity::class.java).apply {
                putExtra(EXTRA_CONTACT, contact)
                putExtra(EXTRA_COIN_SYMBOL, coinSymbol)
            })
        }
    }

    override fun onTransactionStateChange() {
        val transaction = TransactionStateManager.getLastVisibleTransaction() ?: return
        if (transaction.type == TransactionState.TYPE_TRANSFER_COIN) {
            if (transaction.isSuccess()) {
                toast(msgRes = R.string.send_success)
                finish()
            } else if (transaction.isFailed()) {
                toast(msgRes = R.string.send_failed)
            }
        }
    }
}