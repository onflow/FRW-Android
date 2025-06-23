package com.flowfoundation.wallet.page.profile.subpage.theme

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatDelegate
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivitySettingsThemeBinding
import com.flowfoundation.wallet.manager.wallpaper.OnWallpaperChange
import com.flowfoundation.wallet.manager.wallpaper.WallpaperManager
import com.flowfoundation.wallet.manager.wallpaper.model.Wallpaper
import com.flowfoundation.wallet.page.wallet.view.FadeAnimationBackground
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.getThemeMode
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateThemeMode

class ThemeSettingActivity : BaseActivity(), OnWallpaperChange {

    private lateinit var binding: ActivitySettingsThemeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()

        setupToolbar()

        with(binding) {
            lightGroup.setOnClickListener { updateTheme(AppCompatDelegate.MODE_NIGHT_NO) }
            darkGroup.setOnClickListener { updateTheme(AppCompatDelegate.MODE_NIGHT_YES) }
            autoPreference.setOnCheckedChangeListener { isAuto -> updateTheme(if (isAuto) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else AppCompatDelegate.MODE_NIGHT_NO) }
            cvWallpaper.setOnClickListener { WallpaperSettingActivity.launch(this@ThemeSettingActivity) }
        }

        uiScope { updateUi(getThemeMode()) }
        bindWallpaper()
        WallpaperManager.addListener(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun updateTheme(themeMode: Int) {
        AppCompatDelegate.setDefaultNightMode(themeMode)
        updateThemeMode(themeMode)
        updateUi(themeMode)
    }

    private fun updateUi(themeMode: Int) {
        with(binding) {
            lightCheckBox.setImageResource(if (themeMode == AppCompatDelegate.MODE_NIGHT_NO) R.drawable.ic_check_round else R.drawable.ic_check_normal)
            darkCheckBox.setImageResource(if (themeMode == AppCompatDelegate.MODE_NIGHT_YES) R.drawable.ic_check_round else R.drawable.ic_check_normal)
            val isAuto = themeMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            autoPreference.setChecked(isAuto)
            lightGroup.alpha = if (isAuto) 0.5f else 1.0f
            darkGroup.alpha = if (isAuto) 0.5f else 1.0f
            lightGroup.isEnabled = !isAuto
            darkGroup.isEnabled = !isAuto


        }
    }

    private fun bindWallpaper() {
        ioScope {
            val wallpaper = WallpaperManager.getWallpaper()
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
                        colorInt = R.color.colorSecondary.res2color()
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

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.appearance.res2String()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, ThemeSettingActivity::class.java))
        }
    }

    override fun onWallpaperChange(id: Int, position: Int, previousPosition: Int) {
        ioScope {
            val wallpaper = WallpaperManager.getWallpaperById(id)
            uiScope {
                loadWallpaper(wallpaper)
            }
        }
    }
}