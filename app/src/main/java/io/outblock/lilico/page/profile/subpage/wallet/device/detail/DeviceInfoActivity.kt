package io.outblock.lilico.page.profile.subpage.wallet.device.detail

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
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceModel
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.isNightMode


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

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mv_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        (intent.getParcelableExtra(EXTRA_DEVICE_MODEL) as? DeviceModel)?.let { bindDevice(it) }
    }

    private fun bindDevice(deviceModel: DeviceModel) {
        this.deviceModel = deviceModel
        with(binding) {
            tvDeviceName.text = deviceModel.device_name
            ppDeviceApplication.setDesc(deviceModel.user_agent)
            ppDeviceIp.setDesc(deviceModel.ip)
            ppDeviceLocation.setDesc(deviceModel.city + ", " + deviceModel.country)
            ppDeviceEntry.setDesc(deviceModel.created_at)
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
        title = R.string.linked_account.res2String()
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