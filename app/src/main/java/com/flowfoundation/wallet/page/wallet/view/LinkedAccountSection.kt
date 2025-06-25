package com.flowfoundation.wallet.page.wallet.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.page.main.model.LinkedAccountData
import com.flowfoundation.wallet.utils.shortenEVMString


@Composable
fun LinkedAccountSection(
    item: LinkedAccountData,
    balance: String,
    onCopyClick: ((String) -> Unit)? = null,
    onAccountClick: (() -> Unit)? = null
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(enabled = onAccountClick != null) { onAccountClick?.invoke() }
            .alpha(if (item.isSelected) 1f else 0.7f)
    ) {
        val (linkedIcon, icon, name, address, balanceText, copy) = createRefs()
        Icon(
            painter = painterResource(id = R.drawable.ic_link),
            contentDescription = "LinkedAccount",
            tint = colorResource(id = R.color.icon),
            modifier = Modifier
                .size(20.dp)
                .constrainAs(linkedIcon) {
                    start.linkTo(parent.start, 14.dp)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                }
        )
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
                    start.linkTo(linkedIcon.end, 10.dp)
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .constrainAs(name) {
                    top.linkTo(parent.top)
                    bottom.linkTo(address.top)
                    start.linkTo(icon.end, 8.dp)
                    end.linkTo(copy.start, 12.dp)
                    width = Dimension.fillToConstraints
                }
        ) {

            Text(
                text = item.name,
                color = colorResource(id = R.color.text_primary),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f, fill = false)
            )

            if (item.isEVMAccount) {
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
                        color = colorResource(id = R.color.text_1),
                        fontSize = 8.sp
                    )
                }
            }
        }
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
        if (onCopyClick!= null) {
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
        }
    }
}
