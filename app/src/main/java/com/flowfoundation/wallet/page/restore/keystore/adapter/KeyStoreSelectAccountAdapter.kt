package com.flowfoundation.wallet.page.restore.keystore.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.FrozenCheckbox


class KeyStoreSelectAccountAdapter: BaseAdapter<KeystoreAddress>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout
            .item_keystore_select_account, parent, false)
        return AccountViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as AccountViewHolder).bind(getItem(position), position)
    }

    fun getSelectedKeystoreAddress(): KeystoreAddress? {
        return if (selectedPosition == -1) {
            null
        } else {
            getItem(position = selectedPosition)
        }
    }

    fun updateSelectedAddress(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position

        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }

    inner class AccountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bgView: View = itemView.findViewById(R.id.view_background)
        private val cbView: FrozenCheckbox = itemView.findViewById(R.id.cb_select)
        private val addressView: TextView = itemView.findViewById(R.id.tv_address)

        fun bind(keystoreAddress: KeystoreAddress, position: Int) {
            addressView.text = keystoreAddress.address
            val isSelected = selectedPosition == position
            bgView.setVisible(isSelected)
            cbView.isChecked = isSelected
            addressView.setTypeface(null, if(isSelected) Typeface.BOLD else Typeface.NORMAL)
            itemView.setOnClickListener {
                updateSelectedAddress(position)
            }
        }
    }
}