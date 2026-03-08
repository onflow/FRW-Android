package com.flowfoundation.wallet.instabug

import android.app.Application
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import ai.luciq.library.Feature
import ai.luciq.library.Luciq
import ai.luciq.library.IssueType
import ai.luciq.library.MaskingType
import ai.luciq.library.ReproConfigurations
import ai.luciq.library.ReproMode
import ai.luciq.library.invocation.LuciqInvocationEvent
import ai.luciq.library.ui.onboarding.WelcomeMessage
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.isTesting
import ai.luciq.bug.BugReporting
import ai.luciq.bug.ProactiveReportingConfigs


fun instabugInitialize(application: Application) {
    if (isTesting()) {
        return
    }
    if (isDev()) {
        Luciq.Builder(application, BuildConfig.INSTABUG_TOKEN_DEV)
            .setInvocationEvents(
                LuciqInvocationEvent.SCREENSHOT,
                LuciqInvocationEvent.SHAKE,
                LuciqInvocationEvent.FLOATING_BUTTON)
            .setTrackingUserStepsState(Feature.State.ENABLED)
            .setReproConfigurations(
                ReproConfigurations.Builder()
                .setIssueMode(IssueType.All, ReproMode.EnableWithScreenshots)
                .build())
            .setAutoMaskScreenshotsTypes(MaskingType.MASK_NOTHING)
            .build()
        Luciq.setWelcomeMessageState(WelcomeMessage.State.BETA)
    } else {
        Luciq.Builder(application, BuildConfig.INSTABUG_TOKEN_PROD)
            .setInvocationEvents(
                LuciqInvocationEvent.SCREENSHOT,
                LuciqInvocationEvent.SHAKE
            )
            .setTrackingUserStepsState(Feature.State.ENABLED)
            .setReproConfigurations(
                ReproConfigurations.Builder()
                    .setIssueMode(IssueType.All, ReproMode.EnableWithScreenshots)
                    .build())
            .setAutoMaskScreenshotsTypes(MaskingType.MASK_NOTHING)
            .build()
        Luciq.setWelcomeMessageState(WelcomeMessage.State.DISABLED)
    }
    Luciq.onReportSubmitHandler { report ->
        firebaseUid()?.let {
            report.setUserAttribute("uid", it)
        }
        report.setUserAttribute("username", AccountManager.userInfo()?.username.orEmpty())
        report.setUserAttribute("FlowAccount", WalletManager.getCurrentFlowWalletAddress().orEmpty())
        report.setUserAttribute("SelectedAccount", WalletManager.selectedWalletAddress())
        val childAccounts = WalletManager.childAccountList().map { it.address }
        if (childAccounts.isNotEmpty())
            report.setUserAttribute(
                "ChildAccounts",
                childAccounts.toString()
            )
        report.setUserAttribute("COA", EVMWalletManager.getEVMAddress().orEmpty())
        report.setUserAttribute("EOA", WalletManager.getEOAAddress().orEmpty())
        report.setUserAttribute("Network", chainNetWorkString())
    }
    val configuration = ProactiveReportingConfigs.Builder()
        .isEnabled(false) // Disable to prevent background ANRs
        .setGapBetweenModals(20) // Time in seconds
        .setModalDelayAfterDetection(5) // Time in seconds
        .build()
    BugReporting.setProactiveReportingConfigurations(configuration)
}
