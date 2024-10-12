package com.flowfoundation.wallet.page.profile.subpage.wallet

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.cache.storageInfoCache
import com.flowfoundation.wallet.databinding.ActivityWalletSettingBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.OnEmojiUpdate
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.page.emoji.EditWalletEmojiDialog
import com.flowfoundation.wallet.page.profile.subpage.claimdomain.ClaimDomainActivity
import com.flowfoundation.wallet.page.profile.subpage.claimdomain.checkMeowDomainClaimed
import com.flowfoundation.wallet.page.profile.subpage.wallet.dialog.WalletResetConfirmDialog
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.AccountKeyActivity
import com.flowfoundation.wallet.page.security.recovery.SecurityPrivateKeyActivity
import com.flowfoundation.wallet.page.security.recovery.SecurityPublicKeyActivity
import com.flowfoundation.wallet.page.security.recovery.SecurityRecoveryActivity
import com.flowfoundation.wallet.page.security.securityOpen
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible

class WalletSettingActivity : BaseActivity(), OnEmojiUpdate {

    private lateinit var binding: ActivityWalletSettingBinding
    private val walletAddress by lazy { intent.getStringExtra(EXTRA_WALLET_ADDRESS) ?: ""}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        AccountEmojiManager.addListener(this)
        binding.root.addStatusBarTopPadding()
        setupToolbar()
        setup()
//        checkMeowDomainClaimed()
        queryStorageInfo()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    @SuppressLint("SetTextI18n")
    private fun setup() {
        with(binding) {
            val isEVMAccount = EVMWalletManager.isEVMWalletAddress(walletAddress)

            if (CryptoProviderManager.getCurrentCryptoProvider() is HDWalletCryptoProvider) {
                llRecoveryLayout.visible()
                recoveryPreference.setOnClickListener {
                    securityOpen(SecurityRecoveryActivity.launchIntent(this@WalletSettingActivity))
                }
                privatePreference.setOnClickListener {
                    securityOpen(SecurityPrivateKeyActivity.launchIntent(this@WalletSettingActivity))
                }
            } else {
                llRecoveryLayout.gone()
                privatePreference.setOnClickListener {
                    securityOpen(SecurityPublicKeyActivity.launchIntent(this@WalletSettingActivity))
                }
            }

            accountKey.setOnClickListener {
                securityOpen(AccountKeyActivity.launchIntent(this@WalletSettingActivity))
            }

            uiScope { freeGasPreference.setChecked(isFreeGasPreferenceEnable()) }
            freeGasPreference.setOnCheckedChangeListener { uiScope { setFreeGasPreferenceEnable(it) } }

            resetButton.setOnClickListener { WalletResetConfirmDialog.show(supportFragmentManager) }

            claimButton.setOnClickListener { ClaimDomainActivity.launch(this@WalletSettingActivity) }

            uiScope {
                claimDomainWrapper.gone()
//                todo hide domain entrance for rebranding
//                claimDomainWrapper.setVisible(!isMeowDomainClaimed())
            }


            ioScope {
                val storageInfo = storageInfoCache().read() ?: return@ioScope
                uiScope {
                    group4.setVisible(isEVMAccount.not())
                    val progress = storageInfo.used.toFloat() / storageInfo.capacity
                    storageInfoUsed.text = (progress * 100).formatNum(3) + "%"
                    storageInfoCount.text = getString(
                        R.string.storage_info_count,
                        toHumanReadableSIPrefixes(storageInfo.used),
                        toHumanReadableSIPrefixes(storageInfo.capacity)
                    )
                    storageInfoProgress.progress = (progress * 1000).toInt()
                }
            }
            group2.setVisible(isEVMAccount.not())
            group3.setVisible(isEVMAccount.not())
            group4.setVisible(isEVMAccount.not())
            resetButton.setVisible(isEVMAccount.not())
            configureWalletEmojiInfo()
        }
    }

    private fun configureWalletEmojiInfo() {
        with(binding) {
            val emojiInfo = AccountEmojiManager.getEmojiByAddress(walletAddress)
            tvAccountIcon.text = Emoji.getEmojiById(emojiInfo.emojiId)
            tvAccountIcon.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(emojiInfo.emojiId))
            tvAccountName.text = emojiInfo.emojiName
            clAccountEmoji.setOnClickListener {
                val username = AccountManager.userInfo()?.username ?: ""
                EditWalletEmojiDialog(this@WalletSettingActivity, username, emojiInfo).show()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.account.res2String()
    }

    override fun onEmojiUpdate(userName: String, address: String, emojiId: Int, emojiName: String) {
        configureWalletEmojiInfo()
    }

    companion object {

        private const val EXTRA_WALLET_ADDRESS = "extra_wallet_address"

        fun launch(context: Context, address: String) {
            context.startActivity(Intent(context, WalletSettingActivity::class.java).apply {
                putExtra(EXTRA_WALLET_ADDRESS, address)
            })
        }
    }

}