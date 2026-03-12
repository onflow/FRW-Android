package com.flowfoundation.wallet.page.main.presenter

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.clearWebViewCache
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.dialog.profile.ProfileSwitchDialog
import com.flowfoundation.wallet.page.evm.EnableEVMActivity
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.main.drawer.DrawerLayoutViewModel
import com.flowfoundation.wallet.page.main.model.WalletAccountData
import com.flowfoundation.wallet.page.main.widget.CopyCOAAddressDialog
import com.flowfoundation.wallet.page.restore.WalletRestoreActivity
import com.flowfoundation.wallet.page.wallet.view.LinkedAccountSection
import com.flowfoundation.wallet.page.wallet.view.WalletAccountSection
import com.flowfoundation.wallet.reactnative.ReactNativeActivity
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.clearCacheDir
import com.flowfoundation.wallet.utils.getActivityFromContext
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.parseAvatarUrl
import com.flowfoundation.wallet.utils.setMeowDomainClaimed
import com.flowfoundation.wallet.utils.shortenEVMString
import com.flowfoundation.wallet.utils.svgToPng
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.FlowLoadingDialog
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog
import kotlinx.coroutines.delay

@Composable
fun DrawerLayoutCompose(drawer: DrawerLayout) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as FragmentActivity }
    val viewModel = remember { ViewModelProvider(activity)[DrawerLayoutViewModel::class.java] }

    val userInfo by viewModel.userInfo.collectAsStateWithLifecycle()
    val showEvmLayout by viewModel.showEvmLayout.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()
    val isAddingAccount by viewModel.isAddingAccount.collectAsStateWithLifecycle()
    val canAddAccount by viewModel.canAddAccount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    DisposableEffect(drawer) {
        val listener = object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) {
                viewModel.loadData(refreshBalance = true)
            }
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerStateChanged(newState: Int) {}
        }

        drawer.addDrawerListener(listener)
        onDispose {
            drawer.removeDrawerListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(colorResource(id = R.color.deep_bg))
            .padding(horizontal = 18.dp, vertical = 24.dp)
    ) {
        HeaderSection(
            userInfo = userInfo,
            onAccountSwitchClick = { ProfileSwitchDialog.show(activity.supportFragmentManager) }
        )
        HorizontalDivider(
            color = colorResource(id = R.color.border_line_stroke),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
        )

        if (showEvmLayout) {
            Spacer(modifier = Modifier.height(24.dp))
            EVMSection(
                onEvmClick = {
                    if (EVMWalletManager.haveEVMAddress()) {
                        drawer.close()
                    } else {
                        EnableEVMActivity.launch(activity)
                    }
                }
            )
        }

        AccountListSection(
            accounts = accounts,
            balanceMap = balanceMap,
            isAddingAccount = isAddingAccount,
            onCopyClick = onCopyClick@{ address ->
                if (EVMWalletManager.isEVMWalletAddress(address)) {
                    CopyCOAAddressDialog(context, address).show()
                    return@onCopyClick
                }
                textToClipboard(address)
                toast(msgRes = R.string.copy_address_toast)
            },
            onAccountClick = { address ->
                FlowLoadingDialog(context).show()
                WalletManager.selectWalletAddress(address)
                ioScope {
                    delay(200)
                    doNetworkChangeTask()
                    clearCacheDir()
                    clearWebViewCache()
                    setMeowDomainClaimed(false)
                    NftCollectionStateManager.clear()
                    TransactionStateManager.reload()
                    FungibleTokenListManager.clear()
                    StakingManager.clear()
                    CryptoProviderManager.clear()
                    delay(1000)
                    uiScope {
                        MainActivity.relaunch(Env.getApp())
                    }
                }
            },
            modifier = Modifier.weight(1f)
        )

        HorizontalDivider(
            color = colorResource(id = R.color.border_line_stroke),
            modifier = Modifier.fillMaxWidth()
        )
        BottomSection(
            onImportWalletClick = {
                WalletRestoreActivity.launch(activity)
            },
            onAddAccountClick = { viewModel.addAccount() },
            canAddAccount = canAddAccount
        )
    }
}

@Composable
fun HeaderSection(
    userInfo: UserInfoData?,
    onAccountSwitchClick: () -> Unit
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
    ) {
        val (icon, name, switch) = createRefs()

        if (userInfo == null) {
            ShimmerBox(
                modifier = Modifier
                    .constrainAs(icon) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                    }
                    .size(40.dp),
                shape = RoundedCornerShape(8.dp)
            )
            ShimmerBox(
                modifier = Modifier
                    .constrainAs(name) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(icon.end, 16.dp)
                        end.linkTo(switch.start, 16.dp)
                        width = Dimension.fillToConstraints
                    }
                    .height(16.dp)
                    .width(120.dp)
            )
        } else {
            val avatarUrl = userInfo.avatar.parseAvatarUrl()
            val avatar = if (avatarUrl.contains("flovatar.com")) {
                avatarUrl.svgToPng()
            } else {
                avatarUrl
            }
            AsyncImage(
                model = avatar,
                contentDescription = "User Avatar",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_placeholder),
                error = painterResource(id = R.drawable.ic_placeholder),
                modifier = Modifier
                    .constrainAs(icon) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(name.start)
                    }
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Text(
                text = userInfo.nickname,
                color = colorResource(id = R.color.text_1),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .constrainAs(name) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(icon.end, 16.dp)
                        end.linkTo(switch.start, 16.dp)
                        width = Dimension.fillToConstraints
                    }
            )
        }

        IconButton(
            onClick = onAccountSwitchClick,
            modifier = Modifier
                .constrainAs(switch) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end, (-12).dp)
                }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_switch_profile),
                contentDescription = "Switch Profile",
                tint = colorResource(id = R.color.icon)
            )
        }
    }
}

@Composable
fun EVMSection(onEvmClick: () -> Unit) {
    ConstraintLayout(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onEvmClick)
        .background(
            color = colorResource(id = R.color.bg_card),
            shape = RoundedCornerShape(16.dp)
        )
        .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        val (title, desc, icon) = createRefs()

        val evm = stringResource(R.string.label_evm)
        val raw = stringResource(R.string.add_evm_account, evm)
        val parts = raw.split(evm)

        val inlineContentId = "evmLabel"

        val annotatedText = buildAnnotatedString {
            append(parts[0])
            appendInlineContent(inlineContentId, "[EVM]")
            if (parts.size > 1) {
                append(parts[1])
            }
        }

        val inlineContent = mapOf(
            inlineContentId to InlineTextContent(
                placeholder = Placeholder(
                    width = 62.sp,
                    height = 16.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ConstraintLayout(
                        modifier = Modifier
                            .background(
                                color = colorResource(R.color.evm),
                                shape = RoundedCornerShape(16.dp)
                            )
                        ) {
                            val (evmLabel, flowLabel) = createRefs()
                            Text(
                                text = stringResource(R.string.label_evm),
                                color = colorResource(id = R.color.white),
                                fontSize = 8.sp,
                                modifier = Modifier
                                    .constrainAs(evmLabel) {
                                      top.linkTo(parent.top)
                                      bottom.linkTo(parent.bottom)
                                      start.linkTo(parent.start, margin = 4.dp)
                                      end.linkTo(flowLabel.start, margin = 2.dp)
                                    }
                            )
                            Box(
                              modifier = Modifier
                                .constrainAs(flowLabel) {
                                  top.linkTo(parent.top)
                                  bottom.linkTo(parent.bottom)
                                  start.linkTo(evmLabel.end, margin = 2.dp)
                                  end.linkTo(parent.end)
                                }
                                .background(
                                  color = colorResource(R.color.evm_on_flow_end_color),
                                  shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                              Text(
                                text = stringResource(R.string.label_flow),
                                color = colorResource(id = R.color.black),
                                fontSize = 8.sp
                              )
                            }
                    }
                }
            }
        )

        Text(
            text = annotatedText,
            inlineContent = inlineContent,
            color = colorResource(id = R.color.text_1),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .constrainAs(title) {
                    top.linkTo(parent.top)
                    bottom.linkTo(desc.top, 4.dp)
                    start.linkTo(parent.start)
                    end.linkTo(icon.start, 16.dp)
                    width = Dimension.fillToConstraints
                }
        )

        Text(
            text = stringResource(R.string.enable_evm_desc),
            color = colorResource(id = R.color.text_2),
            fontSize = 12.sp,
            modifier = Modifier
                .constrainAs(desc) {
                    top.linkTo(title.bottom)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(title.start)
                    end.linkTo(title.end)
                }
        )

        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_right),
            contentDescription = "Arrow Right",
            tint = colorResource(id = R.color.icon),
            modifier = Modifier
                .constrainAs(icon) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end)
                }
        )
    }
}

@Composable
fun ActiveAccountSection(
    item: WalletAccountData,
    balanceMap: Map<String, String>,
    onCopyClick: (String) -> Unit,
) {
    val activeLinkedAccount = item.linkedAccounts.firstOrNull { it.isSelected }
    val activeAddress = if (item.isSelected) {
        item.address
    } else {
        activeLinkedAccount?.address ?: ""
    }
    val balance = balanceMap[activeAddress] ?: ""
    val activeEmojiId = if (item.isSelected) {
        item.emojiId
    } else {
        activeLinkedAccount?.emojiId?: Emoji.EMPTY.id
    }
    val activeName = if (item.isSelected) {
        item.name
    } else {
        activeLinkedAccount?.name?: ""
    }
    val activeIcon = if (item.isSelected) {
        null
    } else {
        activeLinkedAccount?.icon
    }
    val walletEmojiId = item.emojiId
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colorResource(id = R.color.bg_card),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(10.dp)
    ) {
        val (icon, walletIcon, name, address, balanceText, copy) = createRefs()
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(
                    width = 1.dp,
                    color = colorResource(
                        if (item.isSelected) R.color.colorSecondary else R.color.transparent
                    ),
                    shape = CircleShape
                )
                .constrainAs(icon) {
                    start.linkTo(parent.start, 7.dp)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                },
            contentAlignment = Alignment.Center
        ) {
            if (activeIcon.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = Color(Emoji.getEmojiColorRes(activeEmojiId)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Emoji.getEmojiById(activeEmojiId),
                        fontSize = 18.sp
                    )
                }
            } else {
                AsyncImage(
                    model = activeIcon,
                    contentDescription = "Account Icon",
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = Color.Transparent,
                            shape = CircleShape
                        )
                )
            }
        }

        if (activeLinkedAccount != null) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(
                        width = 1.dp,
                        color = colorResource(R.color.bg_card),
                        shape = CircleShape
                    )
                    .constrainAs(walletIcon) {
                        start.linkTo(parent.start)
                        top.linkTo(icon.top)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            color = Color(Emoji.getEmojiColorRes(walletEmojiId)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Emoji.getEmojiById(walletEmojiId),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .constrainAs(name) {
                    top.linkTo(parent.top)
                    bottom.linkTo(address.top)
                    start.linkTo(icon.end, 9.dp)
                    end.linkTo(copy.start, 12.dp)
                    width = Dimension.fillToConstraints
                }
        ) {
            if (activeLinkedAccount != null) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_link),
                    contentDescription = "LinkedAccount",
                    tint = colorResource(id = R.color.icon),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = activeName,
                color = colorResource(id = R.color.text_1),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f, fill = false)
            )

            if (activeLinkedAccount != null && activeLinkedAccount.isCOAAccount) {
                Spacer(modifier = Modifier.width(4.dp))
                ConstraintLayout(
                    modifier = Modifier
                        .background(
                            color = colorResource(R.color.evm),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    val (evmLabel, flowLabel) = createRefs()
                    Text(
                        text = stringResource(R.string.label_evm),
                        color = colorResource(id = R.color.white),
                        fontSize = 8.sp,
                        modifier = Modifier.constrainAs(evmLabel) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start, margin = 4.dp)
                            end.linkTo(flowLabel.start)
                        }
                    )
                    Box(
                        modifier = Modifier
                            .constrainAs(flowLabel) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                start.linkTo(evmLabel.end, margin = 4.dp)
                                end.linkTo(parent.end)
                            }
                            .background(
                                color = colorResource(R.color.evm_on_flow_end_color),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_flow),
                            color = colorResource(id = R.color.black),
                            fontSize = 8.sp
                        )
                    }
                }
            }
            else if (item.isEOAAccount) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = colorResource(R.color.evm),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_evm),
                        color = colorResource(id = R.color.white),
                        fontSize = 8.sp
                    )
                }
            }
        }

        Text(
            text = shortenEVMString(activeAddress),
            color = colorResource(id = R.color.text_2),
            fontSize = 12.sp,
            modifier = Modifier
                .constrainAs(address) {
                    top.linkTo(name.bottom, 2.dp)
                    bottom.linkTo(balanceText.top, 2.dp)
                    start.linkTo(name.start)
                    end.linkTo(name.end)
                    width = Dimension.fillToConstraints
                }
        )
        Text(
            text = balance,
            color = colorResource(id = R.color.text_2),
            fontSize = 12.sp,
            modifier = Modifier
                .constrainAs(balanceText) {
                    top.linkTo(address.bottom)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(name.start)
                    end.linkTo(name.end)
                    width = Dimension.fillToConstraints
                }
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_copy_address),
            contentDescription = "Copy",
            tint = colorResource(id = R.color.icon),
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = { onCopyClick(activeAddress) })
                .constrainAs(copy) {
                    end.linkTo(parent.end)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                }
        )
    }
}

@Composable
fun AccountListSection(
    accounts: List<WalletAccountData>,
    balanceMap: Map<String, String>,
    onCopyClick: (String) -> Unit,
    onAccountClick: (String) -> Unit,
    isAddingAccount: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val activeAccount = accounts.firstOrNull { account -> account.isSelected || account.linkedAccounts.any { it.isSelected } }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = stringResource(id = R.string.active_account),
            color = colorResource(id = R.color.text_2),
            fontSize = 14.sp
        )
        if (activeAccount != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ActiveAccountSection(
                item = activeAccount,
                balanceMap = balanceMap,
                onCopyClick = onCopyClick
            )
        } else if (accounts.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            ShimmerWalletAccountRow()
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.other_accounts),
            color = colorResource(id = R.color.text_2),
            fontSize = 14.sp,
        )

        if (isAddingAccount) {
            LoadingWalletAccountRow()
        }

        if (accounts.isEmpty()) {
            repeat(2) {
                ShimmerWalletAccountRow()
            }
        } else {
            accounts.forEach { account ->
                WalletAccountSection(
                    item = account,
                    balance = balanceMap[account.address.toAddress()] ?: "",
                    onCopyClick = onCopyClick,
                    onAccountClick = { onAccountClick(account.address) }
                )
                account.linkedAccounts.forEach { linkedAccount ->
                    LinkedAccountSection(
                        item = linkedAccount,
                        balance = balanceMap[linkedAccount.address.toAddress()] ?: "",
                        onCopyClick = onCopyClick,
                        onAccountClick = { onAccountClick(linkedAccount.address) }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomSection(
    onImportWalletClick: () -> Unit,
    onAddAccountClick: () -> Unit = {},
    canAddAccount: Boolean = false
) {
    Column {
        if (canAddAccount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable(onClick = onAddAccountClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = colorResource(id = R.color.bg_card),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_add_24),
                        contentDescription = "Add Account",
                        tint = colorResource(id = R.color.text_1)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(id = R.string.add_account),
                    color = colorResource(id = R.color.text_2),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clickable(onClick = onImportWalletClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = colorResource(id = R.color.bg_card),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_import_wallet),
                    contentDescription = "Recover Profile",
                    tint = colorResource(id = R.color.text_1)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(id = R.string.recover_profile),
                color = colorResource(id = R.color.text_2),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

    }
}

@Composable
private fun LoadingWalletAccountRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loading_rotation"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier.size(45.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { 0.25f },
                modifier = Modifier.size(45.dp).rotate(rotation),
                color = colorResource(id = R.color.accent_green),
                trackColor = colorResource(id = R.color.accent_green_8),
                strokeWidth = 7.dp
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_coin_flow),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = Color.Unspecified
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column {
            ShimmerBox(modifier = Modifier.width(60.dp).height(14.dp))
            Spacer(modifier = Modifier.height(2.dp))
            ShimmerBox(modifier = Modifier.width(100.dp).height(12.dp))
            Spacer(modifier = Modifier.height(2.dp))
            ShimmerBox(modifier = Modifier.width(80.dp).height(12.dp))
        }
    }
}

@Composable
private fun ShimmerBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    val alpha by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(colorResource(id = R.color.bg_card), shape)
    )
}

@Composable
private fun ShimmerWalletAccountRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        ShimmerBox(
            modifier = Modifier.size(42.dp),
            shape = CircleShape
        )
        Spacer(modifier = Modifier.width(9.dp))
        Column {
            ShimmerBox(modifier = Modifier.width(60.dp).height(14.dp))
            Spacer(modifier = Modifier.height(2.dp))
            ShimmerBox(modifier = Modifier.width(100.dp).height(12.dp))
            Spacer(modifier = Modifier.height(2.dp))
            ShimmerBox(modifier = Modifier.width(80.dp).height(12.dp))
        }
    }
}

fun setupDrawerLayoutCompose(drawer: DrawerLayout) {
    val composeView = ComposeView(drawer.context)
    composeView.setContent {
        DrawerLayoutCompose(drawer)
    }

    val layoutParams = DrawerLayout.LayoutParams(
        (ScreenUtils.getScreenWidth() * 0.8f).toInt(),
        DrawerLayout.LayoutParams.MATCH_PARENT
    ).apply {
        gravity = GravityCompat.START
    }
    composeView.layoutParams = layoutParams

    drawer.addView(composeView)
}
