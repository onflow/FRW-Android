package com.flowfoundation.wallet.page.profile.subpage.theme.presenter

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemWallpaperImageBinding
import com.flowfoundation.wallet.manager.wallpaper.WallpaperManager
import com.flowfoundation.wallet.manager.wallpaper.model.Wallpaper
import com.flowfoundation.wallet.page.wallet.view.FadeAnimationBackground
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.getWallpaperId
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope


class WallpaperImagePresenter(
    private val view: View
): BaseViewHolder(view), BasePresenter<Wallpaper> {

    private val binding by lazy { ItemWallpaperImageBinding.bind(view) }

    override fun bind(model: Wallpaper) {
        ioScope {
            val id = getWallpaperId()
            uiScope {
                binding.ivSelected.setVisible(model.id == id)
            }
        }
        if (model.isDynamic) {
            binding.staticWallpaper.gone()
            uiScope {
                binding.dynamicWallpaper.setContent {
                    FadeAnimationBackground(
                        imageRes = model.drawableId,
                        itemPerRow = 8,
                        colorInt = R.color.accent_green.res2color()
                    )
                }
            }
            binding.dynamicWallpaper.visible()
        } else {
            binding.dynamicWallpaper.gone()
            binding.staticWallpaper.setImageResource(model.drawableId)
            binding.staticWallpaper.visible()
        }
        binding.root.setOnClickListener {
            WallpaperManager.selectWallpaper(model)
            val activity = BaseActivity.getCurrentActivity() ?: return@setOnClickListener
            activity.finish()
        }
    }
}