package com.flowfoundation.wallet.page.security.recovery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.recyclerview.widget.GridLayoutManager
import com.instabug.library.Instabug
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivitySecurityRecoveryBinding
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicAdapter
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecurityRecoveryActivity : BaseActivity() {

    private lateinit var binding: ActivitySecurityRecoveryBinding

    private val adapter by lazy { MnemonicAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        initPhrases()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun initPhrases() {
        with(binding.mnemonicContainer) {
            setVisible()
            adapter = this@SecurityRecoveryActivity.adapter
            layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
            addItemDecoration(GridSpaceItemDecoration(vertical = 16.0))
            Instabug.addPrivateViews(this)
        }
        loadMnemonic()
        binding.stringContainer.setVisible(false)
        binding.copyButton.setOnClickListener { copyToClipboard(Wallet.store().mnemonic()) }
    }

    private fun loadMnemonic() {
        ioScope {
            val str = Wallet.store().mnemonic()
            withContext(Dispatchers.Main) {
                val list = str.split(" ").mapIndexed { index, s -> MnemonicModel(index + 1, s) }
                val result = mutableListOf<MnemonicModel>()
                (0 until list.size / 2).forEach { i ->
                    result.add(list[i])
                    result.add(list[i + list.size / 2])
                }
                adapter.setNewDiffData(result)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.recovery_phrase.res2String()
    }

    private fun copyToClipboard(text: String) {
        textToClipboard(text)
        toast(msgRes = R.string.copied_to_clipboard)
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(launchIntent(context))
        }

        fun launchIntent(context: Context): Intent = Intent(context, SecurityRecoveryActivity::class.java)
    }
}