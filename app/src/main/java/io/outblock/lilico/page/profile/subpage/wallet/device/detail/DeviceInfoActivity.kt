package io.outblock.lilico.page.profile.subpage.wallet.device.detail

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityDeviceInfoBinding
import io.outblock.lilico.manager.account.AccountKeyManager
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceKeyModel
import io.outblock.lilico.page.profile.subpage.wallet.key.AccountKeyRevokeDialog
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.extensions.setVisible
import io.outblock.lilico.utils.formatGMTToDate
import io.outblock.lilico.utils.isNightMode


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
            btnRevoke.setVisible(DeviceInfoManager.isCurrentDevice(deviceModel.id).not())
            btnRevoke.setOnClickListener {
                if (btnRevoke.isProgressVisible()) {
                    return@setOnClickListener
                }
                btnRevoke.setProgressVisible(true)
                revokeLastKeyOfDevice(deviceModel.id)
            }
        }
    }

    private fun revokeLastKeyOfDevice(deviceId: String) {
        if (DeviceInfoManager.isCurrentDevice(deviceId)) {
            return
        }
        deviceKeyModel?.let {
            AccountKeyRevokeDialog.show(this@DeviceInfoActivity, it.keyId)
        }
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