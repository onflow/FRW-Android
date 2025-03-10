package com.flowfoundation.wallet.widgets.webview.evm.model

import android.os.Parcelable


@kotlinx.parcelize.Parcelize
class EVMDialogModel(
    val title: String? = null,
    val logo: String? = null,
    val url: String? = null,
    val network: String? = null,
    val cadence: String? = null,
) : Parcelable

@kotlinx.parcelize.Parcelize
class EVMTransactionDialogModel(
    val title: String? = null,
    val logo: String? = null,
    val url: String? = null,
    val toAddress: String? = null,
    val value: String? = null,
    val data: String? = null
) : Parcelable