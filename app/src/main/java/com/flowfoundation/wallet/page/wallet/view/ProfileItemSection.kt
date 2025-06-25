package com.flowfoundation.wallet.page.wallet.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.page.wallet.viewmodel.WalletAccountViewModel
import com.flowfoundation.wallet.utils.getActivityFromContext
import com.flowfoundation.wallet.utils.parseAvatarUrl
import com.flowfoundation.wallet.utils.svgToPng
import com.flowfoundation.wallet.wallet.toAddress


@Composable
fun ProfileItemSection(
    profile: Account,
    isSelected: Boolean,
    onProfileClick: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember { getActivityFromContext(context) as FragmentActivity }
    val viewModel = remember { ViewModelProvider(activity)[WalletAccountViewModel::class.java] }

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchWalletList(profile)
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(
                color = colorResource(id = R.color.bg_card),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 18.dp, vertical = 24.dp)
            .clickable(onClick = { onProfileClick(profile.wallet?.id ?: "" )})
    ) {
        val userInfo = profile.userInfo
        val avatarUrl = userInfo.avatar.parseAvatarUrl()
        val avatar = if (avatarUrl.contains("flovatar.com")) {
            avatarUrl.svgToPng()
        } else {
            avatarUrl
        }

        ConstraintLayout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp)
        ) {
            val (icon, name, switch) = createRefs()
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

            Icon(
                painter = painterResource(id = R.drawable.ic_check_round),
                contentDescription = "Select Profile",
                tint = colorResource(id = if (isSelected) R.color.icon else R.color.accent_green),
                modifier = Modifier
                    .constrainAs(switch) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        end.linkTo(parent.end)
                    }
            )
        }

        HorizontalDivider(
            color = colorResource(id = R.color.divider_25),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
        )

        accounts.forEach { account ->
            WalletAccountSection(
                item = account,
                balance = balanceMap[account.address.toAddress()] ?: ""
            )
            account.linkedAccounts.forEach { linkedAccount ->
                LinkedAccountSection(
                    item = linkedAccount,
                    balance = balanceMap[linkedAccount.address.toAddress()] ?: ""
                )
            }
            HorizontalDivider(
                color = colorResource(id = R.color.divider_25),
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }
}