package com.flowfoundation.wallet.page.address

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.crowdin.platform.util.inflateWithCrowdin
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityAddressBookBinding
import com.flowfoundation.wallet.page.address.model.AddressBookActivityModel
import com.flowfoundation.wallet.page.address.presenter.AddressBookActivityPresenter
import com.flowfoundation.wallet.page.addressadd.AddressAddActivity
import com.flowfoundation.wallet.utils.isNightMode

class AddressBookActivity : BaseActivity() {

    private lateinit var binding: ActivityAddressBookBinding
    private lateinit var presenter: AddressBookActivityPresenter
    private lateinit var viewModel: AddressBookViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddressBookBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        presenter = AddressBookActivityPresenter(this, binding)
        viewModel = ViewModelProvider(this)[AddressBookViewModel::class.java].apply {
            clearEditTextFocusLiveData.observe(this@AddressBookActivity) { presenter.bind(AddressBookActivityModel(isClearInputFocus = it)) }
        }

        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, AddressBookFragment()).commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflateWithCrowdin(R.menu.address_book, menu, resources)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_add -> AddressAddActivity.launch(this)
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, AddressBookActivity::class.java))
        }
    }
}