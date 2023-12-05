package io.outblock.lilico.page.wallet.sync.model

import android.graphics.Bitmap


class SyncReceiveModel(
    val data: SyncReceiveData? = null,
    val qrCode: Bitmap? = null
)

class SyncReceiveData(
    val username: String,
    val address: String,
    val avatar: String
)