package com.flowfoundation.wallet.page.wallet.confirm

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
import org.onflow.flow.models.TransactionStatus
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogWalletConfirmationBinding
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.walletconnect.model.WCAccountInfo
import com.flowfoundation.wallet.manager.walletconnect.model.WCAccountKey
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.AccountSyncRequest
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import org.onflow.flow.infrastructure.Cadence.Companion.uint8


class WalletConfirmationDialog : BottomSheetDialogFragment(), OnMapReadyCallback,
    OnTransactionStateChange {

    private val infoJson by lazy { arguments?.getString(EXTRA_ACCOUNT_INFO) ?: "" }
    private val topic by lazy { arguments?.getString(EXTRA_SESSION_TOPIC) ?: "" }
    private val requestId by lazy { arguments?.getLong(EXTRA_REQUEST_ID) ?: 0L }
    private lateinit var binding: DialogWalletConfirmationBinding
    private var accountInfo: WCAccountInfo? = null
    private var currentTxId: String? = null

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
                tvDeviceIp.text = it.ip ?: ""
                tvDeviceLocation.text = if (it.city.isNullOrBlank()) {
                    it.countryCode ?: ""
                }  else {
                    it.city + ", " + (it.countryCode ?: "")
                }

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
                    toast(msgRes = R.string.add_public_key_failure)
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
            toast(msgRes = R.string.send_response_failure)
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
            val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
                val pubKeyWithPrefix = accountKey.publicKey // e.g., "04..."
                val pubKeyHexRaw = pubKeyWithPrefix.removePrefix("0x")
                
                // Flow's Cadence addKey script expects the publicKey string argument to be the
                // 64-byte hex representation (128 chars) WITHOUT the "04" uncompressed prefix.
                val pubKeyForCadence = if (pubKeyHexRaw.startsWith("04") && pubKeyHexRaw.length == 130) {
                    pubKeyHexRaw.substring(2)
                } else {
                    pubKeyHexRaw
                }
                
                arg { string(pubKeyForCadence) }
                arg { uint8(accountKey.signAlgo.toUByte()) }
                arg { uint8(accountKey.hashAlgo.toUByte()) }
                arg { ufix64Safe(1000) }
            }
            val transactionState = TransactionState(
                transactionId = txId!!,
                time = System.currentTimeMillis(),
                state = TransactionStatus.PENDING.ordinal,
                type = TransactionState.TYPE_ADD_PUBLIC_KEY,
                data = ""
            )
            currentTxId = txId
            TransactionStateManager.newTransaction(transactionState)
            pushBubbleStack(transactionState)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        accountInfo?.deviceInfo?.let {
            val initialLatLng = LatLng(it.lat ?: 0.0, it.lon ?: 0.0)
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
            transactionList.lastOrNull { it.type == TransactionState.TYPE_ADD_PUBLIC_KEY }
        transaction?.let { state ->
            if (currentTxId == state.transactionId && state.isSuccess()) {
                accountInfo?.let {
                    currentTxId = null
                    syncAccountInfo(it) { isSyncSuccess ->
                        if (isSyncSuccess) {
                            sendWCResponse()
                        } else {
                            toast(msgRes = R.string.sync_account_info_failure)
                        }
                    }
                }
            }
        }
    }
}