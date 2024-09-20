package com.flowfoundation.wallet.page.backup

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
import com.flowfoundation.wallet.manager.backup.ACTION_GOOGLE_DRIVE_VIEW_FINISH
import com.flowfoundation.wallet.manager.backup.BackupItem
import com.flowfoundation.wallet.manager.drive.EXTRA_CONTENT
import com.flowfoundation.wallet.manager.drive.GoogleDriveAuthActivity
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
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.toast
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class BackupDetailActivity : BaseActivity(), OnMapReadyCallback, OnTransactionStateChange {
    private lateinit var binding: ActivityBackupDetailBinding
    private val backupKey by lazy {
        val keyInfo = intent.getStringExtra(EXTRA_BACKUP_KEY_INFO) ?: ""
        Gson().fromJson(keyInfo, BackupKey::class.java) ?: null
    }
    private lateinit var mMap: GoogleMap

    private val googleDriveViewReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                binding.btnRecoveryPhrase.setProgressVisible(false)
                val data = intent?.getParcelableArrayListExtra<BackupItem>(EXTRA_CONTENT) ?: return
                data.firstOrNull {
                    it.publicKey == backupKey?.info?.pubKey?.publicKey
                }?.let {
                    BackupViewMnemonicActivity.launch(this@BackupDetailActivity, it.data)
                } ?: run {
                    toast(msgRes = R.string.backup_not_found)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(googleDriveViewReceiver, IntentFilter(
                ACTION_GOOGLE_DRIVE_VIEW_FINISH))
        TransactionStateManager.addOnTransactionStateChange(this)
        binding = ActivityBackupDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()

        setupToolbar()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        bindInfo()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(googleDriveViewReceiver)
        super.onDestroy()
    }

    @SuppressLint("SetTextI18n")
    private fun bindInfo() {
        with(binding) {
            backupKey?.info?.backupInfo?.let {
                tvBackupName.text = BackupType.getBackupName(it.type)
            }
            val created = backupKey?.info?.backupInfo?.createTime
            backupKey?.let {
                val backupType = it.info?.backupInfo?.type ?: -1
                ivKeyType.setImageResource(BackupType.getBackupKeyIcon(backupType))
                tvKeyType.text = BackupType.getBackupName(backupType)
                val statusType: String
                val statusColor: Int
                val weight = it.info?.pubKey?.weight ?: 0
                if (it.isRevoking) {
                    statusType = "Revoking..."
                    statusColor = R.color.accent_orange.res2color()
                } else if (weight >= 1000){
                    statusType = "Full Access"
                    statusColor = R.color.accent_green.res2color()
                } else {
                    statusType = "Multi-sign"
                    statusColor = R.color.text_3.res2color()
                }
                tvStatusLabel.text = statusType
                tvStatusLabel.backgroundTintList = ColorStateList.valueOf(statusColor)
                tvStatusLabel.setTextColor(statusColor)
                btnRecoveryPhrase.setVisible(backupType == BackupType.GOOGLE_DRIVE.index)
            }
            backupKey?.info?.device?.let { deviceModel ->
                tvDeviceApplication.text = deviceModel.user_agent
                tvDeviceIp.text = deviceModel.ip
                tvDeviceLocation.text = deviceModel.city + ", " + deviceModel.countryCode
                tvDeviceDate.text =
                    if (created.isNullOrBlank()) formatGMTToDate(deviceModel.updated_at)
                    else formatGMTToDate(created)
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
            btnRecoveryPhrase.setOnClickListener {
                if (btnRecoveryPhrase.isProgressVisible()) {
                    return@setOnClickListener
                }
                btnRecoveryPhrase.setProgressVisible(true)
                GoogleDriveAuthActivity.viewMnemonic(this@BackupDetailActivity)
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