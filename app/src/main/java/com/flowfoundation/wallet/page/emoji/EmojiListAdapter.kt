package com.flowfoundation.wallet.page.emoji

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible

class EmojiListAdapter(
    private var selectedPosition: Int,
    private val callback: (Int) -> Unit
) : BaseAdapter<Emoji>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_emoji_list, parent, false)
        return EmojiItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        (holder as EmojiItemViewHolder).let { holder ->
            val emoji = getItem(position)
            if (emoji == Emoji.EMPTY) {
                holder.iconView.text = ""
                holder.iconView.backgroundTintList =
                    ColorStateList.valueOf(R.color.transparent.res2color())
                holder.selectedView.setVisible(false)
            } else {
                holder.iconView.text = emoji.emoji
                holder.iconView.backgroundTintList = ColorStateList.valueOf(emoji.colorRes.res2color())
                holder.selectedView.setVisible(position == selectedPosition)
                holder.itemView.setOnClickListener {
                    val previousSelectedPosition = selectedPosition
                    selectedPosition = holder.adapterPosition
                    notifyItemChanged(previousSelectedPosition)
                    notifyItemChanged(selectedPosition)
                    callback.invoke(emoji.id)
                }
            }
        }
    }

    internal class EmojiItemViewHolder(view: View) : BaseViewHolder(view) {
        val iconView: TextView by lazy { view.findViewById<TextView>(R.id.tv_account_icon) }
        val selectedView: View by lazy { view.findViewById<View>(R.id.view_selected) }
    }
}
