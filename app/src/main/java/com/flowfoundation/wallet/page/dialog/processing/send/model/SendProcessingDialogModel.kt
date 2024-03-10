package com.flowfoundation.wallet.page.dialog.processing.send.model

import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.network.model.UserInfoData

class SendProcessingDialogModel(
    val userInfo: UserInfoData? = null,
    val amountConvert: Float? = null,
    val stateChange: TransactionState? = null,
)