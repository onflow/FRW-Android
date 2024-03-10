package io.outblock.lilico.page.wallet.confirm.presenter

import android.app.Activity
import com.google.gson.Gson
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.databinding.ActivityWalletConfirmBinding
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.walletconnect.chainId
import io.outblock.lilico.manager.walletconnect.currentWcSession
import io.outblock.lilico.manager.walletconnect.model.WCAccountInfo
import io.outblock.lilico.manager.walletconnect.model.WCAccountKey
import io.outblock.lilico.manager.walletconnect.model.WCAccountRequest
import io.outblock.lilico.manager.walletconnect.model.WalletConnectMethod
import io.outblock.lilico.network.generatePrefix
import io.outblock.lilico.page.wallet.confirm.model.ConfirmUserInfo
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.loadAvatar
import io.outblock.lilico.utils.loge
import io.outblock.lilico.widgets.FlowLoadingDialog
import io.outblock.wallet.KeyManager
import io.outblock.wallet.toFormatString


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

    private fun addDeviceKey(username: String) {
        loadingDialog.show()
        ioScope {
            val deviceInfoRequest = DeviceInfoManager.getWCDeviceInfo()
            val keyPair = KeyManager.generateKeyWithPrefix(generatePrefix(username))
            val currentSession = currentWcSession() ?: return@ioScope
            val params = WCAccountRequest(
                method = WalletConnectMethod.ADD_DEVICE_KEY.value,
                data = WCAccountInfo(
                    WCAccountKey(publicKey = keyPair.public.toFormatString()),
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
        }
    }

    fun dismissProgress() {
        loadingDialog.dismiss()
    }
}