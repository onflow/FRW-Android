package com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.model

import com.walletconnect.android.Core
import com.walletconnect.sign.client.Sign

class PendingRequestModel(
    val request: Sign.Model.PendingRequest,
    val metadata: Core.Model.AppMetaData?,
)