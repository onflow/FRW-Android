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
import com.nftco.flow.sdk.FlowAddress
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityDeviceInfoBinding
import io.outblock.lilico.manager.flowjvm.lastBlockAccount
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceModel
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.formatGMTToDate
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.isNightMode
import io.outblock.lilico.utils.toast
import io.outblock.lilico.utils.uiScope
import org.joda.time.DateTimeUtils


class DeviceInfoActivity: BaseActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityDeviceInfoBinding

    private var deviceModel: DeviceModel? = null
    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()
        setupToolbar()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        (intent.getParcelableExtra(EXTRA_DEVICE_MODEL) as? DeviceModel)?.let { bindDevice(it) }
    }

    @SuppressLint("SetTextI18n")
    private fun bindDevice(deviceModel: DeviceModel) {
        this.deviceModel = deviceModel
        with(binding) {
            tvDeviceName.text = deviceModel.device_name
            tvDeviceApplication.text = deviceModel.user_agent
            tvDeviceIp.text = deviceModel.ip
            tvDeviceLocation.text = deviceModel.city + ", " + deviceModel.countryCode
            tvDeviceDate.text = formatGMTToDate(deviceModel.updated_at)

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
        //fetch keys from service and chain
        ioScope {
            val service = retrofit().create(ApiService::class.java)
            val response = service.getKeyDeviceInfo()
            val keyDeviceInfo = response.data.result ?: emptyList()
//            uiScope {
//                keyList.forEach { accountKey ->
//                    keyDeviceInfo.find { it.pubKey.publicKey == accountKey.publicKey.base16Value }
//                        ?.let {
//                            accountKey.deviceName = it.backupInfo?.name.takeIf { name -> !name.isNullOrEmpty() } ?: it.device?.device_name ?: ""
//                        }
//                }
//                keyListLiveData.value = keyList
//            }
            val account = FlowAddress(WalletManager.selectedWalletAddress()).lastBlockAccount()
//            if (account == null || account.keys.isEmpty()) {
//                toast(msgRes = R.string.revoke_device_failed)
//                binding.btnRevoke.setProgressVisible(false)
//                return@ioScope
//            }
            val keys = account?.keys ?: emptyList()
            keyDeviceInfo.map { it.device?.id == deviceId }.map {  }
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

        fun launch(context: Context, deviceModel: DeviceModel) {
            context.startActivity(Intent(context, DeviceInfoActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_MODEL, deviceModel)
            })
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        deviceModel?.let {
            val initialLatLng = LatLng(it.lat, it.lon)
            mMap.moveCamera(CameraUpdateFactory.newLatLng(initialLatLng))

            mMap.addMarker(MarkerOptions().position(initialLatLng)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_map_location_mark)))
        }
    }
}