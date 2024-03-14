package com.flowfoundation.wallet.page.profile.subpage.logo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw

const val FLOW_WALLET_LOGO_DEFAULT = "com.flowfoundation.wallet.page.profile.subpage.logo.pages.FlowWalletLogoDefault"

private val logos = listOf(
    FLOW_WALLET_LOGO_DEFAULT,
)

fun changeAppIcon(context: Context, logo: String) {
    logw("xxx", "logos:$logos , logo:$logo")

    context.setComponentEnabledSetting(logo, true)
    logos.filter { it != logo }.forEach { context.setComponentEnabledSetting(it, false) }
}

private fun Context.setComponentEnabledSetting(cls: String, isEnable: Boolean) {
    val componentName = ComponentName(this, cls)

    val state: Int = packageManager.getComponentEnabledSetting(componentName)
    val isSystemEnabled =
        (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) || (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED && cls == FLOW_WALLET_LOGO_DEFAULT)

    logw("xxx", "cls = $cls, isEnable = $isEnable, state = $state, isSystemEnabled = $isSystemEnabled")

    if (isSystemEnabled == isEnable) return


    loge("xxx", "set cls = $cls to $isEnable")
    packageManager.setComponentEnabledSetting(
        componentName,
        if (isEnable) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        },
        PackageManager.DONT_KILL_APP
    )
}