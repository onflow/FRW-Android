package com.flowfoundation.wallet.page.token.custom

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import androidx.transition.Fade
import androidx.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityAddCustomTokenBinding
import com.flowfoundation.wallet.page.token.custom.fragment.CustomTokenAddressInputFragment
import com.flowfoundation.wallet.page.token.custom.fragment.CustomTokenContractSelectFragment
import com.flowfoundation.wallet.page.token.custom.fragment.CustomTokenInfoImportFragment
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenOption
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.android.material.transition.MaterialSharedAxis
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class AddCustomTokenActivity : BaseActivity() {

    private lateinit var binding: ActivityAddCustomTokenBinding
    private lateinit var viewModel: CustomTokenViewModel
    private var currentOption: CustomTokenOption? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCustomTokenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.bg_1).light(!isNightMode(this))
            .applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()
        setupToolbar()
        viewModel = ViewModelProvider(this)[CustomTokenViewModel::class.java].apply {
            optionChangeLiveData.observe(this@AddCustomTokenActivity) {
                onOptionChange(it)
            }
            loadingLiveData.observe(this@AddCustomTokenActivity) { show ->
                uiScope {
                    if (show) {
                        binding.lavLoading.visible()
                    } else {
                        binding.lavLoading.gone()
                    }
                }
            }
            importSuccessLiveData.observe(this@AddCustomTokenActivity) { isSuccess ->
                if (isSuccess) {
                    toast(msgRes = R.string.add_token_success)
                    finish()
                } else {
                    toast(msgRes = R.string.invalid_evm_address)
                }
            }
            changeOption(CustomTokenOption.ADDRESS_INPUT)
        }
    }

    @SuppressLint("CommitTransaction")
    private fun onOptionChange(option: CustomTokenOption) {
        val transition = createTransition(currentOption, option)
        val fragment = when (option) {
            CustomTokenOption.ADDRESS_INPUT -> CustomTokenAddressInputFragment()
            CustomTokenOption.CONTRACT_SELECT -> CustomTokenContractSelectFragment()
            CustomTokenOption.INFO_IMPORT -> CustomTokenInfoImportFragment()
        }
        fragment.enterTransition = transition
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentOption = option
    }

    private fun createTransition(
        currentOption: CustomTokenOption?,
        option: CustomTokenOption
    ): Transition {
        if (currentOption == null) {
            return Fade().apply { duration = 50 }
        }
        val transition = MaterialSharedAxis(MaterialSharedAxis.X, true)

        transition.addTarget(currentOption.layoutId)
        transition.addTarget(option.layoutId)
        return transition
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, AddCustomTokenActivity::class.java))
        }
    }
}