package com.flowfoundation.wallet.page.profile.subpage.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityWallpaperSettingBinding
import com.flowfoundation.wallet.manager.wallpaper.OnWallpaperChange
import com.flowfoundation.wallet.manager.wallpaper.WallpaperManager
import com.flowfoundation.wallet.page.profile.subpage.theme.adapter.WallpaperAdapter
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.isNightMode
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX

class WallpaperSettingActivity : BaseActivity(), OnWallpaperChange {

    private lateinit var binding: ActivityWallpaperSettingBinding

    private val wallpaperAdapter = WallpaperAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWallpaperSettingBinding.inflate(layoutInflater)
        WallpaperManager.addListener(this)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background)
            .light(!isNightMode(this)).applyStatusBar()
        setupToolbar()
        with(binding.rvWallpaper) {
            layoutManager = GridLayoutManager(this@WallpaperSettingActivity, 2).apply {
                spanSizeLookup = object : SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        if (wallpaperAdapter.getItemViewType(position) == 0) {
                            return 2
                        }
                        return 1
                    }

                }
            }
            adapter = wallpaperAdapter
            val margin = 8f.dp2px().toInt()
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    outRect.set(margin, margin, margin, margin)

                }
            })
        }
        wallpaperAdapter.setNewDiffData(WallpaperManager.getWallpaperList())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, WallpaperSettingActivity::class.java))
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onWallpaperChange(id: Int, position: Int, previousPosition: Int) {
        wallpaperAdapter.notifyItemChanged(position, true)
        wallpaperAdapter.notifyItemChanged(previousPosition, false)
    }
}