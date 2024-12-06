package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit

import android.graphics.BitmapFactory
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.firebase.storage.uploadAvatarToFirebase
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.flowjvm.Cadence
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.viewModelIOScope
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ChildAccountEditViewModel : ViewModel() {

    val progressDialogVisibleLiveData = MutableLiveData<Boolean>()
    val transactionFinishLiveData = MutableLiveData<Boolean>()

    private var avatarFilePath: String? = null
    private lateinit var childAccount: ChildAccount

    fun bindAccount(account: ChildAccount) {
        childAccount = account
    }

    fun updateAvatar(filePath: String) {
        this.avatarFilePath = filePath
    }

    fun save(name: String, description: String) {
        progressDialogVisibleLiveData.postValue(true)
        viewModelIOScope(this) {
            var avatarUrl: String? = childAccount.icon
            if (avatarFilePath != null && File(avatarFilePath!!).exists()) {
                avatarUrl = uploadAvatar()
                if (avatarUrl.isNullOrEmpty()) {
                    progressDialogVisibleLiveData.postValue(false)
                    return@viewModelIOScope
                }
            }

            val txId = Cadence.CADENCE_EDIT_CHILD_ACCOUNT.transactionByMainWallet {
                arg { address(childAccount.address) }
                arg { string(name) }
                arg { string(description) }
                arg { string(avatarUrl.orEmpty()) }
            }

            if (txId.isNullOrBlank()) {
                toast(R.string.edit_fail)
            } else {
                val transactionState = TransactionState(
                    transactionId = txId,
                    time = System.currentTimeMillis(),
                    state = FlowTransactionStatus.PENDING.num,
                    type = TransactionState.TYPE_TRANSACTION_DEFAULT,
                    data = Gson().toJson(childAccount),
                )
                TransactionStateManager.newTransaction(transactionState)
                pushBubbleStack(transactionState)
                transactionFinishLiveData.postValue(true)
            }
            progressDialogVisibleLiveData.postValue(false)
        }
    }

    private suspend fun uploadAvatar() = suspendCoroutine { continuation ->
        runBlocking {
            try {
                val bitmap = BitmapFactory.decodeFile(avatarFilePath!!)
                uploadAvatarToFirebase(bitmap) { avatarUrl ->
                    logd("upload avatar url", avatarUrl)
                    viewModelIOScope(this@ChildAccountEditViewModel) {
                        continuation.resume(avatarUrl)
                    }
                }
            } catch (e: Exception) {
                loge(e)
                continuation.resume(null)
            }
        }
    }

}