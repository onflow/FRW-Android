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
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.ProgressDialog
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog

class AccountListAdapter : BaseAdapter<Account>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return AccountViewHolder(parent.inflate(R.layout.item_account_list_dialog))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as AccountViewHolder).bind(getItem(position))
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
            if (isTestnet() || isPreviewnet()) {
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
            usernameView.text = userInfo.username
            addressView.text = model.wallet?.walletAddress()
            checkedView.setVisible(model.isActive)
        }
    }
}
