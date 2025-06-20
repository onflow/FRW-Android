package com.flowfoundation.wallet.page.send.transaction.subpage.amount

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivitySendAmountBinding
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.main.HomeTab
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.SendAmountModel
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.presenter.SendAmountPresenter
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.models.TransactionStatus

class SendAmountActivity : BaseActivity(), OnTransactionStateChange {

    private val contact by lazy { intent.getParcelableExtra<AddressBookContact>(EXTRA_CONTACT)!! }
    private val coinContractId by lazy { intent.getStringExtra(EXTRA_COIN_CONTRACT_ID) }
    private val initialAmount by lazy { intent.getStringExtra(EXTRA_AMOUNT) }
    private val sourceTabIndex by lazy { intent.getIntExtra(EXTRA_SOURCE_TAB, -1) }

    private lateinit var binding: ActivitySendAmountBinding
    private lateinit var presenter: SendAmountPresenter
    private lateinit var viewModel: SendAmountViewModel

    private var hasNavigatedBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logd("SendAmountActivity", "onCreate: sourceTabIndex=$sourceTabIndex")
        binding = ActivitySendAmountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()
        TransactionStateManager.addOnTransactionStateChange(this)
        presenter = SendAmountPresenter(this, binding, contact)
        viewModel = ViewModelProvider(this)[SendAmountViewModel::class.java].apply {
            setContact(contact)
            setInitialAmount(initialAmount)
            FungibleTokenListManager.getTokenById(coinContractId.orEmpty())?.let { changeToken(it) }
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

    override fun onTransactionStateChange() {
        val transaction = TransactionStateManager.getLastVisibleTransaction() ?: return
        logd("SendAmountActivity", "Transaction state change: type=${transaction.type}, state=${transaction.state}, isProcessing=${transaction.isProcessing()}, hasNavigatedBack=$hasNavigatedBack, sourceTabIndex=$sourceTabIndex")
        
        if (transaction.type == TransactionState.TYPE_TRANSFER_COIN) {
            // Check if transaction is either processing, finalized, executed, or sealed
            if (!hasNavigatedBack && (transaction.isProcessing() || transaction.isFinalized() || transaction.isExecuted() || transaction.isSealed())) {
                hasNavigatedBack = true
                logd("SendAmountActivity", "Navigating back due to transaction state: ${transaction.state}")
                
                // Navigate back to the original tab if we have one
                if (sourceTabIndex != -1) {
                    val sourceTab = HomeTab.entries.getOrNull(sourceTabIndex)
                    if (sourceTab != null) {
                        logd("SendAmountActivity", "Navigating to source tab: $sourceTab")
                        navigateToTab(sourceTab)
                        return
                    }
                }
                
                // Fallback to just finishing the activity safely
                logd("SendAmountActivity", "No source tab, just finishing activity")
                finish()
                return
            }
        }
    }

    private fun TransactionState.isFinalized(): Boolean = state == TransactionStatus.FINALIZED.ordinal
    private fun TransactionState.isExecuted(): Boolean = state == TransactionStatus.EXECUTED.ordinal
    private fun TransactionState.isSealed(): Boolean = state == TransactionStatus.SEALED.ordinal

    private fun navigateToTab(tab: HomeTab) {
        logd("SendAmountActivity", "Attempting to navigate to tab: $tab")
        
        // Check if we're already finishing to avoid duplicate finish requests
        if (isFinishing || isDestroyed) {
            logd("SendAmountActivity", "Activity already finishing, skipping navigation")
            return
        }
        
        // Always launch MainActivity with the target tab and use safer flags
        logd("SendAmountActivity", "Launching MainActivity with tab: $tab and clearing task stack")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("extra_target_tab", tab.index)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        try {
            startActivity(intent)
            logd("SendAmountActivity", "Successfully started MainActivity, now finishing")
            finish()
        } catch (e: Exception) {
            android.util.Log.e("SendAmountActivity", "Error launching MainActivity: ${e.message}")
            finish()
        }
    }

    fun onTransactionSubmitted() {
        // Called when a transaction is submitted to trigger delayed navigation
        logd("SendAmountActivity", "Transaction submitted, setting up delayed navigation")
        setupNavigationOnTransactionSubmit()
    }

    private fun setupNavigationOnTransactionSubmit() {
        // Set up a delayed navigation that will trigger shortly after transaction submission
        // This ensures we navigate back even if the activity becomes inactive before transaction completion
        logd("SendAmountActivity", "Setting up delayed navigation with sourceTabIndex: $sourceTabIndex")
        
        // Post with a short delay to allow transaction to be submitted
        Handler(Looper.getMainLooper()).postDelayed({
            if (!hasNavigatedBack && !isFinishing && !isDestroyed) {
                logd("SendAmountActivity", "Executing delayed navigation")
                hasNavigatedBack = true
                
                if (sourceTabIndex != -1) {
                    val sourceTab = HomeTab.entries.getOrNull(sourceTabIndex)
                    if (sourceTab != null) {
                        logd("SendAmountActivity", "Delayed navigation to source tab: $sourceTab")
                        navigateToTab(sourceTab)
                        return@postDelayed
                    }
                }
                
                logd("SendAmountActivity", "Delayed navigation: finishing activity")
                finish()
            } else {
                logd("SendAmountActivity", "Skipping delayed navigation: hasNavigatedBack=$hasNavigatedBack, isFinishing=$isFinishing, isDestroyed=$isDestroyed")
            }
        }, 3000) // Increased to 3 seconds to reduce race condition chance
    }

    companion object {
        private const val EXTRA_CONTACT = "extra_contact"
        private const val EXTRA_COIN_CONTRACT_ID = "coin_contract_id"
        private const val EXTRA_AMOUNT = "extra_amount"
        private const val EXTRA_SOURCE_TAB = "extra_source_tab"

        fun launch(context: Context, contact: AddressBookContact, coinContractId: String?, amount: String? = null, sourceTab: HomeTab? = null) {
            context.startActivity(Intent(context, SendAmountActivity::class.java).apply {
                putExtra(EXTRA_CONTACT, contact)
                putExtra(EXTRA_COIN_CONTRACT_ID, coinContractId)
                putExtra(EXTRA_AMOUNT, amount)
                sourceTab?.let { putExtra(EXTRA_SOURCE_TAB, it.index) }
            })
        }
    }
}