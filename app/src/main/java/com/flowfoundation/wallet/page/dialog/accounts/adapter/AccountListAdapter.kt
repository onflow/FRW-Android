package com.flowfoundation.wallet.page.dialog.accounts.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemAccountListDialogBinding
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.ProgressDialog
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog

class AccountListAdapter : BaseAdapter<Any>() {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Account -> TYPE_ACCOUNT
            is LocalSwitchAccount -> TYPE_SWITCH_ACCOUNT
            else -> -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ACCOUNT -> AccountViewHolder(parent.inflate(R.layout.item_account_list_dialog))
            TYPE_SWITCH_ACCOUNT -> SwitchAccountViewHolder(parent.inflate(R.layout.item_account_list_dialog))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AccountViewHolder -> holder.bind(getItem(position) as Account)
            is SwitchAccountViewHolder -> holder.bind(getItem(position) as LocalSwitchAccount)
        }
    }

    companion object {
        private const val TYPE_ACCOUNT = 0
        private const val TYPE_SWITCH_ACCOUNT = 1
    }
}

private class SwitchAccountViewHolder(
    private val view: View
) : BaseViewHolder(view), BasePresenter<LocalSwitchAccount> {

    private val binding by lazy { ItemAccountListDialogBinding.bind(view) }

    private var model: LocalSwitchAccount? = null
    private val progressDialog by lazy { ProgressDialog(view.context) }

    init {
        view.setOnClickListener {
            if (isTestnet()) {
                SwitchNetworkDialog(view.context, DialogType.SWITCH).show()
            } else {
                model?.let {
                    progressDialog.show()
                    AccountManager.switch(it) {
                        uiScope {
                            progressDialog.dismiss()
                        }
                    }
                }
            }
        }
    }
    override fun bind(model: LocalSwitchAccount) {
        this.model = model
        with(binding) {
            avatarView.setImageResource(R.drawable.ic_placeholder)
            usernameView.text = model.username
            addressView.text = model.address
            checkedView.setVisible(false)
        }
    }

}

private class AccountViewHolder(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<Account> {

    private val binding by lazy { ItemAccountListDialogBinding.bind(view) }

    private var model: Account? = null
    private val progressDialog by lazy { ProgressDialog(view.context) }

    init {
        view.setOnClickListener {
            if (isTestnet()) {
                SwitchNetworkDialog(view.context, DialogType.SWITCH).show()
            } else {
                model?.let {
                    progressDialog.show()
                    AccountManager.switch(it) {
                        uiScope {
                            progressDialog.dismiss()
                        }
                    }
                }
            }
        }
    }

    override fun bind(model: Account) {
        this.model = model
        with(binding) {
            val userInfo = model.userInfo
            avatarView.loadAvatar(userInfo.avatar)
            usernameView.text = userInfo.nickname
            addressView.text = model.wallet?.walletAddress()
            checkedView.setVisible(model.isActive)
        }
    }
}
