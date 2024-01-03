package io.outblock.lilico.page.wallet.confirm

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import io.outblock.lilico.R
import io.outblock.lilico.databinding.DialogWalletConfirmationBinding
import io.outblock.lilico.manager.flowjvm.CADENCE_ADD_PUBLIC_KEY
import io.outblock.lilico.manager.flowjvm.transactionByMainWallet
import io.outblock.lilico.manager.flowjvm.ufix64Safe
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.walletconnect.model.WCAccountInfo
import io.outblock.lilico.manager.walletconnect.model.WCAccountKey
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.model.AccountSyncRequest
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.window.bubble.tools.pushBubbleStack
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.loge
import io.outblock.lilico.utils.toast


class WalletConfirmationDialog : BottomSheetDialogFragment(), OnMapReadyCallback,
    OnTransactionStateChange {

    private val infoJson by lazy { arguments?.getString(EXTRA_ACCOUNT_INFO) ?: "" }
    private val topic by lazy { arguments?.getString(EXTRA_SESSION_TOPIC) ?: "" }
    private val requestId by lazy { arguments?.getLong(EXTRA_REQUEST_ID) ?: 0L }
    private lateinit var binding: DialogWalletConfirmationBinding
    private var accountInfo: WCAccountInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogWalletConfirmationBinding.inflate(inflater)
        return binding.rootView
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (infoJson.isEmpty()) {
            return
        }
        TransactionStateManager.addOnTransactionStateChange(this)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        accountInfo = Gson().fromJson(infoJson, WCAccountInfo::class.java)
        accountInfo?.deviceInfo?.let {
            with(binding) {
                tvDeviceApplication.text = it.user_agent
                tvDeviceIp.text = it.ip
                tvDeviceLocation.text = it.city + ", " + it.countryCode
            }
        }
        binding.closeButton.setOnClickListener { dismissAllowingStateLoss() }
        binding.sendButton.setOnProcessing {
            sendPublicKey(accountInfo)
        }
    }

    private fun sendPublicKey(accountInfo: WCAccountInfo?) {
        accountInfo?.let {
            ioScope {
                val isAddSuccess = addPublicKey(it.accountKey)
                if (isAddSuccess.not()) {
                    toast(msg = "add public key failure")
                }
            }
        }
    }

    private fun sendWCResponse() {
        val response = Sign.Params.Response(
            sessionTopic = topic,
            jsonRpcResponse = Sign.Model.JsonRpcResponse.JsonRpcResult(
                requestId, ""
            )
        )
        logd(TAG, "respondAddDeviceKey:\n$response")
        SignClient.respond(response, onSuccess = { success ->
            logd(TAG, "success:${success}")
            dismiss()
        }) { error ->
            loge(error.throwable)
            toast(msg = "send response failure")
        }
    }

    private fun syncAccountInfo(accountInfo: WCAccountInfo, callback: (isSuccess: Boolean) -> Unit) {
        ioScope {
            try {
                val service = retrofit().create(ApiService::class.java)
                val resp = service.syncAccount(
                    AccountSyncRequest(
                        accountInfo.accountKey.getApiAccountKey(),
                        accountInfo.deviceInfo?.getApiDeviceInfo()
                    )
                )
                callback.invoke(resp.status == 200)
            } catch (e: Exception) {
                callback.invoke(false)
            }
        }
    }

    private suspend fun addPublicKey(accountKey: WCAccountKey): Boolean {
        try {
            val txId = CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
                arg { string(accountKey.publicKey) }
                arg { uint8(accountKey.signAlgo) }
                arg { uint8(accountKey.hashAlgo) }
                arg { ufix64Safe(1000) }
            }
            val transactionState = TransactionState(
                transactionId = txId!!,
                time = System.currentTimeMillis(),
                state = FlowTransactionStatus.PENDING.num,
                type = TransactionState.TYPE_ADD_PUBLIC_KEY,
                data = ""
            )
            TransactionStateManager.newTransaction(transactionState)
            pushBubbleStack(transactionState)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        accountInfo?.deviceInfo?.let {
            val initialLatLng = LatLng(it.lat, it.lon)
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(initialLatLng))

            googleMap.addMarker(
                MarkerOptions().position(initialLatLng)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_map_location_mark))
            )
        }

    }

    companion object {
        private const val EXTRA_REQUEST_ID = "extra_request_id"
        private const val EXTRA_SESSION_TOPIC = "extra_session_topic"
        private const val EXTRA_ACCOUNT_INFO = "extra_account_info"
        private val TAG = WalletConfirmationDialog::class.java.simpleName

        fun show(activity: FragmentActivity, requestId: Long, topic: String, infoJson: String) {
            WalletConfirmationDialog().apply {
                arguments = Bundle().apply {
                    putLong(EXTRA_REQUEST_ID, requestId)
                    putString(EXTRA_SESSION_TOPIC, topic)
                    putString(EXTRA_ACCOUNT_INFO, infoJson)
                }
            }.show(activity.supportFragmentManager, "")
        }
    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.firstOrNull { it.type == TransactionState.TYPE_ADD_PUBLIC_KEY }
        transaction?.let { state ->
            if (state.isSuccess()) {
                accountInfo?.let {
                    syncAccountInfo(it) { isSyncSuccess ->
                        if (isSyncSuccess) {
                            sendWCResponse()
                        } else {
                            toast(msg = "sync account info failure")
                        }
                    }
                }
            }
        }
    }
}