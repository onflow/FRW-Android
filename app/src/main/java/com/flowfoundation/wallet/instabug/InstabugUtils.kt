package com.flowfoundation.wallet.instabug

import android.app.Application
import com.flowfoundation.wallet.BuildConfig
import com.instabug.library.Feature
import com.instabug.library.Instabug
import com.instabug.library.IssueType
import com.instabug.library.MaskingType
import com.instabug.library.ReproConfigurations
import com.instabug.library.ReproMode
import com.instabug.library.invocation.InstabugInvocationEvent
import com.instabug.library.ui.onboarding.WelcomeMessage
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.isTesting


fun instabugInitialize(application: Application) {
    if (isTesting()) {
        return
    }
    if (isDev()) {
        Instabug.Builder(application, BuildConfig.INSTABUG_TOKEN_DEV)
            .setInvocationEvents(InstabugInvocationEvent.SHAKE, InstabugInvocationEvent.FLOATING_BUTTON)
            .setTrackingUserStepsState(Feature.State.ENABLED)
            .setReproConfigurations(
                ReproConfigurations.Builder()
                .setIssueMode(IssueType.All, ReproMode.EnableWithScreenshots)
                .build())
            .setAutoMaskScreenshotsTypes(MaskingType.MASK_NOTHING)
            .build()
        Instabug.setWelcomeMessageState(WelcomeMessage.State.BETA)
    } else {
        Instabug.Builder(application, BuildConfig.INSTABUG_TOKEN_PROD)
            .setTrackingUserStepsState(Feature.State.DISABLED)
            .setReproConfigurations(
                ReproConfigurations.Builder()
                    .setIssueMode(IssueType.All, ReproMode.Disable)
                    .build())
            .setAutoMaskScreenshotsTypes(MaskingType.MASK_NOTHING)
            .build()
        Instabug.setWelcomeMessageState(WelcomeMessage.State.DISABLED)
    }
}