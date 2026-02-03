package com.flowfoundation.wallet.page.wallet.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil3.compose.AsyncImage
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.page.wallet.model.AvatarData
import com.flowfoundation.wallet.utils.parseAvatarUrl
import com.flowfoundation.wallet.utils.svgToPng

@Composable
fun ProfileItemSection(
    profile: Account,
    isSelected: Boolean,
    onProfileClick: (String) -> Unit,
    avatarList: List<AvatarData> = emptyList(),
    balanceMap: Map<String, String> = emptyMap()
) {

    val userInfo = profile.userInfo
    val avatarUrl = userInfo.avatar.parseAvatarUrl()
    val avatar = if (avatarUrl.contains("flovatar.com")) {
        avatarUrl.svgToPng()
    } else {
        avatarUrl
    }

    // Calculate total balance
    val totalBalance = balanceMap.values.sumOf { balance ->
        balance.replace(" FLOW", "").replace(",", "").toDoubleOrNull() ?: 0.0
    }

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .clickable(onClick = { onProfileClick(profile.wallet?.id ?: "" )})
    ) {
        val (icon, name, balance, accountCount, avatarRow, switch) = createRefs()

        // Avatar
        AsyncImage(
            model = avatar,
            contentDescription = "User Avatar",
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_placeholder),
            error = painterResource(id = R.drawable.ic_placeholder),
            modifier = Modifier
                .constrainAs(icon) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                }
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        // Name
        Text(
            text = userInfo.nickname,
            color = colorResource(id = R.color.text_1),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .constrainAs(name) {
                    top.linkTo(icon.top)
                    start.linkTo(icon.end, 12.dp)
                    end.linkTo(switch.start, 12.dp)
                    width = Dimension.fillToConstraints
                }
        )

        // Balance
        if (balanceMap.isNotEmpty()) {
            Text(
                text = String.format("%.2f FLOW", totalBalance),
                color = colorResource(id = R.color.text_2),
                fontSize = 12.sp,
                modifier = Modifier
                    .constrainAs(balance) {
                        top.linkTo(name.bottom, 4.dp)
                        start.linkTo(name.start)
                    }
            )
        }

        // Account count
        Text(
            text = "${avatarList.size} Accounts",
            color = colorResource(id = R.color.text_2),
            fontSize = 12.sp,
            modifier = Modifier
                .constrainAs(accountCount) {
                    if (balanceMap.isNotEmpty()) {
                        top.linkTo(balance.bottom, 4.dp)
                    } else {
                        top.linkTo(name.bottom, 4.dp)
                    }
                    start.linkTo(name.start)
                }
        )

        // Avatar LazyRow
        LazyRow(
            modifier = Modifier
                .constrainAs(avatarRow) {
                    top.linkTo(accountCount.top)
                    bottom.linkTo(accountCount.bottom)
                    start.linkTo(accountCount.end, 8.dp)
                    end.linkTo(switch.start, 12.dp)
                    width = Dimension.fillToConstraints
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(avatarList) { avatarData ->
                when (avatarData) {
                    is AvatarData.Icon -> {
                        AsyncImage(
                            model = avatarData.url,
                            contentDescription = "Account Icon",
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    color = Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
                    is AvatarData.Emoji -> {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    color = Color(Emoji.getEmojiColorRes(avatarData.emojiId)),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = Emoji.getEmojiById(avatarData.emojiId),
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }

        // Switch icon
        Icon(
            painter = painterResource(id = R.drawable.ic_check_round),
            contentDescription = "Select Profile",
            tint = colorResource(id = if (isSelected) R.color.accent_green else R.color.icon),
            modifier = Modifier
                .constrainAs(switch) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end)
                }
        )
    }
}
