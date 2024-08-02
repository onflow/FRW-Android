package com.flowfoundation.wallet.page.wallet.presenter

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentCoordinatorWalletBinding
import com.flowfoundation.wallet.firebase.analytics.reportEvent
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.notification.WalletNotificationManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallpaper.WallpaperManager
import com.flowfoundation.wallet.manager.wallpaper.model.Wallpaper
import com.flowfoundation.wallet.page.main.presenter.openDrawerLayout
import com.flowfoundation.wallet.page.wallet.WalletFragmentViewModel
import com.flowfoundation.wallet.page.wallet.adapter.WalletFragmentAdapter
import com.flowfoundation.wallet.page.wallet.dialog.ChangeWallpaperDialog
import com.flowfoundation.wallet.page.wallet.model.WalletFragmentModel
import com.flowfoundation.wallet.page.wallet.view.FadeAnimationBackground
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class WalletFragmentPresenter(
    private val fragment: Fragment,
    private val binding: FragmentCoordinatorWalletBinding,
) : BasePresenter<WalletFragmentModel> {

    private val recyclerView = binding.recyclerView
    private val adapter by lazy { WalletFragmentAdapter() }

    private val viewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletFragmentViewModel::class.java] }

    init {
        with(recyclerView) {
            this.adapter = this@WalletFragmentPresenter.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                ColorDividerItemDecoration(
                    Color.TRANSPARENT,
                    2.dp2px().toInt(),
                    LinearLayout.VERTICAL
                )
            )
        }
        with(binding.refreshLayout) {
            setOnRefreshListener { viewModel.load(isRefresh = true) }
            setColorSchemeColors(R.color.colorSecondary.res2color())
        }

        binding.cvAvatar.setOnClickListener {
            openDrawerLayout(fragment.requireContext())
        }
        binding.toolBarLayout.setOnLongClickListener {
            ChangeWallpaperDialog.show(fragment.childFragmentManager)
            true
        }
        bindUserInfo()
        bindWallpaper()
    }

    private fun bindWallpaper() {
        ioScope {
            val wallpaper = WallpaperManager.getWallpaper()
            uiScope {
                loadWallpaper(wallpaper)
            }
        }
    }

    override fun bind(model: WalletFragmentModel) {
        model.data?.let {
            reportEvent("wallet_coin_list_loaded", mapOf("count" to it.size.toString()))
            adapter.setNewDiffData(it)
            binding.refreshLayout.isRefreshing = false
            bindUserInfo()
        }
    }

    private fun bindUserInfo() {
        ioScope {
            val address = WalletManager.selectedWalletAddress()
            if (WalletManager.isChildAccountSelected()) {
                val icon = WalletManager.childAccount(address)?.icon ?: ""
                if (icon.isNotEmpty()) {
                    uiScope {
                        binding.ivAvatar.loadAvatar(icon)
                        binding.ivAvatar.visible()
                        binding.tvAvatar.gone()
                    }
                }
            } else {
                val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)
                uiScope {
                    binding.tvAvatar.text = Emoji.getEmojiById(emojiInfo.emojiId)
                    binding.tvAvatar.backgroundTintList =
                        ColorStateList.valueOf(Emoji.getEmojiColorRes(emojiInfo.emojiId))
                    binding.tvAvatar.visible()
                    binding.ivAvatar.gone()
                }
            }
            binding.viewMask.setVisible(WalletNotificationManager.haveNotification() || isNightMode())
        }
    }

    fun onWallpaperChange(id: Int) {
        ioScope {
            val wallpaper = WallpaperManager.getWallpaperById(id)
            uiScope {
                loadWallpaper(wallpaper)
            }
        }
    }

    private fun loadWallpaper(wallpaper: Wallpaper) {
        if (wallpaper.isDynamic) {
            binding.staticWallpaper.gone()
            uiScope {
                binding.dynamicWallpaper.setContent {
                    FadeAnimationBackground(
                        imageRes = wallpaper.drawableId,
                        itemPerRow = 8,
                        colorInt = R.color.accent_green.res2color()
                    )
                }
            }
            binding.dynamicWallpaper.visible()
        } else {
            binding.dynamicWallpaper.gone()
            binding.staticWallpaper.setImageResource(wallpaper.drawableId)
            binding.staticWallpaper.visible()
        }
    }

}