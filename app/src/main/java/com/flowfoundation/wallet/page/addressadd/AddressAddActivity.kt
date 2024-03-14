package com.flowfoundation.wallet.page.addressadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityAddAddressBinding
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.widgets.ProgressDialog

class AddressAddActivity : BaseActivity() {

    private lateinit var binding: ActivityAddAddressBinding

    private lateinit var viewModel: AddressAddViewModel

    private val progressDialog by lazy { ProgressDialog(this) }

    private val contact by lazy { intent.getParcelableExtra<AddressBookContact>(EXTRA_CONTACT) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddAddressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        setupToolbar()

        viewModel = ViewModelProvider(this)[AddressAddViewModel::class.java].apply {
            setContact(contact)
            resultLiveData.observe(this@AddressAddActivity) { onSaveResult(it) }
            addressVerifyStateLiveData.observe(this@AddressAddActivity) { updateAddressVerifyState(it) }
        }
        binding.root.addStatusBarTopPadding()
        binding.saveButton.setOnClickListener {
            progressDialog.show()
            binding.nameText.hideKeyboard()
            viewModel.save(binding.nameText.text.toString(), binding.addressText.text.toString())
        }
        binding.nameText.doOnTextChanged { _, _, _, _ -> updateSaveButtonState() }
        binding.addressText.doOnTextChanged { text, _, _, _ -> viewModel.checkAddressVerify(text.toString().lowercase().trim()) }
        updateAddressVerifyState(ADDRESS_VERIFY_STATE_IDLE)

        contact?.let {
            binding.nameText.setText(it.name())
            binding.addressText.setText(it.address)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun updateAddressVerifyState(state: Int) {
        with(binding) {
            progressBar.setVisible(state == ADDRESS_VERIFY_STATE_PENDING)
            stateIcon.setVisible(state != ADDRESS_VERIFY_STATE_PENDING && state != ADDRESS_VERIFY_STATE_IDLE)
            stateIcon.setImageResource(if (state == ADDRESS_VERIFY_STATE_SUCCESS) R.drawable.ic_username_success else R.drawable.ic_username_error)

            stateText.setText(
                when (state) {
                    ADDRESS_VERIFY_STATE_PENDING -> R.string.checking
                    ADDRESS_VERIFY_STATE_SUCCESS -> R.string.address_check_success
                    ADDRESS_VERIFY_STATE_FORMAT_ERROR -> R.string.address_check_format_error
                    ADDRESS_VERIFY_STATE_ERROR -> R.string.address_check_chain_error
                    else -> R.string.address_add_address_tip
                }
            )

            updateSaveButtonState()
        }
    }

    private fun updateSaveButtonState() {
        with(binding) {
            val isNameVerified = !nameText.text.isNullOrEmpty()
            val isAddressVerified = viewModel.addressVerifyStateLiveData.value == ADDRESS_VERIFY_STATE_SUCCESS
            binding.saveButton.isEnabled = isNameVerified && isAddressVerified
        }
    }

    private fun onSaveResult(isSuccess: Boolean) {
        progressDialog.dismiss()
        if (isSuccess) {
            finish()
        } else {
            Toast.makeText(this, "Save fail", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.add_address.res2String()
    }

    companion object {
        private const val EXTRA_CONTACT = "extra_contact"

        fun launch(context: Context, contact: AddressBookContact? = null) {
            context.startActivity(Intent(context, AddressAddActivity::class.java).apply {
                putExtra(EXTRA_CONTACT, contact)
            })
        }
    }
}