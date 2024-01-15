package io.outblock.lilico.instabug

import android.app.Application
import com.instabug.library.Feature
import com.instabug.library.Instabug
import com.instabug.library.IssueType
import com.instabug.library.MaskingType
import com.instabug.library.ReproConfigurations
import com.instabug.library.ReproMode
import com.instabug.library.invocation.InstabugInvocationEvent
import com.instabug.library.ui.onboarding.WelcomeMessage
import io.outblock.lilico.manager.env.EnvKey
import io.outblock.lilico.utils.isDev
import io.outblock.lilico.utils.isTesting


fun instabugInitialize(application: Application) {
    if (isDev() || isTesting()) {
        Instabug.Builder(application, EnvKey.get("INSTABUG_TOKEN_DEV"))
            .setInvocationEvents(InstabugInvocationEvent.SHAKE, InstabugInvocationEvent.FLOATING_BUTTON)
            .setTrackingUserStepsState(Feature.State.ENABLED)
            .setReproConfigurations(
                ReproConfigurations.Builder()
                .setIssueMode(IssueType.All, ReproMode.EnableWithScreenshots)
                .build())
            .setAutoMaskScreenshotsTypes(MaskingType.LABELS, MaskingType.MEDIA, MaskingType.TEXT_INPUTS)
            .build()
        Instabug.setWelcomeMessageState(WelcomeMessage.State.BETA)
    } else {
        Instabug.Builder(application, EnvKey.get("INSTABUG_TOKEN_PRO"))
            .setTrackingUserStepsState(Feature.State.DISABLED)
            .setReproConfigurations(
                ReproConfigurations.Builder()
                    .setIssueMode(IssueType.All, ReproMode.Disable)
                    .build())
            .setAutoMaskScreenshotsTypes(MaskingType.LABELS, MaskingType.MEDIA, MaskingType.TEXT_INPUTS)
            .build()
        Instabug.setWelcomeMessageState(WelcomeMessage.State.DISABLED)
    }
}