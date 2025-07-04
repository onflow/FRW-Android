package com.flowfoundation.wallet.instabug

import android.app.Application
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
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
import com.instabug.bug.BugReporting
import com.instabug.bug.ProactiveReportingConfigs


fun instabugInitialize(application: Application) {
    if (isTesting()) {
        return
    }
    if (isDev()) {
        Instabug.Builder(application, BuildConfig.INSTABUG_TOKEN_DEV)
            .setInvocationEvents(
                InstabugInvocationEvent.SCREENSHOT,
                InstabugInvocationEvent.SHAKE,
                InstabugInvocationEvent.FLOATING_BUTTON)
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
            .setInvocationEvents(
                InstabugInvocationEvent.SCREENSHOT,
                InstabugInvocationEvent.SHAKE
            )
            .setTrackingUserStepsState(Feature.State.ENABLED)
            .setReproConfigurations(
                ReproConfigurations.Builder()
                    .setIssueMode(IssueType.All, ReproMode.EnableWithScreenshots)
                    .build())
            .setAutoMaskScreenshotsTypes(MaskingType.MASK_NOTHING)
            .build()
        Instabug.setWelcomeMessageState(WelcomeMessage.State.DISABLED)
    }
    Instabug.onReportSubmitHandler { report ->
        firebaseUid()?.let {
            report.setUserAttribute("uid", it)
        }
        report.setUserAttribute("username", AccountManager.userInfo()?.username.orEmpty())
        report.setUserAttribute("FlowAccount", WalletManager.wallet()?.walletAddress().orEmpty())
        report.setUserAttribute("SelectedAccount", WalletManager.selectedWalletAddress())
        val childAccounts = WalletManager.childAccountList()?.get()?.map { it.address } ?: emptyList()
        if (childAccounts.isNotEmpty())
            report.setUserAttribute(
                "ChildAccounts",
                childAccounts.toString()
            )
        report.setUserAttribute("COA", EVMWalletManager.getEVMAddress().orEmpty())
        report.setUserAttribute("Network", chainNetWorkString())
    }
    val configuration = ProactiveReportingConfigs.Builder()
        .isEnabled(false) // Disable to prevent background ANRs
        .setGapBetweenModals(20) // Time in seconds
        .setModalDelayAfterDetection(5) // Time in seconds
        .build()
    BugReporting.setProactiveReportingConfigurations(configuration)
}