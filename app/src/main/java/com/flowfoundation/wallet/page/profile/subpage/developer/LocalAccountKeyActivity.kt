package com.flowfoundation.wallet.page.profile.subpage.developer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityLocalAccountKeyBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.page.profile.subpage.developer.adapter.LocalAccountKeyAdapter
import com.flowfoundation.wallet.page.profile.subpage.developer.model.LocalAccountKey
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.uiScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX

class LocalAccountKeyActivity: BaseActivity()  {
    private lateinit var binding: ActivityLocalAccountKeyBinding
    private val keyListAdapter by lazy { LocalAccountKeyAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalAccountKeyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        with(binding.rvKeyList) {
            adapter = keyListAdapter
            layoutManager = LinearLayoutManager(context)
        }
        setupToolbar()
        loadKeyList()
    }

    private fun loadKeyList() {
        ioScope {
            val currentAccount = AccountManager.get() ?: return@ioScope
            val currentCryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return@ioScope
            val keyList = mutableListOf<Any>()
            keyList.add(R.string.current_account)
            keyList.add(LocalAccountKey(
                userId = currentAccount.wallet?.id ?: Firebase.auth.currentUser?.uid ?: "",
                userName = currentAccount.userInfo.username,
                publicKey = currentCryptoProvider.getPublicKey()
            ))
            val otherAccounts = AccountManager.list().mapNotNull {
                val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(it)
                if (it.isActive || cryptoProvider == null) {
                    null
                } else {
                    LocalAccountKey(
                        userId = it.wallet?.id.orEmpty(),
                        userName = it.userInfo.username,
                        publicKey = cryptoProvider.getPublicKey()
                    )
                }
            }.toList()
            if (otherAccounts.isNotEmpty()) {
                keyList.add(R.string.other_accounts)
            }
            keyList.addAll(otherAccounts)
            uiScope {
                keyListAdapter.setNewDiffData(keyList)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.account_keys.res2String()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, LocalAccountKeyActivity::class.java))
        }
    }
}