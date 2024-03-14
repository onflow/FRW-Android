package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityChildAccountEditBinding
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.model.ChildAccountEditModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.presenter.ChildAccountEditPresenter
import com.flowfoundation.wallet.utils.CACHE_VIDEO_PATH
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.toFile
import com.flowfoundation.wallet.utils.uiScope
import java.io.File

class ChildAccountEditActivity : BaseActivity() {

    private val account by lazy { intent.getParcelableExtra<ChildAccount>(EXTRA_DATA) }
    private lateinit var binding: ActivityChildAccountEditBinding
    private lateinit var presenter: ChildAccountEditPresenter
    private lateinit var viewModel: ChildAccountEditViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (account == null) {
            finish()
            return
        }
        binding = ActivityChildAccountEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()

        presenter = ChildAccountEditPresenter(binding, this)

        viewModel = ViewModelProvider(this)[ChildAccountEditViewModel::class.java].apply {
            progressDialogVisibleLiveData.observe(this@ChildAccountEditActivity) { presenter.bind(ChildAccountEditModel(showProgressDialog = it)) }
            transactionFinishLiveData.observe(this@ChildAccountEditActivity) { finish() }
            bindAccount(account!!)
        }

        account?.let { presenter.bind(ChildAccountEditModel(account = account)) }

        setupToolbar()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        ioScope {
            val file = data?.data?.toFile(File(CACHE_VIDEO_PATH, "${System.currentTimeMillis()}.jpg").absolutePath) ?: return@ioScope
            uiScope {
                presenter.bind(ChildAccountEditModel(avatarFile = file))
                viewModel.updateAvatar(file.absolutePath)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.edit.res2String()
    }

    companion object {
        private const val EXTRA_DATA = "extra_data"


        fun launch(context: Context, account: ChildAccount) {
            context.startActivity(Intent(context, ChildAccountEditActivity::class.java).apply {
                putExtra(EXTRA_DATA, account)
            })
        }
    }
}