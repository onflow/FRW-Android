package com.flowfoundation.wallet.page.profile.subpage.wallet.device.detail

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityDeviceInfoBinding
import com.flowfoundation.wallet.manager.account.AccountKeyManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.profile.subpage.wallet.device.model.DeviceKeyModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.AccountKeyActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.AccountKeyRevokeDialog
import com.flowfoundation.wallet.page.security.securityOpen
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatGMTToDate
import com.flowfoundation.wallet.utils.isNightMode

class DeviceInfoActivity: BaseActivity(), OnMapReadyCallback, OnTransactionStateChange {

    private lateinit var binding: ActivityDeviceInfoBinding

    private var deviceKeyModel: DeviceKeyModel? = null
    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        TransactionStateManager.addOnTransactionStateChange(this)

        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()
        setupToolbar()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        (intent.getParcelableExtra(EXTRA_DEVICE_MODEL) as? DeviceKeyModel)?.let { bindDevice(it) }
    }

    @SuppressLint("SetTextI18n")
    private fun bindDevice(deviceKeyModel: DeviceKeyModel) {
        this.deviceKeyModel = deviceKeyModel
        val deviceModel = deviceKeyModel.deviceModel
        with(binding) {
            tvDeviceName.text = deviceModel.device_name
            tvDeviceApplication.text = deviceModel.user_agent
            tvDeviceIp.text = deviceModel.ip
            tvDeviceLocation.text = deviceModel.city + ", " + deviceModel.countryCode
            tvDeviceDate.text = formatGMTToDate(deviceModel.updated_at)
            btnRevoke.setVisible(canRevokeDevice())
            btnRevoke.setOnClickListener {
                if (canRevokeDevice().not()) {
                    return@setOnClickListener
                }
                if (btnRevoke.isProgressVisible()) {
                    return@setOnClickListener
                }
                btnRevoke.setProgressVisible(true)
                changeLabelStatus(true)
                deviceKeyModel.keyId?.let {
                    AccountKeyRevokeDialog.show(this@DeviceInfoActivity, it)
                }
            }

            ivKeyType.setImageResource(if (deviceModel.device_type == 2) R.drawable.ic_device_type_computer else R.drawable.ic_device_type_phone)
            tvKeyType.text = deviceModel.device_name
            changeLabelStatus(false)
            cvKeyCard.setOnClickListener {
                securityOpen(AccountKeyActivity.launchIntent(this@DeviceInfoActivity))
            }
        }
    }

    private fun changeLabelStatus(isRevoking: Boolean) {
        val statusType: String
        val statusColor: Int
        if (isRevoking) {
            statusType = "Revoking..."
            statusColor = R.color.accent_orange.res2color()
        } else {
            statusType = "Full Access"
            statusColor = R.color.accent_green.res2color()
        }
        with(binding.tvStatusLabel) {
            text = statusType
            backgroundTintList = ColorStateList.valueOf(statusColor)
            setTextColor(statusColor)
        }
    }

    private fun canRevokeDevice(): Boolean {
        return DeviceInfoManager.isCurrentDevice(deviceKeyModel?.deviceId ?: "").not() && deviceKeyModel?.keyId != null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.device_info.res2String()
    }

    companion object {
        private const val EXTRA_DEVICE_MODEL = "extra_device_model"

        fun launch(context: Context, deviceModel: DeviceKeyModel) {
            context.startActivity(Intent(context, DeviceInfoActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_MODEL, deviceModel)
            })
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        deviceKeyModel?.let {
            val initialLatLng = LatLng(it.deviceModel.lat, it.deviceModel.lon)
            mMap.moveCamera(CameraUpdateFactory.newLatLng(initialLatLng))

            mMap.addMarker(MarkerOptions().position(initialLatLng)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_map_location_mark)))
        }
    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_REVOKE_KEY }
        transaction?.let { state ->
            if (state.isSuccess() && deviceKeyModel?.keyId == AccountKeyManager.getRevokingIndexId()) {
                finish()
            }
        }
    }
}