package com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.model

import com.reown.android.Core
import com.reown.sign.client.Sign

class PendingRequestModel(
    val request: Sign.Model.SessionRequest,
    val metadata: Core.Model.AppMetaData?,
)