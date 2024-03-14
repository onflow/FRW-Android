package com.flowfoundation.wallet.page.send.transaction.subpage.transaction.model

import com.flowfoundation.wallet.network.model.UserInfoData

class TransactionDialogModel(
    val userInfo: UserInfoData? = null,
    val amountConvert: Float? = null,
    val isSendSuccess: Boolean? = null,
)