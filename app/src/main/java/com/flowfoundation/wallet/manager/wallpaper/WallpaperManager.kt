package com.flowfoundation.wallet.manager.wallpaper

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.wallpaper.model.Wallpaper
import com.flowfoundation.wallet.utils.getWallpaperId
import com.flowfoundation.wallet.utils.setWallpaperId
import com.flowfoundation.wallet.utils.uiScope
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList


object WallpaperManager {

    private val listeners = CopyOnWriteArrayList<WeakReference<OnWallpaperChange>>()
    private var position = -1
    private val wallpaperList = listOf<Any>(
        R.string.wallpaper_dynamic,
        Wallpaper.DYNAMIC_FLOW,
        Wallpaper.DYNAMIC_ETH,
        R.string.wallpaper_static,
        Wallpaper.STATIC_LIGHT_RED,
        Wallpaper.STATIC_PURPLE,
        Wallpaper.STATIC_GREEN,
        Wallpaper.STATIC_ORANGE,
        Wallpaper.STATIC_LIGHT_PURPLE,
        Wallpaper.STATIC_LIGHT_GREEN
    )

    fun getWallpaperList(): List<Any> {
        return wallpaperList
    }

    suspend fun getWallpaper(): Wallpaper {
        return getWallpaperById(getWallpaperId())
    }

    fun getWallpaperById(id: Int): Wallpaper {
        position = wallpaperList.indexOfFirst { (it as? Wallpaper)?.id == id }
        return Wallpaper.getWallpaper(id)
    }

    fun selectWallpaper(wallpaper: Wallpaper) {
        val previousPosition = position
        position = wallpaperList.indexOfFirst { (it as? Wallpaper)?.id == wallpaper.id }
        setWallpaperId(wallpaper.id)
        dispatchListeners(wallpaper.id, position, previousPosition)
    }

    fun addListener(callback: OnWallpaperChange) {
        if (listeners.firstOrNull { it.get() == callback } != null) {
            return
        }
        uiScope {
            this.listeners.add(WeakReference(callback))
        }
    }

    private fun dispatchListeners(id: Int, position: Int, previousPosition: Int) {
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onWallpaperChange(id, position, previousPosition) }
        }
    }
}

interface OnWallpaperChange {
    fun onWallpaperChange(id: Int, position: Int, previousPosition: Int)
}
