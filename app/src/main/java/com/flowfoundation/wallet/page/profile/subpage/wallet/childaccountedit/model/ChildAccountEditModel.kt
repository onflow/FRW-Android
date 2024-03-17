package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.model

import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import java.io.File

class ChildAccountEditModel(
    val account: ChildAccount? = null,
    val avatarFile: File? = null,
    var showProgressDialog: Boolean? = null,
)