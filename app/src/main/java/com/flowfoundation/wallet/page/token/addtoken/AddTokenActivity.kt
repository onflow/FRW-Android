package com.flowfoundation.wallet.page.token.addtoken

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivityAddTokenBinding
import com.flowfoundation.wallet.page.token.addtoken.model.AddTokenModel
import com.flowfoundation.wallet.page.token.addtoken.presenter.AddTokenPresenter
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.widgets.FlowLoadingDialog

class AddTokenActivity : BaseActivity() {

    private lateinit var presenter: AddTokenPresenter
    private lateinit var viewModel: AddTokenViewModel
    private lateinit var binding: ActivityAddTokenBinding

    private val loadingDialog by lazy { FlowLoadingDialog(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTokenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        presenter = AddTokenPresenter(this, binding)
        viewModel = ViewModelProvider(this)[AddTokenViewModel::class.java].apply {
            tokenListLiveData.observe(this@AddTokenActivity) {
                loadingDialog.dismiss()
                presenter.bind(AddTokenModel(data = it))
            }
            load()
            loadingDialog.show()
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
            context.startActivity(Intent(context, AddTokenActivity::class.java))
        }
    }
}