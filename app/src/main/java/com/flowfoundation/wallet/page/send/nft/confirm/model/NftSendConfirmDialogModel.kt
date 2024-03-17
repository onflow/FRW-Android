package com.flowfoundation.wallet.page.send.nft.confirm.model

import com.flowfoundation.wallet.network.model.UserInfoData

class NftSendConfirmDialogModel(
    val userInfo: UserInfoData? = null,
    val amountConvert: Float? = null,
    val isSendSuccess: Boolean? = null,
)