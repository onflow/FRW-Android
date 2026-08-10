package com.flowfoundation.wallet.page.profile.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil3.compose.AsyncImage
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.parseAvatarUrl
import com.flowfoundation.wallet.utils.svgToPng

@Composable
fun SettingSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colorResource(R.color.bg_card), // Using bg_card color
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp)),
            content = content
        )
    }
}

@Composable
fun SettingItem(
    @DrawableRes iconRes: Int? = null,
    @StringRes titleRes: Int,
    description: String = "",
    showArrow: Boolean = true,
    showDivider: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = colorResource(R.color.accent_green),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))
            }

            // Content
            Text(
                text = stringResource(titleRes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colorResource(R.color.text_1),
                modifier = Modifier.weight(1f)
            )

            // Description or Arrow
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = colorResource(R.color.note)
                )
            } else if (showArrow) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = colorResource(R.color.icon),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                thickness = 1.dp,
                color = colorResource(R.color.profile_card_divider)
            )
        }
    }
}

@Composable
fun SettingSwitchItem(
    @DrawableRes iconRes: Int? = null,
    @StringRes titleRes: Int,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!isChecked) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = colorResource(R.color.accent_green),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))
            }

            // Content
            Text(
                text = stringResource(titleRes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colorResource(R.color.text_1),
                modifier = Modifier.weight(1f)
            )

            // SwitchCompat with FrozenSwitch styling
            AndroidView(
                factory = { context ->
                    SwitchCompat(context).apply {
                        setChecked(isChecked)

                        // Apply FrozenSwitch colors
                        val checkedThumbColor = R.color.colorSecondary.res2color()
                        val checkedTrackColor = R.color.colorSecondary_50.res2color()
                        val uncheckedColor = R.color.gray_99_40.res2color() // 40% transparent gray for unchecked track

                        val states = arrayOf(
                            intArrayOf(android.R.attr.state_checked), // checked
                            intArrayOf() // unchecked (default)
                        )
                        val trackColors = intArrayOf(checkedTrackColor, uncheckedColor)
                        val thumbColors = intArrayOf(checkedThumbColor, R.color.white.res2color())

                        trackTintList = android.content.res.ColorStateList(states, trackColors)
                        thumbTintList = android.content.res.ColorStateList(states, thumbColors)

                        // Set change listener
                        setOnCheckedChangeListener { _, checked ->
                            onCheckedChange(checked)
                        }

                        // Match view_settings_switch layout properties
                        minWidth = 0
                        minHeight = 0
                        setPadding(0, 0, 0, 0)
                    }
                },
                update = { switchCompat ->
                    switchCompat.isChecked = isChecked
                },
                modifier = Modifier.wrapContentSize()
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                thickness = 1.dp,
                color = colorResource(R.color.profile_card_divider)
            )
        }
    }
}

@Composable
fun UserProfileHeader(
    nickname: String,
    avatar: String,
    isSignedIn: Boolean,
    onAvatarClick: () -> Unit,
    onEditClick: () -> Unit,
    onNicknameClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (isSignedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with rounded rectangle shape
                val avatarUrl = avatar.parseAvatarUrl()
                val processedAvatar = if (avatarUrl.contains("flovatar.com")) {
                    avatarUrl.svgToPng()
                } else {
                    avatarUrl
                }

                AsyncImage(
                    model = processedAvatar,
                    contentDescription = "User Avatar",
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.ic_placeholder),
                    error = painterResource(id = R.drawable.ic_placeholder),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)) // Rounded rectangle
                        .clickable { onAvatarClick() }
                )

                Spacer(modifier = Modifier.width(16.dp))

                // User info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNicknameClick() }
                ) {
                    Text(
                        text = nickname,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.text_1)
                    )
                }

                // Edit button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = colorResource(R.color.white_10),
                            shape = CircleShape
                        )
                        .clickable { onEditClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = "Edit",
                        tint = colorResource(R.color.icon),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            // Not logged in state - styled card
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colorResource(R.color.deep_bg)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = colorResource(R.color.salmon_primary)
                ),
                onClick = { onNicknameClick() }
            ) {
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    val (avatar, welcomeText, signTips, arrowButton) = createRefs()

                    // Avatar
                    AsyncImage(
                        model = R.mipmap.ic_launcher,
                        contentDescription = "App Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .constrainAs(avatar) {
                                top.linkTo(parent.top)
                                start.linkTo(parent.start, margin = 12.dp)
                            }
                            .size(24.dp)
                            .clip(RoundedCornerShape(82.dp)) // Circular
                    )

                    // Welcome text
                    Text(
                        text = stringResource(R.string.welcome_to_wallet),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.text),
                        modifier = Modifier.constrainAs(welcomeText) {
                            top.linkTo(parent.top)
                            start.linkTo(avatar.end, margin = 12.dp)
                            end.linkTo(arrowButton.start, margin = 12.dp)
                            bottom.linkTo(signTips.top)
                            width = Dimension.fillToConstraints
                        }
                    )

                    // Sign tips text
                    Text(
                        text = stringResource(R.string.profile_sign_tips),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorResource(R.color.text),
                        modifier = Modifier.constrainAs(signTips) {
                            top.linkTo(welcomeText.bottom, margin = 8.dp)
                            start.linkTo(avatar.end, margin = 14.dp)
                            end.linkTo(arrowButton.start, margin = 12.dp)
                            bottom.linkTo(parent.bottom)
                            width = Dimension.fillToConstraints
                        }
                    )

                    // Arrow button
                    IconButton(
                        onClick = { onNicknameClick() },
                        modifier = Modifier
                            .constrainAs(arrowButton) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                end.linkTo(parent.end, margin = 20.dp)
                            }
                            .size(32.dp)
                            .clip(RoundedCornerShape(82.dp))
                            .background(Color.Transparent)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_circle_arrow_right_2),
                            contentDescription = "Login",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
