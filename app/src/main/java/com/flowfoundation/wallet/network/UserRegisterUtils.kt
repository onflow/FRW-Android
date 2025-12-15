package com.flowfoundation.wallet.network

import android.webkit.WebStorage
import android.widget.Toast
import com.flow.wallet.crypto.BIP39
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.FileSystemStorage
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.firebase.auth.firebaseCustomLogin
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.firebase.auth.signInAnonymously
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.evm.DAppEVMConnectionManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.key.KeyCompatibilityManager
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.mixpanel.AccountCreateKeyType
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.LoginRequest
import com.flowfoundation.wallet.network.model.RegisterRequest
import com.flowfoundation.wallet.network.model.RegisterResponse
import com.flowfoundation.wallet.page.walletrestore.firebaseLogin
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.NETWORK_MAINNET
import com.flowfoundation.wallet.utils.cleanBackupMnemonicPreference
import com.flowfoundation.wallet.utils.clearCacheDir
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.error.WalletError
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flowfoundation.wallet.utils.setMeowDomainClaimed
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.storeWalletPassword
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.updateChainNetworkPreference
import com.flowfoundation.wallet.wallet.Wallet
// Removed: import com.flowfoundation.wallet.wallet.createWalletFromServer - was causing duplicate account creation
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import org.onflow.flow.ChainId
import org.onflow.flow.models.SigningAlgorithm
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "UserRegisterUtils"

/**
 * Result from early return registration
 */
data class RegisterEarlyResult(
  val success: Boolean,
  val txId: String?,
  val prefix: String?,
  val username: String?,
  val error: String?
)

/**
 * Register user and initiate account creation, returning early with txId
 * Does NOT wait for transaction to seal - caller should monitor tx and then call initWalletWithTxId
 */
suspend fun registerOutblockEarlyReturn(
  username: String
): RegisterEarlyResult = suspendCoroutine { continuation ->
  ioScope {
    // Ensure we're on mainnet for account creation (backend creates accounts on mainnet)
    if (!isMainnet()) {
      logd(TAG, "[EarlyReturn] Switching to mainnet for account creation...")
      updateChainNetworkPreference(NETWORK_MAINNET)
      delay(100)
      refreshChainNetworkSync()
    }

    registerOutblockUserInternal(username) { isSuccess, prefix ->
      ioScope {
        if (!isSuccess) {
          continuation.resume(RegisterEarlyResult(
            success = false,
            txId = null,
            prefix = null,
            username = username,
            error = "Failed to register with backend"
          ))
          return@ioScope
        }

        val service = retrofit().create(ApiService::class.java)

        // Note: Don't call createWalletFromServer() here - it uses /v1/user/address
        // which would create a SECOND Flow account. We only need createWalletV2().
        setRegistered()

        // Create Flow account on-chain via backend API
        logd(TAG, "[EarlyReturn] Creating Flow account via /v2/user/address...")
        val txIdFromBackend: String?
        try {
          val createWalletResponse = service.createWalletV2()
          txIdFromBackend = createWalletResponse.data?.txid
          if (txIdFromBackend != null) {
            logd(TAG, "[EarlyReturn] Flow account creation initiated, txId: $txIdFromBackend")
          } else {
            logd(TAG, "[EarlyReturn] Flow account creation initiated but no txId returned")
            continuation.resume(RegisterEarlyResult(
              success = false,
              txId = null,
              prefix = prefix,
              username = username,
              error = "No txId returned from backend"
            ))
            return@ioScope
          }
        } catch (e: Exception) {
          loge(TAG, "[EarlyReturn] Failed to create Flow account: ${e.message}")
          continuation.resume(RegisterEarlyResult(
            success = false,
            txId = null,
            prefix = prefix,
            username = username,
            error = e.message ?: "Failed to create Flow account"
          ))
          return@ioScope
        }

        // Store prefix for later use by initWalletWithTxId
        pendingRegistrationPrefix = prefix

        // Return early with txId - RN will monitor the tx and call initWalletWithTxId when sealed
        logd(TAG, "[EarlyReturn] Returning early with txId: $txIdFromBackend, stored prefix: $prefix")
        continuation.resume(RegisterEarlyResult(
          success = true,
          txId = txIdFromBackend,
          prefix = prefix,
          username = username,
          error = null
        ))
      }
    }
  }
}

// Store prefix temporarily between early return and wallet init
private var pendingRegistrationPrefix: String? = null

/**
 * Initialize wallet after transaction has sealed
 * Called by RN after monitoring tx status confirms the transaction is sealed
 */
suspend fun initWalletWithTxId(
  txId: String
): Pair<Boolean, String?> = suspendCoroutine { continuation ->
  ioScope {
    try {
      logd(TAG, "[InitWallet] Starting wallet initialization with txId: $txId")

      val service = retrofit().create(ApiService::class.java)

      // Get user info
      val userInfo = try { service.userInfo().data } catch (e: Exception) {
        loge(TAG, "[InitWallet] Failed to fetch user info: ${e.message}")
        continuation.resume(Pair(false, null))
        return@ioScope
      }

      // Get the prefix from the pending registration (stored during early return)
      val prefix = pendingRegistrationPrefix
      if (prefix == null) {
        loge(TAG, "[InitWallet] No pending registration prefix found")
        continuation.resume(Pair(false, null))
        return@ioScope
      }
      logd(TAG, "[InitWallet] Using prefix from pending registration: $prefix")

      // Fetch account by txId using Wallet SDK
      val chainId = when (chainNetWorkString()) {
        "mainnet" -> ChainId.Mainnet
        "testnet" -> ChainId.Testnet
        else -> ChainId.Mainnet
      }

      val storage = FileSystemStorage(File(Env.getApp().filesDir, "wallet"))
      val keyForWalletSDK = KeyCompatibilityManager.getPrivateKeyWithFallback(prefix, storage)
      if (keyForWalletSDK == null) {
        loge(TAG, "[InitWallet] Failed to retrieve stored private key")
        continuation.resume(Pair(false, null))
        return@ioScope
      }

      val walletForSDK = WalletFactory.createKeyWallet(
        keyForWalletSDK,
        setOf(ChainId.Mainnet, ChainId.Testnet),
        storage
      )

      logd(TAG, "[InitWallet] Fetching account by txId: $txId")
      val fetchedAccount = walletForSDK.fetchAccountByCreationTxId(txId, chainId)
      val createdAddress = fetchedAccount.address

      logd(TAG, "[InitWallet] Account fetched successfully at address: $createdAddress")

      // Fetch wallet list to get wallet metadata
      val walletListData: com.flowfoundation.wallet.network.model.WalletListData?
      try {
        walletListData = service.getWalletList().data
        if (walletListData == null) {
          loge(TAG, "[InitWallet] Failed to fetch wallet list")
          continuation.resume(Pair(false, null))
          return@ioScope
        }
      } catch (e: Exception) {
        loge(TAG, "[InitWallet] Error fetching wallet list: ${e.message}")
        continuation.resume(Pair(false, null))
        return@ioScope
      }

      // Build initial walletNodes with the FlowWallet we know about
      val formattedCreatedAddress = if (createdAddress.startsWith("0x")) createdAddress else "0x$createdAddress"
      val emojiInfo = AccountEmojiManager.getEmojiByAddress(formattedCreatedAddress)
      val initialWalletNodes = listOf(
        FlowWallet(
          address = formattedCreatedAddress,
          name = emojiInfo.emojiName,
          emojiId = emojiInfo.emojiId,
          chainIdString = chainNetWorkString(),
          linkedWallets = emptyList()
        )
      )
      logd(TAG, "[InitWallet] Created initial FlowWallet node: address=$formattedCreatedAddress, network=${chainNetWorkString()}")

      // Add account to AccountManager with walletNodes populated
      AccountManager.add(
        Account(
          userInfo = userInfo,
          prefix = prefix,
          wallet = walletListData,
          walletNodes = initialWalletNodes
        ),
        firebaseUid()
      )

      logd(TAG, "[InitWallet] Account added to AccountManager with FlowWallet, address: $createdAddress")

      // Clear the pending prefix now that wallet init is complete
      pendingRegistrationPrefix = null

      continuation.resume(Pair(true, createdAddress))
    } catch (e: Exception) {
      loge(TAG, "[InitWallet] Error: ${e.message}")
      e.printStackTrace()
      // Clear pending prefix on error too
      pendingRegistrationPrefix = null
      continuation.resume(Pair(false, null))
    }
  }
}

// register one step, create user & create wallet
suspend fun registerOutblock(
  username: String,
) = suspendCoroutine { continuation ->
  ioScope {
    // Ensure we're on mainnet for account creation (backend creates accounts on mainnet)
    if (!isMainnet()) {
      logd(TAG, "Currently on testnet, switching to mainnet for account creation...")
      updateChainNetworkPreference(NETWORK_MAINNET)
      delay(100) // Small delay to ensure preference is saved
      refreshChainNetworkSync()
      logd(TAG, "Switched to mainnet: ${chainNetWorkString()}")
    } else {
      logd(TAG, "Already on mainnet, proceeding with account creation")
    }

    // registerOutblockUserInternal will call registerServer, which creates and stores
    // the primary private key associated with the prefix, and performs the actual
    // server registration using that key's public key.
    registerOutblockUserInternal(username) { isSuccess, prefix ->
      ioScope {
        if (isSuccess) {
          // At this point, user is registered on server, Firebase is synced,
          // and the correct private key (from registerServer) is stored with the prefix.

          // Declare service here for fetching user and wallet info
          val service = retrofit().create(ApiService::class.java)

          // Note: Don't call createWalletFromServer() here - it uses /v1/user/address
          // which would create a SECOND Flow account. We only need createWalletV2().
          setRegistered()

          // Wallet and Account object creation should use data from the successful registration (via registerServer)
          // The service calls here should ideally just fetch the latest state if needed,
          // not perform new registrations or key creations.

          val userInfo = try { service.userInfo().data } catch (e: Exception) {
            logd(TAG, "Failed to fetch user info after registration")
            continuation.resume(false)
            return@ioScope
          }

          // Create Flow account on-chain via backend API (using v2 endpoint that returns txId)
          logd(TAG, "Creating Flow account via /v2/user/address...")
          val txIdFromBackend: String?
          try {
            val createWalletResponse = service.createWalletV2()
            txIdFromBackend = createWalletResponse.data?.txid
            if (txIdFromBackend != null) {
              logd(TAG, "Flow account creation initiated, txId: $txIdFromBackend")
            } else {
              logd(TAG, "Flow account creation initiated (no txId returned)")
            }
          } catch (e: Exception) {
            loge(TAG, "Failed to create Flow account: ${e.message}")
            continuation.resume(false)
            return@ioScope
          }

          // Use fetchAccountByCreationTxId to directly fetch the created account
          // This is faster than waiting for the key indexer to poll the address
          val createdAddress: String?
          if (txIdFromBackend != null) {
            logd(TAG, "Using fetchAccountByCreationTxId to fetch account (txId: $txIdFromBackend)")
            try {
              val chainId = when (chainNetWorkString()) {
                "mainnet" -> ChainId.Mainnet
                "testnet" -> ChainId.Testnet
                else -> ChainId.Mainnet
              }

              // Initialize wallet SDK early to use fetchAccountByCreationTxId
              val storage = FileSystemStorage(File(Env.getApp().filesDir, "wallet"))
              val keyForWalletSDK = KeyCompatibilityManager.getPrivateKeyWithFallback(prefix, storage)
              if (keyForWalletSDK == null) {
                logd(TAG, "Failed to retrieve stored private key for Wallet SDK init from both new and old storage.")
                continuation.resume(false)
                return@ioScope
              }

              val walletForSDK = WalletFactory.createKeyWallet(
                keyForWalletSDK,
                setOf(ChainId.Mainnet, ChainId.Testnet),
                storage
              )

              // Use fetchAccountByCreationTxId instead of waitForCreatedAccountAddress
              // This directly fetches the account using the transaction ID
              val networkName = chainNetWorkString()
              logd(TAG, "=== NETWORK CHECK ===")
              logd(TAG, "chainNetWorkString(): $networkName")
              logd(TAG, "chainId being used: $chainId")
              logd(TAG, "isMainnet(): ${isMainnet()}")
              logd(TAG, "isTestnet(): ${com.flowfoundation.wallet.manager.app.isTestnet()}")
              logd(TAG, "Starting fetchAccountByCreationTxId call on network: $networkName (chainId: $chainId)")
              logd(TAG, "This may take time while waiting for blockchain to confirm transaction...")
              val startTime = System.currentTimeMillis()
              val account = walletForSDK.fetchAccountByCreationTxId(txIdFromBackend, chainId)
              val duration = System.currentTimeMillis() - startTime
              logd(TAG, "fetchAccountByCreationTxId completed in ${duration}ms")
              createdAddress = account.address

              logd(TAG, "Account fetched successfully at address: $createdAddress")
            } catch (e: Exception) {
              val errorType = e.javaClass.simpleName
              logd(TAG, "Error fetching account by creation txId ($errorType): ${e.message}")
              e.printStackTrace()
              continuation.resume(false)
              return@ioScope
            }
          } else {
            logd(TAG, "No txId received from backend, cannot proceed without blockchain confirmation")
            continuation.resume(false)
            return@ioScope
          }

          // Fetch wallet list to get wallet metadata (username, etc.)
          // We already have the address from blockchain via fetchAccountByCreationTxId
          logd(TAG, "Fetching wallet metadata from backend...")
          val walletListData: com.flowfoundation.wallet.network.model.WalletListData?
          try {
            walletListData = service.getWalletList().data
            if (walletListData == null) {
              logd(TAG, "Failed to fetch wallet list from backend")
              continuation.resume(false)
              return@ioScope
            }
          } catch (e: Exception) {
            logd(TAG, "Error fetching wallet list: ${e.message}")
            continuation.resume(false)
            return@ioScope
          }

          // Account is already fetched via fetchAccountByCreationTxId, no need to fetch again
          logd(TAG, "Account already populated in wallet SDK via fetchAccountByCreationTxId")

          // Log wallet data structure for debugging
          logd(TAG, "WalletListData structure: wallets count=${walletListData.wallets?.size}, username=${walletListData.username}")
          walletListData.wallets?.forEachIndexed { idx, wallet ->
            logd(TAG, "  Wallet $idx: name=${wallet.name}, blockchain count=${wallet.blockchain?.size}")
            wallet.blockchain?.forEach { blockchain ->
              logd(TAG, "    Blockchain: chainId=${blockchain.chainId}, address=${blockchain.address}")
            }
          }

          // Build initial walletNodes with the FlowWallet we know about
          // This ensures the account is usable immediately without waiting for async WalletDataManager
          val formattedCreatedAddress = if (createdAddress.startsWith("0x")) createdAddress else "0x$createdAddress"
          val emojiInfo = AccountEmojiManager.getEmojiByAddress(formattedCreatedAddress)
          val initialWalletNodes = listOf(
            FlowWallet(
              address = formattedCreatedAddress,
              name = emojiInfo.emojiName,
              emojiId = emojiInfo.emojiId,
              chainIdString = chainNetWorkString(),
              linkedWallets = emptyList()
            )
          )
          logd(TAG, "Created initial FlowWallet node: address=$formattedCreatedAddress, network=${chainNetWorkString()}")

          AccountManager.add(
            Account(
              userInfo = userInfo,
              prefix = prefix, // This prefix matches the one used to store the key in registerServer
              wallet = walletListData,
              walletNodes = initialWalletNodes
            ),
            firebaseUid()
          )
          logd(TAG, "Account added to AccountManager with FlowWallet in walletNodes.")

          // Get the Flow address from wallet data
          val flowAddress = walletListData.wallets
            ?.firstOrNull { wallet -> wallet.blockchain?.any { it.address.isNotBlank() } == true }
            ?.blockchain?.firstOrNull()?.address

          if (!flowAddress.isNullOrBlank()) {
            val formattedAddress = if (flowAddress.startsWith("0x")) flowAddress else "0x$flowAddress"
            WalletManager.selectWalletAddress(formattedAddress)
            logd(TAG, "Selected Flow address: $formattedAddress")
          }

          // Close the drawer immediately to prevent showing old account data
          com.flowfoundation.wallet.page.main.MainActivity.getInstance()?.closeDrawer()
          logd(TAG, "Closed drawer to prevent flash of old account data")

          // Now, get the CryptoProvider. It should use the prefix and load the key stored by registerServer.
          val currentAccount = AccountManager.get() // Should be the newly added account
          if (currentAccount == null || currentAccount.prefix != prefix) {
            loge(TAG, "Critical: currentAccount after add is null or prefix mismatch!")
            continuation.resume(false)
            return@ioScope
          }

          val cryptoProvider = CryptoProviderManager.generateAccountCryptoProvider(currentAccount)
          if (cryptoProvider == null) {
            loge(TAG, "Failed to generate crypto provider for the registered account.")
            continuation.resume(false)
            return@ioScope
          }
          logd(TAG, "Crypto provider generated successfully for registered account. Public key: ${cryptoProvider.getPublicKey()}")

          // The public key from this cryptoProvider SHOULD now match the on-chain key
          // because both originate from the single private key created and stored in registerServer.

          MixpanelManager.accountCreated(
            cryptoProvider.getPublicKey(),
            AccountCreateKeyType.KEY_STORE, // This might need re-evaluation; it's a prefix-stored key
            cryptoProvider.getSignatureAlgorithm().value,
            cryptoProvider.getHashAlgorithm().algorithm
          )
          clearUserCache()
          continuation.resume(true)
        } else {
          // Registration failed in registerOutblockUserInternal (e.g., server or Firebase issue)
          loge(TAG, "registerOutblockUserInternal indicated failure.")
          // resumeAccount() // This was here, consider if it's needed or if failure is handled by caller
          continuation.resume(false)
        }
      }
    }
  }
}

private suspend fun registerOutblockUserInternal(
  username: String,
  callback: (isSuccess: Boolean, prefix: String) -> Unit,
) {
  val prefix = generatePrefix(username)
  try {
    if (!setToAnonymous()) {
      resumeAccount()
      callback.invoke(false, prefix)
      return
    }
    val user = registerServer(username, prefix)

    if (user.status > 400) {
      callback(false, prefix)
      return
    }
    logd(TAG, "SYNC Register userId:::${user.data.uid}")
    logd(TAG, "start delete user")
    registerFirebase(user) { isSuccess ->
      callback.invoke(isSuccess, prefix)
    }
  } catch (e: Exception) {
    if (e is IllegalStateException) {
      ErrorReporter.reportCriticalWithMixpanel(WalletError.KEY_STORE_FAILED, e)
    } else {
      ErrorReporter.reportWithMixpanel(AccountError.REGISTER_USER_FAILED, e)
    }
    callback.invoke(false, prefix)
  }
}

private fun registerFirebase(user: RegisterResponse, callback: (isSuccess: Boolean) -> Unit) {
  FirebaseMessaging.getInstance().deleteToken()
  Firebase.auth.currentUser?.delete()?.addOnCompleteListener {
    logd(TAG, "delete user finish exception:${it.exception}")
    if (it.isSuccessful) {
      firebaseCustomLogin(user.data.customToken) { isSuccessful, _ ->
        if (isSuccessful) {
          MixpanelManager.identifyUserProfile()
          callback(true)
        } else callback(false)
      }
    } else callback(false)
  }
}

private suspend fun registerServer(username: String, prefix: String): RegisterResponse {
  logd(TAG, "Starting server registration for username: $username")
  val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
  val service = retrofit().create(ApiService::class.java)
  val baseDir = File(Env.getApp().filesDir, "wallet")
  val storage = FileSystemStorage(baseDir)

  try {
    // Generate and store mnemonic globally for potential future EOA support
    val mnemonic = BIP39.generate(BIP39.SeedPhraseLength.TWELVE)
    logd(TAG, "Generated new 12-word mnemonic for backup support")

    val passwordMap = try {
      val pref = readWalletPassword()
      if (pref.isBlank()) {
        HashMap<String, String>()
      } else {
        Gson().fromJson(pref, object : TypeToken<HashMap<String, String>>() {}.type)
      }
    } catch (e: Exception) {
      HashMap<String, String>()
    }

    // Store mnemonic globally (available for future EOA enablement if user chooses)
    storeWalletPassword(Gson().toJson(passwordMap.apply { put("global", mnemonic) }))
    logd(TAG, "Stored mnemonic globally for backup support")

    // Create a new private key
    val privateKey = PrivateKey.create(storage)
    logd(TAG, "Created new private key for registration")

    // Store the private key with prefix as ID for later retrieval
    val keyId = "prefix_key_$prefix"
    privateKey.store(keyId, prefix) // Use prefix as password for simplicity
    logd(TAG, "Stored private key with ID: $keyId")

    // Get the uncompressed public key using the fixed Flow-Wallet-Kit method
    val publicKeyBytes = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)
    if (publicKeyBytes == null) {
      logd(TAG, "Failed to get public key from private key")
      throw IllegalStateException("Failed to get public key from private key")
    }

    logd(TAG, "Public key size: ${publicKeyBytes.size} bytes")

    // Convert public key to hex string, removing "04" prefix if present
    // Flow expects uncompressed public keys without the format indicator
    val hexPublicKey = if (publicKeyBytes.size == 65 && publicKeyBytes[0] == 0x04.toByte()) {
      // Remove the "04" prefix for uncompressed keys
      publicKeyBytes.copyOfRange(1, publicKeyBytes.size).joinToString("") { "%02x".format(it) }
    } else {
      publicKeyBytes.joinToString("") { "%02x".format(it) }
    }
    logd(TAG, "Formatted public key: $hexPublicKey (${hexPublicKey.length} chars)")

    // Create registration request with correct algorithm parameters
    val request = RegisterRequest(
      username = username,
      accountKey = AccountKey(
        publicKey = hexPublicKey
        // Using default values: ECDSA_P256 and SHA2_256
      ),
      deviceInfo = deviceInfoRequest
    )

    logd(TAG, "Sending registration request: $request")
    try {
      val user = service.register(request)
      logd(TAG, "Registration response: $user")

      if (user.status > 400) {
        logd(TAG, "Registration failed with status: ${user.status}, message: ${user.message}")
        throw IllegalStateException("Registration failed with status: ${user.status}, message: ${user.message}")
      }

      return user
    } catch (e: retrofit2.HttpException) {
      val errorBody = e.response()?.errorBody()?.string()
      logd(TAG, "HTTP Error: ${e.code()}, Response: $errorBody")
      throw e
    }
  } catch (e: Exception) {
    logd(TAG, "Error during server registration: ${e.message}")
    logd(TAG, "Error stack trace: ${e.stackTraceToString()}")
    throw e
  }
}

fun generatePrefix(text: String): String {
  val timestamp = System.currentTimeMillis().toString()
  val combinedInput = "${text}_$timestamp"
  val bytes = MessageDigest.getInstance("SHA-256")
    .digest(combinedInput.toByteArray())
  return bytes.joinToString("") { "%02x".format(it) }
}

private suspend fun setToAnonymous(): Boolean {
  if (!isAnonymousSignIn()) {
    Firebase.auth.signOut()
    return signInAnonymously()
  }
  return true
}

// create user failed, resume account
private suspend fun resumeAccount() {
  if (!setToAnonymous()) {
    toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
    return
  }
  val deviceInfoRequest = DeviceInfoManager.getDeviceInfoRequest()
  val service = retrofit().create(ApiService::class.java)
  val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
  if (cryptoProvider == null) {
    toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
    return
  }
  val resp = service.login(
    LoginRequest(
      signature = cryptoProvider.getUserSignature(getFirebaseJwt()),
      accountKey = AccountKey(
        publicKey = cryptoProvider.getPublicKey(),
        hashAlgo = cryptoProvider.getHashAlgorithm().cadenceIndex,
        signAlgo = cryptoProvider.getSignatureAlgorithm().cadenceIndex
      ),
      deviceInfo = deviceInfoRequest
    )
  )
  if (resp.data?.customToken.isNullOrBlank()) {
    toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
    return
  }
  firebaseLogin(resp.data?.customToken!!) { isSuccess ->
    if (isSuccess) {
      setRegistered()
      if (AccountManager.get()?.prefix == null && AccountManager.get()?.keyStoreInfo == null) {
        Wallet.store().resume()
      }
    } else {
      toast(msgRes = R.string.resume_login_error, duration = Toast.LENGTH_LONG)
      return@firebaseLogin
    }
  }
}

suspend fun clearUserCache() {
  clearCacheDir()
  clearWebViewCache()
  setMeowDomainClaimed(false)
  FungibleTokenListManager.clear()
  WalletManager.clear()
  DAppEVMConnectionManager.clearPreferences()
  NftCollectionStateManager.clear()
  TransactionStateManager.reload()
  StakingManager.clear()
  CryptoProviderManager.clear()
  cleanBackupMnemonicPreference()
  delay(1000)
}

fun clearWebViewCache() {
  WebStorage.getInstance().deleteAllData()
}
