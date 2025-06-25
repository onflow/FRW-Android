package com.flowfoundation.wallet.page.main.presenter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.LayoutMainDrawerLayoutBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
import com.flowfoundation.wallet.manager.account.WalletFetcher
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.isDeveloperMode
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.childaccount.ChildAccountList
import com.flowfoundation.wallet.manager.childaccount.ChildAccountUpdateListenerCallback
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.OnEmojiUpdate
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.page.dialog.accounts.AccountSwitchDialog
import com.flowfoundation.wallet.page.evm.EnableEVMActivity
import com.flowfoundation.wallet.page.main.MainActivityViewModel
import com.flowfoundation.wallet.page.main.model.MainDrawerLayoutModel
import com.flowfoundation.wallet.page.main.refreshWalletList
import com.flowfoundation.wallet.page.main.widget.NetworkPopupMenu
import com.flowfoundation.wallet.page.restore.WalletRestoreActivity
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.capitalizeV2
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.parseAvatarUrl
import com.flowfoundation.wallet.utils.svgToPng
import com.flowfoundation.wallet.utils.uiScope
import kotlinx.coroutines.Job

class DrawerLayoutPresenter(
    private val drawer: DrawerLayout,
    private val binding: LayoutMainDrawerLayoutBinding,
) : BasePresenter<MainDrawerLayoutModel>, ChildAccountUpdateListenerCallback, OnWalletDataUpdate,
    OnEmojiUpdate {

    private val TAG = "DrawerLayoutPresenter"
    private val activity by lazy { findActivity(drawer) as FragmentActivity }
    private var isUpdatingWallet = false
    private val walletUpdateLock = Object()
    
    // FIXED: Add debouncing mechanism to prevent excessive refreshes
    private var lastRefreshTime = 0L
    private var refreshJob: Job? = null
    private val refreshDebounceMs = 1000L // 1 second debounce
    private var isRefreshing = false
    private val refreshLock = Object()

    init {
        logd(TAG, "Initializing DrawerLayoutPresenter")
        drawer.addDrawerListener(DrawerListener())

        with(binding.root.layoutParams) {
            width = (ScreenUtils.getScreenWidth() * 0.8f).toInt()
            binding.root.layoutParams = this
        }
        logd(TAG, "Drawer width set to: ${binding.root.layoutParams.width}")

        // Set initial lock mode
        ioScope {
            val wallet = WalletManager.wallet()
            logd(TAG, "Wallet state: ${if (wallet == null) "null" else "not null"}")
            val address = wallet?.walletAddress()
            logd(TAG, "Wallet address: $address")
            val lockMode = if (address.isNullOrBlank()) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED
            logd(TAG, "Initial drawer lock mode set to: $lockMode, address is ${if (address.isNullOrBlank()) "null/blank" else "present"}")
            drawer.setDrawerLockMode(lockMode)
        }

        with(binding) {
            accountSwitchButton.setOnClickListener { AccountSwitchDialog.show(activity.supportFragmentManager) }
            clEvmLayout.setOnClickListener {
                if (EVMWalletManager.haveEVMAddress()) {
                    drawer.close()
                } else {
                    EnableEVMActivity.launch(activity)
                }
            }
            flNetworkLayout.setVisible(isDeveloperMode())
            flNetworkLayout.setOnClickListener {
                NetworkPopupMenu(tvNetwork).show()
            }
            tvImportAccount.setOnClickListener {
                WalletRestoreActivity.launch(activity)
            }
        }

        bindData()
        logd(TAG, "Initial wallet list refresh")
        
        // FIXED: Use wallet ready callback to ensure proper timing with debouncing
        WalletManager.onWalletReady {
            logd(TAG, "Wallet is ready, scheduling debounced refresh")
            scheduleDeboucedRefresh(true)
            uiScope {
                val address = WalletManager.wallet()?.walletAddress()
                val lockMode = if (address.isNullOrBlank()) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED
                logd(TAG, "Updating drawer lock mode to: $lockMode after wallet ready")
                drawer.setDrawerLockMode(lockMode)
            }
        }

        logd(TAG, "Adding listeners for account updates")
        AccountEmojiManager.addListener(this)
        ChildAccountList.addAccountUpdateListener(this)
        WalletFetcher.addListener(this)
    }

    // FIXED: Add debounced refresh method to prevent excessive calls
    private fun scheduleDeboucedRefresh(refreshBalance: Boolean = false) {
        synchronized(refreshLock) {
            val currentTime = System.currentTimeMillis()
            
            // If we're already refreshing or too soon since last refresh, skip
            if (isRefreshing || (currentTime - lastRefreshTime) < refreshDebounceMs) {
                logd(TAG, "Skipping refresh - too frequent or already refreshing")
                return
            }
            
            // Cancel any pending refresh
            refreshJob?.cancel()
            
            refreshJob = ioScope {
                try {
                    isRefreshing = true
                    lastRefreshTime = currentTime
                    logd(TAG, "Executing debounced refresh")
                    binding.refreshWalletList(refreshBalance)
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    private fun initEVMLayoutTitle() {
        val text = R.string.enable_evm_title.res2String()
        val evmText = R.string.evm_on_flow.res2String()
        val index = text.indexOf(evmText)
        if (index < 0 || index + evmText.length > text.length) {
            binding.tvEvmTitle.text = text
        } else {
            val start = binding.tvEvmTitle.paint.measureText(text.substring(0, index))
            binding.tvEvmTitle.text = SpannableStringBuilder(text).apply {
                val startColor = R.color.evm_on_flow_start_color.res2color()
                val endColor = R.color.evm_on_flow_end_color.res2color()
                val gradientTextWidth = binding.tvEvmTitle.paint.measureText(text)
                val shader = LinearGradient(
                    start, 0f, gradientTextWidth, 0f,
                    intArrayOf(startColor, endColor), null,
                    Shader.TileMode.CLAMP,
                )
                setSpan(
                    ShaderSpan(shader),
                    index,
                    index + evmText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    override fun bind(model: MainDrawerLayoutModel) {
        model.refreshData?.let { bindData() }
        model.openDrawer?.let { drawer.open() }
    }

    private fun bindData() {
        logd(TAG, "Binding data to drawer")
        uiScope {
            with(binding.tvNetwork) {
                val network = chainNetWorkString()
                text = network.capitalizeV2()
                val color = when (network) {
                    "mainnet" -> R.color.mainnet
                    "testnet" -> R.color.testnet
                    else -> R.color.text
                }
                setTextColor(color.res2color())
                backgroundTintList = ColorStateList.valueOf(color.res2color()).withAlpha(16)
            }
        }
        ioScope {
            val userInfo = AccountManager.userInfo()
            logd(TAG, "User info: ${userInfo?.nickname}, avatar: ${userInfo?.avatar}")
            
            uiScope {
                with(binding) {
                    nickNameView.text = userInfo?.nickname ?: ""

                    val avatarUrl = userInfo?.avatar?.parseAvatarUrl()
                    logd(TAG, "Avatar URL: $avatarUrl")
                    val avatar = if (avatarUrl?.contains("flovatar.com") == true) {
                        avatarUrl.svgToPng()
                    } else {
                        avatarUrl
                    }
                    Glide.with(avatarView)
                        .asBitmap()
                        .load(avatar)
                        .placeholder(R.drawable.ic_placeholder)
                        .into(object : SimpleTarget<Bitmap>() {
                            override fun onResourceReady(
                                resource: Bitmap,
                                transition: Transition<in Bitmap>?
                            ) {
                                avatarView.setImageBitmap(resource)
                                val color = Palette.from(resource).generate().getDominantColor(R.color.text_sub.res2color())
                                val startColor = R.color.background60.res2color()
                                val endColor = ColorUtils.setAlphaComponent(color, 153)
                                val gradientDrawable = GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    intArrayOf(
                                        startColor,
                                        endColor
                                    )
                                )
                                gradientDrawable.cornerRadius = 12f
                                headerBg.background = gradientDrawable
                            }
                        })
                }
            }
        }
    }

    private fun bindEVMInfo() {
        if (EVMWalletManager.showEVMEnablePage()) {
            initEVMLayoutTitle()
            binding.clEvmLayout.visible()
        } else {
            binding.clEvmLayout.gone()
        }
    }

    private inner class DrawerListener : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerOpened(drawerView: View) {
            super.onDrawerOpened(drawerView)
            logd(TAG, "Drawer opened")
            bindData()
            bindEVMInfo()
            
            // FIXED: Use debounced refresh instead of multiple calls
            logd(TAG, "Drawer opened, scheduling debounced refresh")
            scheduleDeboucedRefresh(refreshBalance = false)
        }

        override fun onDrawerClosed(drawerView: View) {
            super.onDrawerClosed(drawerView)
            logd(TAG, "Drawer closed")
        }

        override fun onDrawerStateChanged(newState: Int) {
            super.onDrawerStateChanged(newState)
            val stateString = when (newState) {
                DrawerLayout.STATE_IDLE -> "IDLE"
                DrawerLayout.STATE_DRAGGING -> "DRAGGING"
                DrawerLayout.STATE_SETTLING -> "SETTLING"
                else -> "UNKNOWN"
            }
            logd(TAG, "Drawer state changed to: $stateString")
        }
    }

    override fun onChildAccountUpdate(parentAddress: String, accounts: List<ChildAccount>) {
        logd(TAG, "Child accounts updated. Parent: $parentAddress, accounts count: ${accounts.size}")
        accounts.forEach { account ->
            logd(TAG, "Child account: ${account.address}, name: ${account.name}")
        }
        
        // FIXED: Use debounced refresh to prevent excessive refreshes when balance is 0
        if (accounts.isNotEmpty()) {
            logd(TAG, "Child accounts available, scheduling debounced refresh")
            scheduleDeboucedRefresh(refreshBalance = false)
        } else {
            logd(TAG, "No child accounts available, skipping refresh")
        }
    }

    override fun onWalletDataUpdate(wallet: WalletListData) {
        logd(TAG, "Wallet data updated: ${wallet.walletAddress()}")
        val address = wallet.walletAddress()
        if (address.isNullOrBlank()) {
            logd(TAG, "Received wallet update with null/blank address")
            return
        }

        synchronized(walletUpdateLock) {
            if (isUpdatingWallet) {
                logd(TAG, "Wallet update already in progress, skipping")
                return
            }

            isUpdatingWallet = true
            ioScope {
                try {
                    logd(TAG, "Starting wallet update process")
                    // Update the wallet first
                    WalletManager.updateWallet(wallet)
                    
                    // Wait a short moment for the wallet to be initialized
                    var retryCount = 0
                    var currentWallet = WalletManager.wallet()
                    
                    while (currentWallet == null && retryCount < 3) {
                        logd(TAG, "Waiting for wallet initialization, attempt ${retryCount + 1}")
                        kotlinx.coroutines.delay(100)
                        currentWallet = WalletManager.wallet()
                        retryCount++
                    }
                    
                    uiScope {
                        if (currentWallet != null) {
                            logd(TAG, "Wallet initialized successfully with address: ${currentWallet.walletAddress()}")
                            val lockMode = DrawerLayout.LOCK_MODE_UNLOCKED
                            logd(TAG, "Updating drawer lock mode to: $lockMode after wallet update")
                            drawer.setDrawerLockMode(lockMode)
                            
                            // FIXED: Use debounced refresh instead of direct call
                            logd(TAG, "Wallet updated, scheduling debounced refresh")
                            scheduleDeboucedRefresh(refreshBalance = true)
                        } else {
                            logd(TAG, "Failed to initialize wallet after $retryCount attempts")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during wallet update: ${e.message}")
                    Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
                } finally {
                    isUpdatingWallet = false
                    logd(TAG, "Wallet update process completed")
                }
            }
        }
    }

    override fun onEmojiUpdate(userName: String, address: String, emojiId: Int, emojiName: String) {
        logd(TAG, "Emoji updated for user: $userName, address: $address, emoji: $emojiName")
        scheduleDeboucedRefresh(refreshBalance = false)
    }

    private inner class ShaderSpan(private val shader: Shader) : ForegroundColorSpan(0) {
        override fun updateDrawState(tp: TextPaint) {
            tp.shader = shader
        }
    }
}

fun openDrawerLayout(context: Context) {
    val activity = context as? FragmentActivity ?: return
    val viewModel = ViewModelProvider(activity)[MainActivityViewModel::class.java]
    viewModel.openDrawerLayout()
}


