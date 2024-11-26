package com.flowfoundation.wallet.page.send.transaction.subpage.transaction.model

import com.flowfoundation.wallet.network.model.UserInfoData
import java.math.BigDecimal

class TransactionDialogModel(
    val userInfo: UserInfoData? = null,
    val amountConvert: BigDecimal? = null,
    val isSendSuccess: Boolean? = null,
)