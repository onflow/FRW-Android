package com.flowfoundation.wallet.page.dialog.processing.send.model

import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.network.model.UserInfoData
import java.math.BigDecimal

class SendProcessingDialogModel(
    val userInfo: UserInfoData? = null,
    val amountConvert: BigDecimal? = null,
    val stateChange: TransactionState? = null,
)