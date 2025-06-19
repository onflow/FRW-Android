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

class DrawerLayoutPresenter(
    private val drawer: DrawerLayout,
    private val binding: LayoutMainDrawerLayoutBinding,
) : BasePresenter<MainDrawerLayoutModel>, ChildAccountUpdateListenerCallback, OnWalletDataUpdate,
    OnEmojiUpdate {

    private val TAG = "DrawerLayoutPresenter"
    private val activity by lazy { findActivity(drawer) as FragmentActivity }
    private var isUpdatingWallet = false
    private val walletUpdateLock = Object()

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
        
        // FIXED: Use wallet ready callback to ensure proper timing
        WalletManager.onWalletReady {
            logd(TAG, "Wallet is ready, refreshing drawer")
            binding.refreshWalletList(true)
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
            
            // FIXED: Add delayed refresh to ensure all accounts are loaded - optimized for responsiveness
            ioScope {
                // Quick initial refresh
                binding.refreshWalletList(refreshBalance = false)
                
                // Short delay for immediate missing accounts
                kotlinx.coroutines.delay(300)
                
                // Check if child accounts are missing and need quick refresh
                val wallet = WalletManager.wallet()
                val mainAddress = wallet?.accounts?.values?.flatten()?.firstOrNull()?.address
                if (mainAddress != null) {
                    val currentLinkedCount = binding.llLinkedAccount.childCount
                    logd(TAG, "Current linked accounts in UI: $currentLinkedCount")
                    
                    // Only do expensive operations if accounts are actually missing
                    if (currentLinkedCount == 0) {
                        logd(TAG, "No linked accounts visible, doing targeted refresh")
                        
                        // Quick check for cached child accounts
                        val childAccountList = WalletManager.childAccountList(mainAddress)
                        val cachedChildAccounts = childAccountList?.get() ?: emptyList()
                        
                        if (cachedChildAccounts.isEmpty()) {
                            logd(TAG, "No cached child accounts, triggering background refresh")
                            // Non-blocking background refresh
                            childAccountList?.refresh()
                        }
                        
                        // Give a short time for refresh then update UI
                        kotlinx.coroutines.delay(500)
                        binding.refreshWalletList(refreshBalance = false)
                    }
                }
            }
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
        
        // FIXED: Add debouncing to prevent excessive refreshes and ensure UI update
        ioScope {
            // Wait a brief moment to allow for other potential updates
            kotlinx.coroutines.delay(200)
            
            // Always refresh if we have accounts, regardless of drawer state
            if (accounts.isNotEmpty()) {
                logd(TAG, "Refreshing wallet list due to child account update with ${accounts.size} accounts")
                binding.refreshWalletList()
                
                // Double-check that the accounts actually appear in the UI
                kotlinx.coroutines.delay(500)
                uiScope {
                    val currentLinkedCount = binding.llLinkedAccount.childCount
                    logd(TAG, "After child account update, linked accounts in UI: $currentLinkedCount")
                    
                    if (currentLinkedCount == 0 && accounts.isNotEmpty()) {
                        logd(TAG, "Child accounts not showing in UI, forcing another refresh")
                        ioScope {
                            binding.refreshWalletList()
                        }
                    }
                }
            } else {
                // Check if the drawer is currently open before refreshing
                uiScope {
                    if (drawer.isDrawerOpen(binding.root)) {
                        logd(TAG, "Drawer is open, refreshing wallet list for child account update")
                        binding.refreshWalletList()
                    } else {
                        logd(TAG, "Drawer is closed, skipping refresh for child account update")
                    }
                }
            }
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
                            logd(TAG, "Refreshing wallet list")
                            binding.refreshWalletList(true)
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
        binding.refreshWalletList()
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


