package com.flowfoundation.wallet.page.main.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.base.recyclerview.getItemView
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_MAINNET
import com.flowfoundation.wallet.manager.app.NETWORK_NAME_TESTNET
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.utils.extensions.capitalizeV2
import com.flowfoundation.wallet.utils.extensions.res2color
import com.lxj.xpopup.core.AttachPopupView
import com.lxj.xpopup.interfaces.OnSelectListener
import com.lxj.xpopup.widget.VerticalRecyclerView

@SuppressLint("ViewConstructor")
class NetworkPopupListView(
    context: Context,
    private val itemData: List<ItemData>
): AttachPopupView(context) {

    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.recyclerView) }
    private val adapter by lazy { Adapter() }

    private var selectListener: OnSelectListener? = null

    override fun getImplLayoutId(): Int = R.layout.network_popup_menu_root

    override fun onCreate() {
        with(recyclerView) {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@NetworkPopupListView.adapter
        }
        adapter.setNewDiffData(itemData)
    }

    override fun applyDarkTheme() {
        super.applyDarkTheme()
        (recyclerView as VerticalRecyclerView).setupDivider(true)
    }

    override fun applyLightTheme() {
        super.applyLightTheme()
        (recyclerView as VerticalRecyclerView).setupDivider(false)
    }

    fun setOnSelectListener(selectListener: OnSelectListener): NetworkPopupListView {
        this.selectListener = selectListener
        return this
    }

    private inner class Adapter : BaseAdapter<ItemData>() {
        inner class ViewHolder(val view: View) : BaseViewHolder(view) {
            private val bgView by lazy { view.findViewById<ConstraintLayout>(R.id.cl_layout) }
            private val titleView by lazy { view.findViewById<TextView>(R.id.tv_text) }
            private val ivCheckBox by lazy { view.findViewById<ImageView>(R.id.iv_check_box) }

            fun bind(item: ItemData) {
                val network = item.title.lowercase()

                val checkIcon = when(network) {
                    NETWORK_NAME_MAINNET -> R.drawable.ic_check_mainnet
                    NETWORK_NAME_TESTNET -> R.drawable.ic_check_testnet
                    else -> R.drawable.ic_check_network
                }

                val networkColor = when(network) {
                    NETWORK_NAME_MAINNET -> R.color.mainnet
                    NETWORK_NAME_TESTNET -> R.color.testnet
                    else -> R.color.text_2
                }

                if (network == chainNetWorkString()) {
                    ivCheckBox.setImageResource(checkIcon)
                    titleView.setTextColor(networkColor.res2color())
                    bgView.backgroundTintList = ColorStateList.valueOf(networkColor.res2color()).withAlpha(16)
                } else {
                    ivCheckBox.setImageResource(R.drawable.ic_check_network)
                    titleView.setTextColor(R.color.text_2.res2color())
                    bgView.backgroundTintList = ColorStateList.valueOf(R.color.transparent.res2color())
                }
                titleView.text = item.title.capitalizeV2()
                view.setOnClickListener {
                    selectListener?.onSelect(layoutPosition, item.title)
                    if (popupInfo.autoDismiss) dismiss()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return ViewHolder(parent.getItemView(R.layout.network_popup_menu_item))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val data = getItem(position)
            (holder as ViewHolder).bind(data)
        }
    }

    class ItemData(
        val title: String,
    )
}