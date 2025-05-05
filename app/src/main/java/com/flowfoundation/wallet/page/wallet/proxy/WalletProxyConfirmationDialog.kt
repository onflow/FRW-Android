package com.flowfoundation.wallet.page.wallet.proxy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogWalletConfirmationBinding
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.walletconnect.model.WCDeviceInfo
import com.flowfoundation.wallet.manager.walletconnect.model.walletConnectProxyAccountResponse
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WalletProxyConfirmationDialog: BottomSheetDialogFragment(), OnMapReadyCallback {

    private val infoJson by lazy { arguments?.getString(EXTRA_DEVICE_INFO) ?: "" }
    private val jwt by lazy { arguments?.getString(EXTRA_ACCOUNT_JWT) ?: "" }
    private val topic by lazy { arguments?.getString(EXTRA_SESSION_TOPIC) ?: "" }
    private val requestId by lazy { arguments?.getLong(EXTRA_REQUEST_ID) ?: 0L }
    private lateinit var binding: DialogWalletConfirmationBinding
    private var deviceInfo: WCDeviceInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogWalletConfirmationBinding.inflate(inflater)
        return binding.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (infoJson.isEmpty() || jwt.isEmpty()) {
            toast(msgRes = R.string.failed)
            dismiss()
            return
        }
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        deviceInfo = Gson().fromJson(infoJson, WCDeviceInfo::class.java)
        deviceInfo?.let {
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
            sendKeyInfo()
        }
    }

    private fun sendKeyInfo() {
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val signature = cryptoProvider.getUserSignature(jwt)
                val response = Sign.Params.Response(
                    sessionTopic = topic,
                    jsonRpcResponse = Sign.Model.JsonRpcResponse.JsonRpcResult(
                        requestId, walletConnectProxyAccountResponse(
                            signature,
                            cryptoProvider.getPublicKey(),
                            cryptoProvider.getHashAlgorithm().cadenceIndex,
                            cryptoProvider.getSignatureAlgorithm().cadenceIndex,
                            cryptoProvider.getKeyWeight())
                    )
                )
                logd(TAG, "respondAccountInfo:\n$response")

                SignClient.respond(response, onSuccess = { success ->
                    logd(TAG, "success:${success}")
                    dismiss()
                }) { error ->
                    loge(error.throwable)
                    toast(msgRes = R.string.send_response_failure)
                }
            } catch (e: Exception) {
                loge(e)
                toast(msgRes = R.string.send_response_failure)
            }
        }
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "extra_request_id"
        private const val EXTRA_SESSION_TOPIC = "extra_session_topic"
        private const val EXTRA_ACCOUNT_JWT = "extra_account_jwt"
        private const val EXTRA_DEVICE_INFO = "extra_device_info"
        private val TAG = WalletProxyConfirmationDialog::class.java.simpleName

        fun show(
            activity: FragmentActivity, requestId: Long, topic: String, jwt: String,
            infoJson: String
        ) {
            WalletProxyConfirmationDialog().apply {
                arguments = Bundle().apply {
                    putLong(EXTRA_REQUEST_ID, requestId)
                    putString(EXTRA_SESSION_TOPIC, topic)
                    putString(EXTRA_ACCOUNT_JWT, jwt)
                    putString(EXTRA_DEVICE_INFO, infoJson)
                }
            }.show(activity.supportFragmentManager, "")
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        deviceInfo?.let {
            val initialLatLng = LatLng(it.lat ?: 0.0, it.lon ?: 0.0)
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(initialLatLng))

            googleMap.addMarker(
                MarkerOptions().position(initialLatLng)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_map_location_mark))
            )
        }
    }
}