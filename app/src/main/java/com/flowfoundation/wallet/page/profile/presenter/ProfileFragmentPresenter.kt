package com.flowfoundation.wallet.page.profile.presenter

import androidx.lifecycle.ViewModelProvider
import androidx.transition.TransitionManager
import com.instabug.library.Instabug
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentProfileBinding
import com.flowfoundation.wallet.firebase.auth.isUserSignIn
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.address.AddressBookActivity
import com.flowfoundation.wallet.page.backup.WalletBackupActivity
import com.flowfoundation.wallet.page.dialog.accounts.AccountSwitchDialog
import com.flowfoundation.wallet.page.inbox.InboxActivity
import com.flowfoundation.wallet.page.main.HomeTab
import com.flowfoundation.wallet.page.main.MainActivityViewModel
import com.flowfoundation.wallet.page.profile.ProfileFragment
import com.flowfoundation.wallet.page.profile.model.ProfileFragmentModel
import com.flowfoundation.wallet.page.profile.subpage.about.AboutActivity
import com.flowfoundation.wallet.page.profile.subpage.accountsetting.AccountSettingActivity
import com.flowfoundation.wallet.page.profile.subpage.avatar.ViewAvatarActivity
import com.flowfoundation.wallet.page.profile.subpage.claimdomain.MeowDomainClaimedStateChangeListener
import com.flowfoundation.wallet.page.profile.subpage.claimdomain.observeMeowDomainClaimedStateChange
import com.flowfoundation.wallet.page.profile.subpage.currency.CurrencyListActivity
import com.flowfoundation.wallet.page.profile.subpage.currency.model.findCurrencyFromFlag
import com.flowfoundation.wallet.page.profile.subpage.developer.DeveloperModeActivity
import com.flowfoundation.wallet.page.profile.subpage.theme.ThemeSettingActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.WalletSettingActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.account.ChildAccountsActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.DevicesActivity
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.WalletConnectSessionActivity
import com.flowfoundation.wallet.page.security.SecuritySettingActivity
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.openInSystemBrowser
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.getCurrencyFlag
import com.flowfoundation.wallet.utils.getNotificationSettingIntent
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isMeowDomainClaimed
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.isNotificationPermissionGrand
import com.flowfoundation.wallet.utils.isRegistered
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.uiScope

class ProfileFragmentPresenter(
    private val fragment: ProfileFragment,
    private val binding: FragmentProfileBinding,
) : BasePresenter<ProfileFragmentModel>, MeowDomainClaimedStateChangeListener {

    private val context = fragment.requireContext()
    private var userInfo: UserInfoData? = null

    init {
        binding.root.addStatusBarTopPadding()
        binding.userInfo.editButton.setOnClickListener {
            userInfo?.let { AccountSettingActivity.launch(fragment.requireContext(), it) }
        }
        binding.userInfo.nicknameView.setOnClickListener {
            AccountSwitchDialog.show(fragment.childFragmentManager)
        }
        binding.notLoggedIn.root.setOnClickListener {
            ViewModelProvider(fragment.requireActivity())[MainActivityViewModel::class.java].changeTab(
                HomeTab.WALLET
            )
        }
        binding.actionGroup.addressButton.setOnClickListener { AddressBookActivity.launch(context) }
        binding.actionGroup.walletButton.setOnClickListener { WalletSettingActivity.launch(context) }
        binding.actionGroup.inboxButton.setOnClickListener { InboxActivity.launch(context) }

        binding.group0.backupPreference.setOnClickListener { WalletBackupActivity.launch(context) }
        binding.group0.securityPreference.setOnClickListener {
            SecuritySettingActivity.launch(context)
        }
        binding.group0.linkedAccount.setOnClickListener {
            ChildAccountsActivity.launch(context)
        }
        binding.group0.developerModePreference.setOnClickListener {
            DeveloperModeActivity.launch(context)
        }

        binding.group1.walletConnectPreference.setOnClickListener {
            WalletConnectSessionActivity.launch(context)
        }
        binding.group1.selfDevices.setOnClickListener {
            DevicesActivity.launch(context)
        }

        binding.group2.currencyPreference.setOnClickListener { CurrencyListActivity.launch(context) }
        binding.group2.themePreference.setOnClickListener { ThemeSettingActivity.launch(context) }
        binding.group2.notificationPreference.setOnClickListener {
            context.startActivity(getNotificationSettingIntent(context))
        }

        binding.group3.chromeExtension.setOnClickListener {
            "https://chrome.google.com/webstore/detail/lilico/hpclkefagolihohboafpheddmmgdffjm".openInSystemBrowser(
                context,
                ignoreInAppBrowser = true
            )
        }

        binding.group3.bugReport.setOnClickListener { Instabug.show() }
        binding.group3.aboutPreference.setOnClickListener { AboutActivity.launch(context) }
        binding.group4.switchAccountPreference.setOnClickListener {
            AccountSwitchDialog.show(fragment.childFragmentManager)
        }

        updatePreferenceState()
        updateClaimDomainState()
        observeMeowDomainClaimedStateChange(this)
    }

    override fun bind(model: ProfileFragmentModel) {
        model.userInfo?.let { bindUserInfo(it) }
        model.onResume?.let { updatePreferenceState() }
        model.inboxCount?.let { updateInboxCount(it) }
        updateNotificationPermissionStatus()
    }

    override fun onDomainClaimedStateChange(isClaimed: Boolean) {
        updateClaimDomainState()
    }

    private fun bindUserInfo(userInfo: UserInfoData) {
        val isAvatarChange = this.userInfo?.avatar != userInfo.avatar
        this.userInfo = userInfo
        with(binding.userInfo) {
            if (isAvatarChange) avatarView.loadAvatar(userInfo.avatar)
            useridView.text = userInfo.username
            nicknameView.text = userInfo.nickname

            avatarView.setOnClickListener { ViewAvatarActivity.launch(context, userInfo) }
        }
    }

    private fun updateNotificationPermissionStatus() {
        binding.group2.notificationPreference.setDesc(
            if (isNotificationPermissionGrand(context)) {
                R.string.on.res2String()
            } else {
                R.string.off.res2String()
            }
        )
    }

    private fun updatePreferenceState() {
        ioScope {
            val isSignIn = isRegistered() && isUserSignIn()
            uiScope {
                with(binding) {
                    userInfo.root.setVisible(isSignIn)
                    notLoggedIn.root.setVisible(!isSignIn)
                    actionGroup.root.setVisible(isSignIn)
                    group0.linkedAccount.setVisible(isSignIn)
                    group0.backupPreference.setVisible(isSignIn)
                    group0.securityPreference.setVisible(isSignIn)
                    group1.root.setVisible(isSignIn && AppConfig.walletConnectEnable())
                    group2.themePreference.setDesc(if (isNightMode(fragment.requireActivity())) R.string.dark.res2String() else R.string.light.res2String())
                    group2.currencyPreference.setDesc(findCurrencyFromFlag(getCurrencyFlag()).name)
                    group0.developerModePreference.setDesc(
                        (if (isTestnet()) R.string.testnet
                        else R.string.mainnet).res2String()
                    )
                }
                updateWalletConnectSessionCount()
            }
        }
    }

    private fun updateClaimDomainState() {
        ioScope {
            val isClaimedDomain = isMeowDomainClaimed()
            val isVisibleChange = binding.actionGroup.inboxButton.isVisible() != isClaimedDomain
            if (isVisibleChange) {
                uiScope {
                    TransitionManager.beginDelayedTransition(binding.actionGroup.root)
                    binding.actionGroup.inboxButton.setVisible(isClaimedDomain)
                }
            }
        }
    }

    private fun updateInboxCount(count: Int) {
        binding.actionGroup.inboxUnreadCount.setVisible(count != 0)
        binding.actionGroup.inboxUnreadCount.text = count.toString()
    }

    private fun updateWalletConnectSessionCount() {
        ioScope {
            if (WalletConnect.isInitialized().not()) {
                return@ioScope
            }
            val count = WalletConnect.get().sessionCount()
            uiScope {
                binding.group1.walletConnectPreference.setMarkText(if (count == 0) "" else "$count")
            }
        }
    }
}