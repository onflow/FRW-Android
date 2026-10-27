package com.flowfoundation.wallet.firebase.auth

// Firebase Auth has been removed. Sessions now come from FRW-Server's own ES256 JWTs
// (flow-wallet-aws-migration/auth-contract.md). The package and the public function names are
// kept so existing call sites keep compiling; the behaviour behind them is new.

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.flowfoundation.wallet.cache.AccountCacheManager
import com.flowfoundation.wallet.firebase.messaging.getFirebaseMessagingToken
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.network.WalletHosts
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.FlowAccountInfo
import com.flowfoundation.wallet.network.model.LoginResponseData
import com.flowfoundation.wallet.network.model.LoginV4Request
import com.flowfoundation.wallet.network.model.RegisterResponseData
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "WalletAuth"

/** Tokens returned by /v4/login, /v4/register, /v3/import, /v1/auth/refresh. */
data class SessionTokens(
    val uid: String,
    val accessToken: String,
    val expiresIn: Long,
    val refreshToken: String,
    val refreshExpiresIn: Long,
) {
    companion object {
        /**
         * Accepts the JSON that React Native passes through the bridge in place of the old Firebase
         * custom token: {"id","access_token","refresh_token","expires_in","refresh_expires_in"}.
         */
        fun fromJson(json: String?): SessionTokens? = runCatching {
            if (json.isNullOrBlank() || !json.trim().startsWith("{")) return null
            val o = JsonParser.parseString(json).asJsonObject
            build(
                uid = o.str("id") ?: o.str("uid"),
                access = o.str("access_token"),
                expiresIn = o.long("expires_in"),
                refresh = o.str("refresh_token"),
                refreshExpiresIn = o.long("refresh_expires_in"),
            )
        }.getOrNull()

        internal fun build(uid: String?, access: String?, expiresIn: Long?, refresh: String?, refreshExpiresIn: Long?): SessionTokens? {
            if (uid.isNullOrBlank() || access.isNullOrBlank() || refresh.isNullOrBlank()) return null
            return SessionTokens(uid, access, expiresIn ?: 3600L, refresh, refreshExpiresIn ?: 7776000L)
        }

        private fun JsonObject.str(k: String): String? = if (has(k) && !get(k).isJsonNull) get(k).asString else null
        private fun JsonObject.long(k: String): Long? = if (has(k) && !get(k).isJsonNull) get(k).asLong else null
    }
}

fun LoginResponseData?.toSessionTokens(): SessionTokens? =
    this?.let { SessionTokens.build(it.id ?: it.uid, it.accessToken, it.expiresIn, it.refreshToken, it.refreshExpiresIn) }

fun RegisterResponseData?.toSessionTokens(): SessionTokens? =
    this?.let { SessionTokens.build(it.uid, it.accessToken, it.expiresIn, it.refreshToken, it.refreshExpiresIn) }

typealias FirebaseAuthCallback = (isSuccessful: Boolean, exception: Exception?) -> Unit

/**
 * Session store and token logic. Persisted in EncryptedSharedPreferences; replaces Firebase's
 * persisted currentUser.
 */
private object SessionStore {
    private const val PREFS = "wallet_auth_session"
    private const val K_UID = "uid"
    private const val K_ACCESS = "access_token"
    private const val K_ACCESS_EXP = "access_expires_at"
    private const val K_REFRESH = "refresh_token"
    private const val K_REFRESH_EXP = "refresh_expires_at"

    private const val ACCESS_MIN_REMAINING_MS = 5 * 60 * 1000L
    private const val ANON_MIN_REMAINING_MS = 2 * 60 * 1000L
    private const val LOGIN_WINDOW_MS = 2 * 60 * 1000L
    private const val RELOGIN_BACKOFF_MS = 60 * 1000L

    val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                Env.getApp(),
                PREFS,
                MasterKey.Builder(Env.getApp()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Env.getApp().getSharedPreferences("${PREFS}_backup", Context.MODE_PRIVATE)
        }
    }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val tokenMutex = Mutex()
    private val reloginMutex = Mutex()

    @Volatile private var anonToken: String? = null
    @Volatile private var anonExpiresAt: Long = 0
    @Volatile private var loginStartedAt: Long = 0
    @Volatile private var lastReloginFailureAt: Long = 0

    private fun now() = System.currentTimeMillis()

    fun uid(): String? = prefs.getString(K_UID, null)?.takeIf { it.isNotBlank() }
    private fun access(): String? = prefs.getString(K_ACCESS, null)?.takeIf { it.isNotBlank() }
    private fun accessExpiresAt(): Long = prefs.getLong(K_ACCESS_EXP, 0)
    private fun refresh(): String? = prefs.getString(K_REFRESH, null)?.takeIf { it.isNotBlank() }
    private fun refreshExpiresAt(): Long = prefs.getLong(K_REFRESH_EXP, 0)

    fun save(tokens: SessionTokens) {
        val t = now()
        prefs.edit()
            .putString(K_UID, tokens.uid)
            .putString(K_ACCESS, tokens.accessToken)
            .putLong(K_ACCESS_EXP, t + tokens.expiresIn * 1000)
            .putString(K_REFRESH, tokens.refreshToken)
            .putLong(K_REFRESH_EXP, t + tokens.refreshExpiresIn * 1000)
            .apply()
        anonToken = null
        anonExpiresAt = 0
        loginStartedAt = 0
        lastReloginFailureAt = 0
    }

    fun clear() {
        prefs.edit().clear().apply()
        anonToken = null
        anonExpiresAt = 0
        loginStartedAt = 0
    }

    /** A signed login is being prepared: hand out anonymous tokens, but keep the stored session. */
    fun beginLogin() {
        loginStartedAt = now()
        anonToken = null
        anonExpiresAt = 0
    }

    fun endLogin() {
        loginStartedAt = 0
    }

    fun loginInProgress() = loginStartedAt != 0L && now() - loginStartedAt < LOGIN_WINDOW_MS

    /**
     * Implements getToken() from the auth contract. Never throws; returns "" when no token can be
     * obtained (callers already handle an empty Bearer).
     */
    suspend fun token(forceRefresh: Boolean): String = withContext(Dispatchers.IO) {
        try {
            if (loginInProgress()) return@withContext anonymous(forceNew = false).orEmpty()

            val access = access()
            if (access != null && !forceRefresh && accessExpiresAt() - now() > ACCESS_MIN_REMAINING_MS) {
                return@withContext access
            }

            if (uid() != null) {
                when (val r = refreshSession()) {
                    is RefreshResult.Ok -> return@withContext r.accessToken
                    RefreshResult.SessionOver -> {
                        logw(TAG, "refresh token rejected, trying silent re-login")
                        silentRelogin()?.let { return@withContext it }
                    }
                    RefreshResult.NetworkError -> {
                        // Keep using the current access token while it is still valid.
                        if (access != null && accessExpiresAt() > now()) return@withContext access
                    }
                }
            } else if (legacyAccountUid() != null) {
                // Upgrade from a Firebase build, or a session lost after an aborted login: the wallet
                // key is on the device, so log in again without asking the user.
                silentRelogin()?.let { return@withContext it }
            }
            anonymous(forceNew = false).orEmpty()
        } catch (e: Exception) {
            loge(TAG, "token() failed: ${e.message}")
            ""
        }
    }

    /** A brand-new anonymous token, cached as the current one. Used as the message to sign. */
    suspend fun freshAnonymous(): String = withContext(Dispatchers.IO) {
        anonymous(forceNew = true).orEmpty()
    }

    private suspend fun anonymous(forceNew: Boolean): String? = tokenMutex.withLock {
        val cached = anonToken
        if (!forceNew && cached != null && anonExpiresAt - now() > ANON_MIN_REMAINING_MS) return@withLock cached
        val body = post("/v1/auth/anonymous", "{}", bearer = null) ?: return@withLock null
        val data = body.getAsJsonObject("data") ?: return@withLock null
        val token = data.get("access_token")?.takeIf { !it.isJsonNull }?.asString ?: return@withLock null
        val expiresIn = data.get("expires_in")?.takeIf { !it.isJsonNull }?.asLong ?: 900L
        anonToken = token
        anonExpiresAt = now() + expiresIn * 1000
        token
    }

    private sealed class RefreshResult {
        data class Ok(val accessToken: String) : RefreshResult()
        object SessionOver : RefreshResult()
        object NetworkError : RefreshResult()
    }

    private suspend fun refreshSession(): RefreshResult = tokenMutex.withLock {
        // Another caller may have refreshed while we waited for the lock.
        val current = access()
        if (current != null && accessExpiresAt() - now() > ACCESS_MIN_REMAINING_MS) return@withLock RefreshResult.Ok(current)

        val refresh = refresh()
        if (refresh == null || refreshExpiresAt() <= now()) return@withLock RefreshResult.SessionOver
        val payload = JsonObject().apply { addProperty("refresh_token", refresh) }.toString()
        val request = Request.Builder()
            .url("${WalletHosts.API}/v1/auth/refresh")
            .post(payload.toRequestBody(jsonType))
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code == 401) return@withLock RefreshResult.SessionOver
                if (!resp.isSuccessful) return@withLock RefreshResult.NetworkError
                val data = JsonParser.parseString(text).asJsonObject.getAsJsonObject("data")
                val tokens = SessionTokens.fromJson(data.toString()) ?: return@withLock RefreshResult.NetworkError
                save(tokens)
                RefreshResult.Ok(tokens.accessToken)
            }
        } catch (e: Exception) {
            logw(TAG, "refresh failed: ${e.message}")
            RefreshResult.NetworkError
        }
    }

    /**
     * Signed /v4/login with the current account's key. Uses its own HTTP client with an explicit
     * Bearer so it never re-enters token() through HeaderInterceptor. Concurrent callers don't wait
     * (they get null and fall back to an anonymous token) to rule out a deadlock.
     */
    private suspend fun silentRelogin(): String? {
        if (now() - lastReloginFailureAt < RELOGIN_BACKOFF_MS) return null
        if (!reloginMutex.tryLock()) return null
        try {
            val provider = CryptoProviderManager.getCurrentCryptoProvider() ?: run {
                logw(TAG, "silent re-login: no crypto provider for the current account")
                lastReloginFailureAt = now()
                return null
            }
            repeat(2) { attempt ->
                val anon = anonymous(forceNew = true) ?: return@repeat
                val signature = provider.getUserSignature(anon)
                val request = LoginV4Request(
                    flowAccountInfo = FlowAccountInfo(
                        accountKey = AccountKey(
                            publicKey = provider.getPublicKey(),
                            hashAlgo = provider.getHashAlgorithm().cadenceIndex,
                            signAlgo = provider.getSignatureAlgorithm().cadenceIndex,
                        ),
                        signature = signature,
                    ),
                    evmAccountInfo = (provider as? HDWalletCryptoProvider)?.getEvmAccountInfo(anon),
                    deviceInfo = DeviceInfoManager.getDeviceInfoRequest(),
                )
                val body = post("/v4/login", Gson().toJson(request), bearer = anon)
                val data = body?.getAsJsonObject("data")
                val tokens = data?.let { SessionTokens.fromJson(it.toString()) }
                if (tokens != null) {
                    save(tokens)
                    logd(TAG, "silent re-login ok (attempt ${attempt + 1})")
                    return tokens.accessToken
                }
            }
            lastReloginFailureAt = now()
            return null
        } catch (e: Exception) {
            loge(TAG, "silent re-login failed: ${e.message}")
            lastReloginFailureAt = now()
            return null
        } finally {
            reloginMutex.unlock()
        }
    }

    private fun post(path: String, json: String, bearer: String?): JsonObject? {
        val builder = Request.Builder()
            .url("${WalletHosts.API}$path")
            .post(json.toRequestBody(jsonType))
        if (bearer != null) builder.addHeader("Authorization", "Bearer $bearer")
        return try {
            http.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    logw(TAG, "POST $path -> HTTP ${resp.code}")
                    return null
                }
                JsonParser.parseString(text).asJsonObject
            }
        } catch (e: Exception) {
            logw(TAG, "POST $path failed: ${e.message}")
            null
        }
    }
}

/**
 * Starts a signed login (register, login, import, account switch). The stored session is NOT
 * deleted: if the login fails, the user stays signed in to the account they had. This is the fix
 * for the account-switch bug where a failed switch signed the user out into onboarding.
 * Always succeeds; kept as a suspend Boolean for call-site compatibility.
 */
suspend fun setToAnonymous(): Boolean {
    SessionStore.beginLogin()
    return true
}

/** Ends a signed login that failed or was abandoned; the previous session (if any) is used again. */
fun abortLogin() {
    SessionStore.endLogin()
}

/** Real sign-out (last account removed, wallet reset). */
fun clearAuthSession() {
    SessionStore.clear()
    cachedLegacyUid = null
    cachedLegacyUidAt = System.currentTimeMillis()
}

/** True when there is no signed-in user (anonymous tokens only). */
fun isAnonymousSignIn(): Boolean = firebaseUid() == null

fun isUserSignIn(): Boolean = !firebaseUid().isNullOrBlank()

/**
 * Stores the session returned by a login/register/import response. Replaces signInWithCustomToken.
 * Calls back synchronously.
 */
fun firebaseCustomLogin(tokens: SessionTokens?, onComplete: FirebaseAuthCallback) {
    if (tokens == null) {
        SessionStore.endLogin()
        onComplete.invoke(false, IllegalStateException("login response has no access_token"))
        return
    }
    SessionStore.save(tokens)
    logd(TAG, "session stored for uid ${tokens.uid}")
    onComplete.invoke(true, null)
    ioScope {
        clearUserCache()
        getFirebaseMessagingToken()
    }
}

/**
 * The signed-in user's server UID (the same value Firebase used, so legacy uid-keyed keystores keep
 * working). Before the first new-style login after an upgrade, falls back to the current account.
 */
fun firebaseUid(): String? {
    // While a signed login is in flight the user is "anonymous", as with Firebase: uid-keyed stores
    // (Wallet.kt) must not write the new account's key under the previous account's uid.
    if (SessionStore.loginInProgress()) return null
    return SessionStore.uid() ?: legacyAccountUid()
}

/**
 * UID of the active local account. AccountManager loads asynchronously at launch, so read the
 * account cache directly if it isn't ready yet (first launch after upgrading from a Firebase build).
 */
@Volatile private var cachedLegacyUid: String? = null
@Volatile private var cachedLegacyUidAt: Long = 0

private fun legacyAccountUid(): String? {
    AccountManager.get()?.wallet?.id?.takeIf { it.isNotBlank() }?.let { return it }
    // AccountCacheManager.read() hits disk; this is called often, so re-read at most every 10 s.
    val now = System.currentTimeMillis()
    if (now - cachedLegacyUidAt < 10_000) return cachedLegacyUid
    val uid = runCatching {
        val cached = AccountCacheManager.read().orEmpty()
        (cached.firstOrNull { it.isActive } ?: cached.firstOrNull())?.wallet?.id?.takeIf { it.isNotBlank() }
    }.getOrNull()
    cachedLegacyUid = uid
    cachedLegacyUidAt = now
    return uid
}

/** Bearer token for API calls: access token, refreshed or re-minted as needed, else anonymous. */
suspend fun getFirebaseJwt(forceRefresh: Boolean = false): String = SessionStore.token(forceRefresh)

/**
 * The message to sign for a login/register/import request. Always a fresh anonymous token (each
 * signed token mints a session once). It is also cached as the current Bearer, so the request that
 * carries the signature sends the same token. Call [setToAnonymous] first.
 */
suspend fun getSigningJwt(): String {
    SessionStore.beginLogin()
    return SessionStore.freshAnonymous()
}

/** Was: delete the Firebase anonymous user. Nothing to delete with server tokens. */
suspend fun deleteAnonymousUser(): Boolean = true

/** Was: Firebase signInAnonymously. Mints an anonymous token; true on success. */
suspend fun signInAnonymously(): Boolean = getSigningJwt().isNotBlank()
