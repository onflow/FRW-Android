package io.outblock.lilico.page.wallet.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.base.recyclerview.BaseAdapter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.databinding.LayoutWalletAccountItemBinding
import io.outblock.lilico.manager.account.Account
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.utils.loadAvatar
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.widgets.ProgressDialog


class WalletAccountAdapter : BaseAdapter<Account>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return WalletAccountViewHolder(parent.inflate(R.layout.layout_wallet_account_item))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as WalletAccountViewHolder).bind(getItem(position))
    }
}

private class WalletAccountViewHolder(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<Account> {

    private val binding by lazy { LayoutWalletAccountItemBinding.bind(view) }

    private var model: Account? = null
    private val progressDialog by lazy { ProgressDialog(view.context) }

    init {
        view.setOnClickListener {
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

    override fun bind(model: Account) {
        this.model = model
        with(binding) {
            val userInfo = model.userInfo
            avatarView.loadAvatar(userInfo.avatar)
            usernameView.text = userInfo.username
            addressView.text = model.wallet?.walletAddress()
        }
    }

}