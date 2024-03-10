package com.flowfoundation.wallet.page.profile.model

import com.flowfoundation.wallet.network.model.UserInfoData

class ProfileFragmentModel(
    val userInfo: UserInfoData? = null,
    val onResume: Boolean? = null,
    val inboxCount: Int? = null,
)