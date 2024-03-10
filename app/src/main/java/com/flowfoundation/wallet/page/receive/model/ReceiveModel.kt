package com.flowfoundation.wallet.page.receive.model

import android.graphics.Bitmap

class ReceiveModel(
    val data: ReceiveData? = null,
    val qrcode: Bitmap? = null,
)

class ReceiveData(
    val walletName: String,
    val address: String,
)