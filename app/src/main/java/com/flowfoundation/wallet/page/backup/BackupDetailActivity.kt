package com.flowfoundation.wallet.page.backup

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
import com.google.gson.Gson
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityBackupDetailBinding
import com.flowfoundation.wallet.manager.account.AccountKeyManager
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.backup.model.BackupKey
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.AccountKeyActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.key.AccountKeyRevokeDialog
import com.flowfoundation.wallet.page.security.securityOpen
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatGMTToDate


class BackupDetailActivity : BaseActivity(), OnMapReadyCallback, OnTransactionStateChange {
    private lateinit var binding: ActivityBackupDetailBinding
    private val backupKey by lazy {
        val keyInfo = intent.getStringExtra(EXTRA_BACKUP_KEY_INFO) ?: ""
        Gson().fromJson(keyInfo, BackupKey::class.java) ?: null
    }
    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TransactionStateManager.addOnTransactionStateChange(this)
        binding = ActivityBackupDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        bindInfo()
    }

    @SuppressLint("SetTextI18n")
    private fun bindInfo() {
        with(binding) {
            backupKey?.info?.backupInfo?.let {
                tvBackupName.text = BackupType.getBackupName(it.type)
            }
            backupKey?.let {
                tvSerialNumber.text = "Key ${it.keyId}"
                val labelText: String
                val labelColor: Int
                if (it.isRevoking) {
                    labelText = "Revoking..."
                    labelColor = R.color.accent_orange.res2color()
                } else {
                    labelText = BackupType.getBackupName(it.info?.backupInfo?.type ?: -1)
                    labelColor = R.color.text_3.res2color()
                }
                tvDeviceLabel.text = labelText
                tvDeviceLabel.backgroundTintList = ColorStateList.valueOf(labelColor)
                tvDeviceLabel.setTextColor(labelColor)
                tvDeviceLabel.setVisible(labelText.isNotEmpty())
                it.info?.pubKey?.let { key ->
                    val weight = if (key.weight < 0) 0 else key.weight
                    tvKeyWeight.text = "$weight / 1000"
                    val progress = weight / 1000f
                    pbKeyWeight.max = 100
                    if (progress > 1) {
                        pbKeyWeight.progressTintList = ColorStateList.valueOf(R.color.accent_green
                            .res2color())
                        pbKeyWeight.progress = 100
                    } else {
                        pbKeyWeight.progress = (progress * 100).toInt()
                    }
                }
            }
            backupKey?.info?.device?.let { deviceModel ->
                tvDeviceApplication.text = deviceModel.user_agent
                tvDeviceIp.text = deviceModel.ip
                tvDeviceLocation.text = deviceModel.city + ", " + deviceModel.countryCode
                tvDeviceDate.text = formatGMTToDate(deviceModel.updated_at)
            }
            cvKeyCard.setOnClickListener {
                securityOpen(AccountKeyActivity.launchIntent(this@BackupDetailActivity))
            }
            btnDelete.setOnClickListener {
                backupKey?.let {
                    if (it.isRevoking) {
                        return@setOnClickListener
                    }
                    AccountKeyRevokeDialog.show(this@BackupDetailActivity, it.keyId)
                }
            }
        }

    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_REVOKE_KEY }
        transaction?.let { state ->
            if (state.isSuccess() && backupKey?.keyId == AccountKeyManager.getRevokingIndexId()) {
                finish()
            }
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
        title = R.string.backup_detail.res2String()
    }

    companion object {
        private const val EXTRA_BACKUP_KEY_INFO = "extra_backup_key_info"

        fun launch(context: Context, backupKey: BackupKey) {
            val intent = Intent(context, BackupDetailActivity::class.java)
            intent.putExtra(EXTRA_BACKUP_KEY_INFO, Gson().toJson(backupKey))
            context.startActivity(intent)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        backupKey?.info?.device?.let {
            val initialLatLng = LatLng(it.lat, it.lon)
            mMap.moveCamera(CameraUpdateFactory.newLatLng(initialLatLng))

            mMap.addMarker(
                MarkerOptions().position(initialLatLng)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_map_location_mark)))
        }
    }
}