package com.flowfoundation.wallet.manager.wallpaper.model

import com.flowfoundation.wallet.R


enum class Wallpaper(val id: Int, val drawableId: Int, val isDynamic: Boolean) {
    DYNAMIC_FLOW(0, R.drawable.dynamic_flow_line, true),
    DYNAMIC_ETH(1, R.drawable.dynamic_eth, true),
    STATIC_LIGHT_GREEN(2, R.drawable.wallpaper_static_light_green, false),
    STATIC_GREEN(3, R.drawable.wallpaper_static_green, false),
    STATIC_ORANGE(4, R.drawable.wallpaper_static_orange, false),
    STATIC_LIGHT_PURPLE(5, R.drawable.wallpaper_static_light_purple, false),
    STATIC_LIGHT_RED(6, R.drawable.wallpaper_static_light_red, false),
    STATIC_PURPLE(7, R.drawable.wallpaper_static_purple, false);

    companion object {

        @JvmStatic
        fun getWallpaper(id: Int): Wallpaper {
            return entries.firstOrNull { it.id == id } ?: STATIC_LIGHT_GREEN
        }
    }
}
