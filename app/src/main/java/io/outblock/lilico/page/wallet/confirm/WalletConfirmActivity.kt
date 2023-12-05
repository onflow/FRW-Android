package io.outblock.lilico.page.wallet.confirm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityWalletConfirmBinding
import io.outblock.lilico.page.wallet.confirm.model.ConfirmUserInfo
import io.outblock.lilico.page.wallet.confirm.presenter.WalletConfirmPresenter
import io.outblock.lilico.utils.isNightMode


class WalletConfirmActivity : BaseActivity() {
    private lateinit var binding: ActivityWalletConfirmBinding
    private lateinit var presenter: WalletConfirmPresenter
    private val userAvatar by lazy { intent.getStringExtra(EXTRA_USER_AVATAR) ?: "" }
    private val userName by lazy { intent.getStringExtra(EXTRA_USER_NAME) ?: "" }
    private val walletAddress by lazy { intent.getStringExtra(EXTRA_WALLET_ADDRESS) ?: "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background)
            .light(!isNightMode(this)).applyStatusBar()

        presenter = WalletConfirmPresenter(this, binding).apply {
            bind(ConfirmUserInfo(userAvatar, userName, walletAddress))
        }
        setupToolbar()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onDestroy() {
        presenter.dismissProgress()
        super.onDestroy()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        private const val EXTRA_USER_AVATAR = "extra_user_avatar"
        private const val EXTRA_USER_NAME = "extra_user_name"
        private const val EXTRA_WALLET_ADDRESS = "extra_wallet_address"

        fun launch(
            context: Context,
            userAvatar: String,
            userName: String,
            walletAddress: String,
        ) {
            context.startActivity(Intent(context, WalletConfirmActivity::class.java).apply {
                putExtra(EXTRA_USER_AVATAR, userAvatar)
                putExtra(EXTRA_USER_NAME, userName)
                putExtra(EXTRA_WALLET_ADDRESS, walletAddress)
            })
        }
    }
}