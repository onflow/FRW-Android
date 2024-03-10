package io.outblock.lilico.page.profile.subpage.wallet.key.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.outblock.lilico.R
import io.outblock.lilico.base.recyclerview.BaseAdapter
import io.outblock.lilico.page.profile.subpage.wallet.key.model.AccountKey
import io.outblock.lilico.page.profile.subpage.wallet.key.presenter.AccountKeyListItemPresenter

class AccountKeyListAdapter: BaseAdapter<AccountKey>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return AccountKeyListItemPresenter(parent.inflate(R.layout.layout_key_list_item))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AccountKeyListItemPresenter) {
            holder.bind(getItem(position))
        }
    }
}
