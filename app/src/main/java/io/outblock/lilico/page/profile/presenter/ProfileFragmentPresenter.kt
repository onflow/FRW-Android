package io.outblock.lilico.page.profile.presenter

import androidx.lifecycle.ViewModelProvider
import androidx.transition.TransitionManager
import com.instabug.library.Instabug
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.databinding.FragmentProfileBinding
import io.outblock.lilico.manager.app.isTestnet
import io.outblock.lilico.manager.config.AppConfig
import io.outblock.lilico.manager.walletconnect.WalletConnect
import io.outblock.lilico.network.model.UserInfoData
import io.outblock.lilico.page.address.AddressBookActivity
import io.outblock.lilico.page.backup.WalletBackupActivity
import io.outblock.lilico.page.dialog.accounts.AccountSwitchDialog
import io.outblock.lilico.page.inbox.InboxActivity
import io.outblock.lilico.page.main.HomeTab
import io.outblock.lilico.page.main.MainActivityViewModel
import io.outblock.lilico.page.profile.ProfileFragment
import io.outblock.lilico.page.profile.model.ProfileFragmentModel
import io.outblock.lilico.page.profile.subpage.about.AboutActivity
import io.outblock.lilico.page.profile.subpage.accountsetting.AccountSettingActivity
import io.outblock.lilico.page.profile.subpage.avatar.ViewAvatarActivity
import io.outblock.lilico.page.profile.subpage.claimdomain.MeowDomainClaimedStateChangeListener
import io.outblock.lilico.page.profile.subpage.claimdomain.observeMeowDomainClaimedStateChange
import io.outblock.lilico.page.profile.subpage.currency.CurrencyListActivity
import io.outblock.lilico.page.profile.subpage.currency.model.findCurrencyFromFlag
import io.outblock.lilico.page.profile.subpage.developer.DeveloperModeActivity
import io.outblock.lilico.page.profile.subpage.theme.ThemeSettingActivity
import io.outblock.lilico.page.profile.subpage.wallet.WalletSettingActivity
import io.outblock.lilico.page.profile.subpage.wallet.account.ChildAccountsActivity
import io.outblock.lilico.page.profile.subpage.wallet.device.DevicesActivity
import io.outblock.lilico.page.profile.subpage.walletconnect.session.WalletConnectSessionActivity
import io.outblock.lilico.page.security.SecuritySettingActivity
import io.outblock.lilico.utils.extensions.isVisible
import io.outblock.lilico.utils.extensions.openInSystemBrowser
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.extensions.setVisible
import io.outblock.lilico.utils.getCurrencyFlag
import io.outblock.lilico.utils.getNotificationSettingIntent
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.isMeowDomainClaimed
import io.outblock.lilico.utils.isNightMode
import io.outblock.lilico.utils.isNotificationPermissionGrand
import io.outblock.lilico.utils.isRegistered
import io.outblock.lilico.utils.loadAvatar
import io.outblock.lilico.utils.uiScope

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

        binding.group4.bugReport.setOnClickListener { Instabug.show() }
        binding.group5.aboutPreference.setOnClickListener { AboutActivity.launch(context) }
        binding.group6.switchAccountPreference.setOnClickListener {
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
            val isSignIn = isRegistered()
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