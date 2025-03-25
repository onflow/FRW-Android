package com.flowfoundation.wallet.page.security.recovery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivitySecurityPublicKeyBinding
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.instabug.library.Instabug

class SecurityPublicKeyActivity : BaseActivity() {

    private lateinit var binding: ActivitySecurityPublicKeyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityPublicKeyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        initPrivateKey()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun initPrivateKey() {
        with(binding) {
            val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return
            publicKeyView.text = cryptoProvider.getPublicKey()

            publicKeyCopyButton.setOnClickListener { copyToClipboard(cryptoProvider.getPublicKey()) }
            tvLearnMore.setOnClickListener { openBrowser(this@SecurityPublicKeyActivity, "https://frw.gitbook.io/doc/faq/faq#where-is-my-seed-phrase-i-cant-find-it-on-flow-wallet-ios-or-android") }

            hashAlgorithm.text = getString(R.string.hash_algorithm, cryptoProvider.getHashAlgorithm().algorithm)
            signAlgorithm.text = getString(R.string.sign_algorithm, cryptoProvider.getSignatureAlgorithm().id)
            Instabug.addPrivateViews(privateKeyView)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.private_key.res2String()
    }

    private fun copyToClipboard(text: String) {
        textToClipboard(text)
        toast(msgRes = R.string.copied_to_clipboard)
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(launchIntent(context))
        }

        fun launchIntent(context: Context): Intent = Intent(context, SecurityPublicKeyActivity::class.java)
    }
}