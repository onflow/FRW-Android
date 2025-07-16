package com.flowfoundation.wallet.page.wallet.confirm.presenter

import android.app.Activity
import com.google.gson.Gson
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityWalletConfirmBinding
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.walletconnect.chainId
import com.flowfoundation.wallet.manager.walletconnect.currentWcSession
import com.flowfoundation.wallet.manager.walletconnect.model.WCAccountInfo
import com.flowfoundation.wallet.manager.walletconnect.model.WCAccountKey
import com.flowfoundation.wallet.manager.walletconnect.model.WCAccountRequest
import com.flowfoundation.wallet.manager.walletconnect.model.WalletConnectMethod
import com.flowfoundation.wallet.page.wallet.confirm.model.ConfirmUserInfo
import com.flowfoundation.wallet.utils.error.BackupError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.WalletError
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.widgets.FlowLoadingDialog
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.utils.Env
import java.io.File
import org.onflow.flow.models.SigningAlgorithm

class WalletConfirmPresenter(
    private val activity: Activity,
    private val binding: ActivityWalletConfirmBinding
) : BasePresenter<ConfirmUserInfo> {

    private val loadingDialog by lazy { FlowLoadingDialog(activity) }

    override fun bind(model: ConfirmUserInfo) {
        with(binding) {
            avatarView.loadAvatar(model.userAvatar)
            usernameView.text = model.userName
            addressView.text = model.address
            btnNext.setOnClickListener { addDeviceKey(model.userName) }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun addDeviceKey(username: String) {
        loadingDialog.show()
        ioScope {
            try {
                val deviceInfoRequest = DeviceInfoManager.getWCDeviceInfo()
                val baseDir = File(Env.getApp().filesDir, "wallet")
                val privateKey = PrivateKey.create(FileSystemStorage(baseDir))
                val currentSession = currentWcSession() ?: return@ioScope
                val params = WCAccountRequest(
                    method = WalletConnectMethod.ADD_DEVICE_KEY.value,
                    data = WCAccountInfo(
                        WCAccountKey(publicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString() ?: ""),
                        deviceInfoRequest
                    )
                )

                SignClient.request(
                    Sign.Params.Request(
                        sessionTopic = currentSession.topic,
                        method = WalletConnectMethod.ADD_DEVICE_KEY.value,
                        params = Gson().toJson(params),
                        chainId = currentSession.chainId(),
                    )
                ) { error -> loge(error.throwable) }
            } catch (e: Exception) {
                if (e is IllegalStateException) {
                    ErrorReporter.reportCriticalWithMixpanel(WalletError.KEY_STORE_FAILED, e)
                } else {
                    ErrorReporter.reportWithMixpanel(BackupError.ADD_DEVICE_KEY_FAILED, e)
                }
            }
        }
    }

    fun dismissProgress() {
        loadingDialog.dismiss()
    }
}