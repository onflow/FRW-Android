package com.flowfoundation.wallet.page.receive.model

import android.graphics.drawable.Drawable

class ReceiveModel(
    val data: ReceiveData? = null,
    val qrcode: Drawable? = null,
)

class ReceiveData(
    val walletName: String,
    val address: String,
)