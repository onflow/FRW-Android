package com.flowfoundation.wallet.page.profile.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.firebase.auth.isUserSignIn
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.page.address.AddressBookActivity
import com.flowfoundation.wallet.page.backup.WalletBackupActivity
import com.flowfoundation.wallet.page.dialog.profile.ProfileSwitchDialog
import com.flowfoundation.wallet.page.main.HomeTab
import com.flowfoundation.wallet.page.main.MainActivityViewModel
import com.flowfoundation.wallet.page.profile.ProfileFragmentViewModel
import com.flowfoundation.wallet.page.profile.subpage.about.AboutActivity
import com.flowfoundation.wallet.page.profile.subpage.accountsetting.AccountSettingActivity
import com.flowfoundation.wallet.page.profile.subpage.avatar.ViewAvatarActivity
import com.flowfoundation.wallet.page.profile.subpage.currency.CurrencyListActivity
import com.flowfoundation.wallet.page.profile.subpage.currency.model.findCurrencyFromFlag
import com.flowfoundation.wallet.page.profile.subpage.developer.DeveloperModeActivity
import com.flowfoundation.wallet.page.profile.subpage.theme.ThemeSettingActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.DevicesActivity
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.WalletConnectSessionActivity
import com.flowfoundation.wallet.page.account.AccountListActivity
import com.flowfoundation.wallet.page.security.SecuritySettingActivity
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.openInSystemBrowser
import com.instabug.library.Instabug
import kotlinx.coroutines.launch

@Composable
fun SettingScreen(
    fragment: Fragment,
    viewModel: ProfileFragmentViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    // Observe user info
    val userInfo by viewModel.profileLiveData.observeAsState()

    // State variables
    var isSignedIn by remember { mutableStateOf(false) }
    var freeGasEnabled by remember { mutableStateOf(false) }
    var themeDesc by remember { mutableStateOf("") }
    var currencyDesc by remember { mutableStateOf("") }
    var notificationDesc by remember { mutableStateOf("") }
    var developerModeDesc by remember { mutableStateOf("") }
    var walletConnectSessionCount by remember { mutableIntStateOf(0) }

    // Load settings on first composition and when resumed
    LaunchedEffect(Unit) {
        scope.launch {
            isSignedIn = isRegistered() && isUserSignIn()
            freeGasEnabled = isFreeGasPreferenceEnable()
            themeDesc = if (isNightMode(fragment.activity)) "Dark" else "Light"
            currencyDesc = findCurrencyFromFlag(getCurrencyFlag()).name
            notificationDesc = if (isNotificationPermissionGrand(context)) "On" else "Off"
            developerModeDesc = if (isTestnet()) "Testnet" else "Mainnet"

            if (WalletConnect.isInitialized()) {
                walletConnectSessionCount = WalletConnect.get().sessionCount()
            }
        }
    }

    LazyColumn(
        state = scrollState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 48.dp), // Add top padding instead of statusBarsPadding
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Title Header
        item {
            Text(
                text = "Settings",
                modifier = Modifier
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.text_1)
            )
        }

        // User Profile Header
        item {
            UserProfileHeader(
                nickname = userInfo?.nickname ?: "Guest",
                avatar = userInfo?.avatar ?: "",
                isSignedIn = isSignedIn,
                onAvatarClick = {
                    userInfo?.let { ViewAvatarActivity.launch(context, it) }
                },
                onEditClick = {
                    userInfo?.let { AccountSettingActivity.launch(context, it) }
                },
                onNicknameClick = {
                    if (isSignedIn) {
                        ProfileSwitchDialog.show(fragment.childFragmentManager)
                    } else {
                        ViewModelProvider(fragment.requireActivity())[MainActivityViewModel::class.java].changeTab(HomeTab.WALLET)
                    }
                }
            )
        }

        // Action Buttons (Address Book + Account List)
        if (isSignedIn) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colorResource(R.color.bg_card)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionButtonContent(
                            iconRes = R.drawable.ic_address,
                            titleRes = R.string.address_book,
                            modifier = Modifier.weight(1f)
                        ) {
                            AddressBookActivity.launch(context)
                        }

                        ActionButtonContent(
                            iconRes = R.drawable.ic_settings_wallet,
                            titleRes = R.string.account_list,
                            modifier = Modifier.weight(1f)
                        ) {
                            AccountListActivity.launch(context)
                        }
                    }
                }
            }
        }

        // First Group - Backup & Security
        if (isSignedIn) {
            item {
                SettingSection {
                    val showBackup = !WalletManager.isChildAccountSelected()

                    if (showBackup) {
                        SettingItem(
                            iconRes = R.drawable.ic_settings_backup,
                            titleRes = R.string.backup,
                            showDivider = true
                        ) {
                            WalletBackupActivity.launch(context)
                        }
                    }

                    SettingItem(
                        iconRes = R.drawable.ic_settings_security,
                        titleRes = R.string.security,
                        showDivider = false
                    ) {
                        SecuritySettingActivity.launch(context)
                    }
                }
            }
        }

        // Second Group - Wallet Connect & Devices
        if (isSignedIn && AppConfig.walletConnectEnable()) {
            item {
                SettingSection {
                    SettingItem(
                        iconRes = R.drawable.ic_wallet_connect,
                        titleRes = R.string.wallet_connect,
                        description = if (walletConnectSessionCount > 0) walletConnectSessionCount.toString() else "",
                        showDivider = true
                    ) {
                        WalletConnectSessionActivity.launch(context)
                    }

                    SettingItem(
                        iconRes = R.drawable.ic_settings_devices,
                        titleRes = R.string.devices,
                        showDivider = false
                    ) {
                        DevicesActivity.launch(context)
                    }
                }
            }
        }

        // Third Group - Notification, Currency, Theme
        item {
            SettingSection {
                SettingItem(
                    iconRes = R.drawable.ic_settings_notification,
                    titleRes = R.string.notification,
                    description = notificationDesc,
                    showDivider = true
                ) {
                    context.startActivity(getNotificationSettingIntent(context))
                }

                SettingItem(
                    iconRes = R.drawable.ic_settings_currency,
                    titleRes = R.string.currency,
                    description = currencyDesc,
                    showDivider = true
                ) {
                    CurrencyListActivity.launch(context)
                }

                SettingItem(
                    iconRes = R.drawable.ic_settings_theme,
                    titleRes = R.string.appearance,
                    description = themeDesc,
                    showDivider = false
                ) {
                    ThemeSettingActivity.launch(context)
                }
            }
        }

        // Fourth Group - Bug Report, Developer Mode, Chrome Extension
        item {
            SettingSection {
                SettingItem(
                    iconRes = R.drawable.ic_settings_bug,
                    titleRes = R.string.bug_report,
                    showDivider = true
                ) {
                    Instabug.show()
                }

                if (isSignedIn) {
                    SettingItem(
                        iconRes = R.drawable.ic_settings_developer,
                        titleRes = R.string.developer_mode,
                        description = developerModeDesc,
                        showDivider = true
                    ) {
                        DeveloperModeActivity.launch(context)
                    }
                }

                SettingItem(
                    iconRes = R.drawable.ic_settings_extension,
                    titleRes = R.string.chrome_extension,
                    showDivider = false
                ) {
                    "https://chrome.google.com/webstore/detail/lilico/hpclkefagolihohboafpheddmmgdffjm".openInSystemBrowser(
                        context,
                        ignoreInAppBrowser = true
                    )
                }
            }
        }

        // Fifth Group - Free Gas Fee
        item {
            SettingSection {
                SettingSwitchItem(
                    titleRes = R.string.free_gas_fee,
                    isChecked = freeGasEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            setFreeGasPreferenceEnable(enabled)
                        }
                    },
                    showDivider = false
                )
            }

            // Description text below the setting section
            Text(
                text = stringResource(R.string.free_gas_fee_desc),
                fontSize = 12.sp,
                color = colorResource(R.color.text_2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }

        // Sixth Group - About
        item {
            SettingSection {
                SettingItem(
                    iconRes = R.drawable.ic_settings_about,
                    titleRes = R.string.about,
                    showDivider = false
                ) {
                    AboutActivity.launch(context)
                }
            }
        }

        // Seventh Group - Switch Account
        if (isSignedIn) {
            item {
                SettingSection {
                    SettingItem(
                        iconRes = R.drawable.ic_settings_profile,
                        titleRes = R.string.add_new_profile,
                        showDivider = false
                    ) {
                        ProfileSwitchDialog.show(fragment.childFragmentManager)
                    }
                }
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
private fun ActionButtonContent(
    iconRes: Int,
    titleRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = null,
            tint = colorResource(R.color.accent_green),
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(titleRes),
            fontSize = 14.sp,
            color = colorResource(R.color.text_1),
            textAlign = TextAlign.Center
        )
    }
}
