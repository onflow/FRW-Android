package com.flowfoundation.wallet.page.wallet.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.page.main.model.WalletAccountData
import com.flowfoundation.wallet.utils.shortenEVMString


@Composable
fun WalletAccountSection(
    item: WalletAccountData,
    balance: String,
    isSelected: Boolean = false,
    canSelected: Boolean = false,
    onCopyClick: ((String) -> Unit)? = null,
    onAccountClick: (() -> Unit)? = null
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onAccountClick != null) { onAccountClick?.invoke() }
            .padding(vertical = 12.dp)
            .alpha(if (item.isSelected) 1f else 0.7f)
    ) {
        val (icon, name, address, balanceText, copy) = createRefs()
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(
                    width = 1.dp,
                    color = colorResource(
                        if (item.isSelected) R.color.accent_green else R.color.transparent
                    ),
                    shape = CircleShape
                )
                .constrainAs(icon) {
                    start.linkTo(parent.start)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = Color(Emoji.getEmojiColorRes(item.emojiId)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Emoji.getEmojiById(item.emojiId),
                    fontSize = 18.sp
                )
            }
        }
        Text(
            text = item.name,
            color = colorResource(id = R.color.text_primary),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .constrainAs(name) {
                    top.linkTo(parent.top)
                    bottom.linkTo(address.top)
                    start.linkTo(icon.end, 12.dp)
                    end.linkTo(copy.start, 12.dp)
                    width = Dimension.fillToConstraints
                }
        )
        Text(
            text = shortenEVMString(item.address),
            color = colorResource(id = R.color.text_secondary),
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
            color = colorResource(id = R.color.text_secondary),
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
        if (onCopyClick != null) {
            Icon(
                painter = painterResource(id = R.drawable.ic_copy_address),
                contentDescription = "Copy",
                tint = colorResource(id = R.color.icon),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = { onCopyClick(item.address) })
                    .constrainAs(copy) {
                        end.linkTo(parent.end)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    }
            )
        } else if (canSelected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check_round),
                contentDescription = "Select Profile",
                tint = colorResource(id = if (isSelected) R.color.icon else R.color.accent_green),
                modifier = Modifier
                    .size(20.dp)
                    .constrainAs(copy) {
                        end.linkTo(parent.end)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    }
            )
        }
    }
}