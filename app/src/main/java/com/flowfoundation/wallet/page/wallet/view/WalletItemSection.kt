package com.flowfoundation.wallet.page.wallet.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.main.model.WalletAccountData
import com.flowfoundation.wallet.wallet.toAddress


@Composable
fun WalletItemSection(
    account: WalletAccountData,
    balanceMap: Map<String, String>,
    onItemSelected: (WalletAccountData) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(
                color = colorResource(id = R.color.bg_card),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 18.dp, vertical = 24.dp)
            .clickable(onClick = { onItemSelected(account)})
    ) {
        WalletAccountSection(
            item = account,
            balance = balanceMap[account.address.toAddress()] ?: ""
        )
        HorizontalDivider(
            color = colorResource(id = R.color.divider_25),
            modifier = Modifier
                .fillMaxWidth()
        )
        account.linkedAccounts.forEach { linkedAccount ->
            LinkedAccountSection(
                item = linkedAccount,
                balance = balanceMap[linkedAccount.address.toAddress()] ?: ""
            )
        }
    }
}