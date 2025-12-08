package com.flowfoundation.wallet.page.dialog.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.restore.WalletRestoreActivity
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_USERNAME
import com.flowfoundation.wallet.page.walletcreate.WalletCreateActivity
import com.flowfoundation.wallet.page.wallet.view.ProfileItemSection
import com.flowfoundation.wallet.reactnative.ReactNativeActivity
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.utils.getActivityFromContext
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.ProgressDialog
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ProfileSwitchDialog: BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ProfileSwitchContent(
                    onDismiss = { dismiss() }
                )
            }
        }
    }

    companion object {
        fun show(fragmentManager: FragmentManager) {
            logd("ProfileSwitchDialog", "show() called")
            ProfileSwitchDialog().showNow(fragmentManager, "ProfileSwitchDialog")
        }
    }
}

@Composable
private fun ProfileSwitchContent(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as FragmentActivity }
    val viewModel = remember { ViewModelProvider(activity)[ProfileSwitchViewModel::class.java] }

    val switchItemList by viewModel.switchItemList.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        logd("ProfileSwitchDialog", "Starting to load switch account list")
        viewModel.loadSwitchAccountList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colorResource(id = R.color.deep_bg),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(18.dp)
    ) {
        // Header
        Text(
            text = stringResource(R.string.profiles),
            color = colorResource(id = R.color.text_1),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        // Account List
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading...",
                    color = colorResource(id = R.color.text_2)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, false)
            ) {
                itemsIndexed(switchItemList) { index, switchItem ->
                    when (switchItem) {
                        is SwitchItemData.ProfileItem -> {
                            ProfileItemSection(
                                profile = switchItem.data.account,
                                isSelected = switchItem.data.account.isActive,
                                onProfileClick = { profileId ->
                                    handleAccountSwitch(context, switchItem.data.account, onDismiss)
                                },
                                avatarList = switchItem.data.avatarList,
                                balanceMap = switchItem.data.balanceMap
                            )
                        }
                        is SwitchItemData.LocalSwitchItem -> {
                            LocalSwitchAccountItem(
                                account = switchItem.account,
                                onClick = {
                                    handleLocalAccountSwitch(context, switchItem.account, onDismiss)
                                }
                            )
                        }
                    }

                    // Add divider between items (except for last item)
                    if (index < switchItemList.size - 1) {
                        HorizontalDivider(
                            color = colorResource(id = R.color.border_line_stroke),
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Bottom Actions
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colorResource(id = R.color.bg_card),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 18.dp)
        ) {
            // Create New Profile Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        logd("ProfileSwitchDialog", "Create new profile clicked - launching RN ProfileTypeSelectionScreen")
                        if (isTestnet()) {
                            SwitchNetworkDialog(context, DialogType.CREATE).show()
                        } else {
                            ReactNativeActivity.launchWithRoute(context, RNBridge.ScreenType.ONBOARDING, RNBridge.InitialRoute.PROFILE_TYPE_SELECTION)
                            onDismiss()
                        }
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add_profile),
                    contentDescription = null,
                    tint = colorResource(id = R.color.text_1),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Create a new profile",
                    color = colorResource(id = R.color.text_1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(
                color = colorResource(id = R.color.border_line_stroke),
                modifier = Modifier.fillMaxWidth()
            )

            // Recover Existing Profile Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        logd("ProfileSwitchDialog", "Recover existing profile clicked")
                        WalletRestoreActivity.launch(context)
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_recover_profile),
                    contentDescription = null,
                    tint = colorResource(id = R.color.text_1),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Recover an existing profile",
                    color = colorResource(id = R.color.text_1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LocalSwitchAccountItem(
    account: LocalSwitchAccount,
    onClick: () -> Unit
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colorResource(id = R.color.bg_card),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(18.dp)
            .clickable(onClick = onClick)
    ) {
        val (icon, name, address) = createRefs()

        // Placeholder icon
        Icon(
            painter = painterResource(id = R.drawable.ic_placeholder),
            contentDescription = "Account Icon",
            tint = colorResource(id = R.color.icon),
            modifier = Modifier
                .constrainAs(icon) {
                    top.linkTo(parent.top, 8.dp)
                    start.linkTo(parent.start)
                }
                .size(40.dp)
        )

        // Username
        Text(
            text = account.username,
            color = colorResource(id = R.color.text_1),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .constrainAs(name) {
                    top.linkTo(icon.top)
                    start.linkTo(icon.end, 12.dp)
                    end.linkTo(parent.end, 12.dp)
                    width = Dimension.fillToConstraints
                }
        )

        // Address
        Text(
            text = account.address,
            color = colorResource(id = R.color.text_2),
            fontSize = 12.sp,
            modifier = Modifier
                .constrainAs(address) {
                    top.linkTo(name.bottom, 4.dp)
                    start.linkTo(name.start)
                }
        )
    }
}

private fun handleAccountSwitch(context: Context, account: Account, onDismiss: () -> Unit) {
    if (isTestnet()) {
        SwitchNetworkDialog(context, DialogType.SWITCH).show()
    } else {
        val progressDialog = ProgressDialog(context)
        progressDialog.show()
        AccountManager.switch(account) {
            uiScope {
                progressDialog.dismiss()
                onDismiss()
            }
        }
    }
}

private fun handleLocalAccountSwitch(context: Context, account: LocalSwitchAccount, onDismiss: () -> Unit) {
    if (isTestnet()) {
        SwitchNetworkDialog(context, DialogType.SWITCH).show()
    } else {
        val progressDialog = ProgressDialog(context)
        progressDialog.show()
        AccountManager.switch(account) {
            uiScope {
                progressDialog.dismiss()
                onDismiss()
            }
        }
    }
}
