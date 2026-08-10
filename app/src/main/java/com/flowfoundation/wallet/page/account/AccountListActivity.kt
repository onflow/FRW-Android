package com.flowfoundation.wallet.page.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.page.dialog.common.AddNewAccountDialog
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.wallet.view.AccountItemSection
import com.flowfoundation.wallet.page.profile.subpage.wallet.WalletSettingActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountdetail.ChildAccountDetailActivity
import com.flowfoundation.wallet.utils.getActivityFromContext
import com.flowfoundation.wallet.utils.isNightMode
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX

class AccountListActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background)
            .light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()

        setContent {
            AccountListScreen(
                onBackPressed = { finish() }
            )
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, AccountListActivity::class.java))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    onBackPressed: () -> Unit = {},
    viewModel: AccountListViewModel = viewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val balanceMap by viewModel.balanceMap.collectAsState()
    val hiddenAccounts by viewModel.hiddenAccounts.collectAsState()
    val isAddingAccount by viewModel.isAddingAccount.collectAsState()
    val canAddAccount by viewModel.canAddAccount.collectAsState()
    val canAddEOAAccount by viewModel.canAddEOAAccount.collectAsState()
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as FragmentActivity }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.account_list),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorResource(id = R.color.text),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colorResource(id = R.color.text)
                        )
                    }
                },
                actions = {
                    val canAdd = canAddAccount || canAddEOAAccount
                    IconButton(
                        onClick = {
                            AddNewAccountDialog.show(
                                activity.supportFragmentManager,
                                canAddCadence = canAddAccount,
                                canAddEOA = canAddEOAAccount,
                                onCadence = { viewModel.addCadenceAccount() },
                                onEOA = { viewModel.addEOAAccount() }
                            )
                        },
                        enabled = canAdd,
                        modifier = Modifier.alpha(if (canAdd) 1f else 0f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Account",
                            tint = colorResource(id = R.color.text)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorResource(id = R.color.background),
                    titleContentColor = colorResource(id = R.color.text),
                    navigationIconContentColor = colorResource(id = R.color.text),
                    actionIconContentColor = colorResource(id = R.color.text)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(id = R.color.background))
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(accounts) { account ->
                    AccountItemSection(
                        account = account,
                        balanceMap = balanceMap,
                        hiddenAccounts = hiddenAccounts,
                        onItemSelected = { address ->
                            if (WalletManager.isChildAccount(address)) {
                                val childAccount = WalletManager.childAccount(address)
                                childAccount?.let { model ->
                                    ChildAccountDetailActivity.launch(activity, model)
                                }
                            } else {
                                WalletSettingActivity.launch(activity, address)
                            }
                        },
                        onVisibilityToggle = { accountData ->
                            viewModel.toggleAccountVisibility(accountData.address)
                        }
                    )
                }
                if (isAddingAccount) {
                    item { LoadingAccountListRow() }
                }
            }
        }
    }
}

@Composable
private fun LoadingAccountListRow() {
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
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorResource(id = R.color.bg_card))
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorResource(id = R.color.bg_card))
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorResource(id = R.color.bg_card))
            )
        }
    }
}
