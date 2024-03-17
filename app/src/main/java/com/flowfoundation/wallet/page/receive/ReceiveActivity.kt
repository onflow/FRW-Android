package com.flowfoundation.wallet.page.receive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivityReceiveBinding
import com.flowfoundation.wallet.page.receive.model.ReceiveModel
import com.flowfoundation.wallet.page.receive.presenter.ReceivePresenter
import com.flowfoundation.wallet.utils.isNightMode

class ReceiveActivity : BaseActivity() {

    private lateinit var presenter: ReceivePresenter
    private lateinit var viewModel: ReceiveViewModel
    private lateinit var binding: ActivityReceiveBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        presenter = ReceivePresenter(this, binding)
        viewModel = ViewModelProvider(this)[ReceiveViewModel::class.java].apply {
            walletLiveData.observe(this@ReceiveActivity) { presenter.bind(ReceiveModel(data = it)) }
            qrcodeLiveData.observe(this@ReceiveActivity) { presenter.bind(ReceiveModel(qrcode = it)) }
            load()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {

        fun launch(context: Context) {
            context.startActivity(Intent(context, ReceiveActivity::class.java).apply {
            })
        }
    }
}