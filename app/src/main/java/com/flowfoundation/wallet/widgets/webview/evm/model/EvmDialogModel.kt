package com.flowfoundation.wallet.widgets.webview.evm.model

import android.os.Parcelable


@kotlinx.parcelize.Parcelize
class EvmDialogModel(
    val title: String? = null,
    val logo: String? = null,
    val url: String? = null,
    val network: String? = null,
) : Parcelable