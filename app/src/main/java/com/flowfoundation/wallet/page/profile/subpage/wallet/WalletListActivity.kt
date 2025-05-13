package com.flowfoundation.wallet.page.profile.subpage.wallet

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.size
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityWalletListBinding
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.OnEmojiUpdate
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalanceWithAddress
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_USERNAME
import com.flowfoundation.wallet.page.walletcreate.WalletCreateActivity
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.format
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.shortenEVMString
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import java.math.BigDecimal


class WalletListActivity : BaseActivity(), OnEmojiUpdate {

    private lateinit var binding: ActivityWalletListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background)
            .light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()

        binding.root.addStatusBarTopPadding()
        AccountEmojiManager.addListener(this)
        setupToolbar()
        initView()
    }

    private fun initView() {
        with(binding) {
            ioScope {
                uiScope {
                    llMainWallet.removeAllViews()

                    // todo multi account
                    val wallet = WalletManager.wallet()?.wallet() ?: return@uiScope
                    val list = mutableListOf<WalletData?>().apply {
                        add(wallet)
                    }.filterNotNull()

                    if (list.isEmpty()) {
                        return@uiScope
                    }

                    list.forEach { walletItem ->
                        val itemView = LayoutInflater.from(this@WalletListActivity)
                            .inflate(R.layout.item_account_list, llMainWallet, false)
                        val emoji = AccountEmojiManager.getEmojiByAddress(walletItem.address())
                        (itemView as ViewGroup).setupWallet(
                            WalletItemData(
                                address = walletItem.address()?.toAddress() ?: "",
                                emojiId = emoji.emojiId,
                                emojiName = emoji.emojiName
                            ), false
                        )
                        llMainWallet.addView(itemView)
                    }
                    llVmWallet.removeAllViews()
                    if (EVMWalletManager.showEVMAccount(chainNetWorkString())) {
                        EVMWalletManager.getEVMAccount()?.let {
                            val childView = LayoutInflater.from(root.context)
                                .inflate(R.layout.item_account_list, llVmWallet, false)
                            val emoji = AccountEmojiManager.getEmojiByAddress(it.address)
                            (childView as ViewGroup).setupWallet(
                                WalletItemData(
                                    address = it.address,
                                    emojiId = emoji.emojiId,
                                    emojiName = emoji.emojiName

                                ),
                                isEVMAccount = true
                            )
                            llVmWallet.addView(childView)
                        }
                    }

                    tvVmWallet.setVisible(llVmWallet.size > 0)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun ViewGroup.setupWallet(wallet: WalletItemData, isEVMAccount: Boolean) {

        val iconView = findViewById<TextView>(R.id.tv_account_icon)
        val nameView = findViewById<TextView>(R.id.tv_account_name)
        val addressView = findViewById<TextView>(R.id.tv_account_address)
        val balanceView = findViewById<TextView>(R.id.tv_account_balance)
        val evmLabel = findViewById<TextView>(R.id.tv_evm_label)

        iconView.text = Emoji.getEmojiById(wallet.emojiId)
        iconView.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(wallet.emojiId))
        nameView.text = wallet.emojiName
        evmLabel.setVisible(isEVMAccount)
        addressView.text = "(${shortenEVMString(wallet.address.toAddress())})"

        bindWalletBalance(balanceView, wallet.address.toAddress())

        setOnClickListener {
            WalletSettingActivity.launch(this@WalletListActivity, wallet.address)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindWalletBalance(balanceView: TextView?, address: String) {
        balanceView?.let {
            ioScope {
                val balance = if (EVMWalletManager.isEVMWalletAddress(address)) {
                    cadenceQueryCOATokenBalance()
                } else {
                    cadenceQueryTokenBalanceWithAddress(
                        FungibleTokenListManager.getFlowToken(),
                        address
                    )
                } ?: BigDecimal.ZERO
                uiScope {
                    it.text = "${balance.format()} FLOW"
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_add -> {
                if (isTestnet()) {
                    SwitchNetworkDialog(this, DialogType.CREATE).show()
                } else {
                    WalletCreateActivity.launch(this, step = WALLET_CREATE_STEP_USERNAME)
                }
            }

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.account_list.res2String()
    }

    private class WalletItemData(
        val address: String,
        val emojiId: Int,
        val emojiName: String
    )

    companion object {

        fun launch(context: Context) {
            context.startActivity(Intent(context, WalletListActivity::class.java))
        }
    }

    override fun onEmojiUpdate(userName: String, address: String, emojiId: Int, emojiName: String) {
        initView()
    }
}