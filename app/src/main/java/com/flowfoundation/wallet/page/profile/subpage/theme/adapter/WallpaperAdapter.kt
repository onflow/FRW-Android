package com.flowfoundation.wallet.page.profile.subpage.theme.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.manager.wallpaper.model.Wallpaper
import com.flowfoundation.wallet.page.profile.subpage.theme.presenter.WallpaperImagePresenter
import com.flowfoundation.wallet.page.profile.subpage.theme.presenter.WallpaperTitlePresenter

class WallpaperAdapter: BaseAdapter<Any>() {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Int -> TYPE_TITLE
            is Wallpaper -> TYPE_WALLPAPER
            else -> -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TITLE -> WallpaperTitlePresenter(parent.inflate(R.layout.item_wallpaper_title))
            TYPE_WALLPAPER -> WallpaperImagePresenter(parent.inflate(R.layout.item_wallpaper_image))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is WallpaperTitlePresenter -> holder.bind(getItem(position) as Int)
            is WallpaperImagePresenter -> holder.bind(getItem(position) as Wallpaper)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val isSelected = payloads[0] as Boolean
            (holder as? WallpaperImagePresenter)?.updateSelectStatus(isSelected)
        }
    }

    companion object {
        private const val TYPE_TITLE = 0
        private const val TYPE_WALLPAPER = 1
    }
}