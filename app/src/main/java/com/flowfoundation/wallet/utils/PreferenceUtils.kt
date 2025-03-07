package com.flowfoundation.wallet.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency
import com.flowfoundation.wallet.page.token.detail.QuoteMarket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val PREFERENCE_TRADITIONAL = "PREFERENCE_TRADITIONAL"

private val KEY_REGISTERED = booleanPreferencesKey("KEY_REGISTERED")
private val KEY_NFT_COLLECTION_EXPANDED = booleanPreferencesKey("KEY_NFT_COLLECTION_EXPANDED")
private val KEY_BIOMETRIC_ENABLE = booleanPreferencesKey("KEY_BIOMETRIC_ENABLE")

private val KEY_BACKUP_MANUALLY = booleanPreferencesKey("KEY_BACKUP_MANUALLY")
private val KEY_BACKUP_GOOGLE_DRIVE = booleanPreferencesKey("KEY_BACKUP_GOOGLE_DRIVE")
private val KEY_BACKUP_MULTI = booleanPreferencesKey("KEY_BACKUP_MULTI")
private val KEY_DEVELOPER_MODE_ENABLE = booleanPreferencesKey("KEY_DEVELOPER_MODE_ENABLE")
private val KEY_CHAIN_NETWORK = intPreferencesKey("KEY_CHAIN_NETWORK")
private val KEY_THEME_MODE = intPreferencesKey("KEY_THEME_MODE")
private val KEY_WALLPAPER_ID = intPreferencesKey("KEY_WALLPAPER_ID")
private val KEY_QUOTE_MARKET = stringPreferencesKey("KEY_QUOTE_MARKET")
private val KEY_HIDE_WALLET_BALANCE = booleanPreferencesKey("KEY_HIDE_WALLET_BALANCE")
private val KEY_FREE_GAS_ENABLE = booleanPreferencesKey("KEY_FREE_GAS_ENABLE")
private const val KEY_IS_STAKING_GUIDE_PAGE_DISPLAYED = "KEY_IS_STAKING_GUIDE_PAGE_DISPLAYED"
private val KEY_IS_MEOW_DOMAIN_CLAIMED = booleanPreferencesKey("KEY_IS_MEOW_DOMAIN_CLAIMED")
private val KEY_INBOX_READ_LIST = stringPreferencesKey("KEY_INBOX_READ_LIST")
private val KEY_CURRENCY_FLAG = stringPreferencesKey("KEY_CURRENCY_FLAG")
private val KEY_IS_ROOT_DETECTED_DIALOG_SHOWN = booleanPreferencesKey("KEY_IS_ROOT_DETECTED_DIALOG_SHOWN")
private val KEY_IS_PROFILE_SWITCH_TIPS_SHOWN = booleanPreferencesKey("KEY_IS_PROFILE_SWITCH_TIPS_SHOWN")
private val KEY_DO_NOT_SHOW_MOVE_DIALOG = booleanPreferencesKey("KEY_DO_NOT_SHOW_MOVE_DIALOG")
private val KEY_DO_NOT_SHOW_BACKUP_DIALOG = booleanPreferencesKey("KEY_DO_NOT_SHOW_BACKUP_DIALOG")
private val KEY_NOTIFICATION_READ_LIST = stringPreferencesKey("KEY_NOTIFICATION_READ_LIST")

private const val KEY_SELECTED_WALLET_ADDRESS = "KEY_SELECTED_WALLET_ADDRESS"


private val KEY_VERSION_CODE = intPreferencesKey("KEY_VERSION_CODE")
private const val KEY_IS_NOTIFICATION_PERMISSION_CHECKED = "KEY_IS_NOTIFICATION_PERMISSION_CHECKED"
private const val KEY_TOKEN_UPLOADED_ADDRESS_SET = "KEY_TOKEN_UPLOADED_ADDRESS_SET"
private const val KEY_COA_LINK_CHECKED_ADDRESS_SET = "KEY_COA_LINK_CHECKED_ADDRESS_SET"
private val scope = CoroutineScope(Dispatchers.IO)

private val sharedPreferencesTraditional by lazy { Env.getApp().getSharedPreferences(PREFERENCE_TRADITIONAL, Context.MODE_PRIVATE) }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "main_preference")
private val dataStore = Env.getApp().dataStore

suspend fun isRegistered(): Boolean = dataStore.data.map { it[KEY_REGISTERED] ?: false }.first()

fun setRegistered() {
    edit { dataStore.edit { it[KEY_REGISTERED] = true } }
}

suspend fun isNewVersion(): Boolean {
    val currentVersionCode = BuildConfig.VERSION_CODE
    if (currentVersionCode > dataStore.data.map { it[KEY_VERSION_CODE] ?: -1 }.first()) {
        edit { dataStore.edit { it[KEY_VERSION_CODE] = currentVersionCode } }
        return true
    }
    return false
}

suspend fun isNftCollectionExpanded(): Boolean = dataStore.data.map { it[KEY_NFT_COLLECTION_EXPANDED] ?: false }.first()

suspend fun updateNftCollectionExpanded(isExpanded: Boolean) {
    dataStore.edit { it[KEY_NFT_COLLECTION_EXPANDED] = isExpanded }
}

suspend fun isBiometricEnable(): Boolean = dataStore.data.map { it[KEY_BIOMETRIC_ENABLE] ?: false }.first()

fun setBiometricEnable(isEnable: Boolean) {
    edit { dataStore.edit { it[KEY_BIOMETRIC_ENABLE] = isEnable } }
}

suspend fun isBackupManually(): Boolean = dataStore.data.map { it[KEY_BACKUP_MANUALLY] ?: false }.first()

fun setBackupManually() {
    edit { dataStore.edit { it[KEY_BACKUP_MANUALLY] = true } }
}

suspend fun isBackupGoogleDrive(): Boolean = dataStore.data.map { it[KEY_BACKUP_GOOGLE_DRIVE] ?: false }.first()

fun setBackupGoogleDrive(isBackuped: Boolean = true) {
    edit { dataStore.edit { it[KEY_BACKUP_GOOGLE_DRIVE] = isBackuped } }
}

suspend fun isMultiBackupCreated(): Boolean = dataStore.data.map { it[KEY_BACKUP_MULTI] ?: false }.first()

fun setMultiBackupCreated() {
    edit { dataStore.edit { it[KEY_BACKUP_MULTI] = true } }
}

fun setMultiBackupDeleted() {
    edit { dataStore.edit { it[KEY_BACKUP_MULTI] = false } }
}

suspend fun isDeveloperModeEnable(): Boolean = dataStore.data.map { it[KEY_DEVELOPER_MODE_ENABLE] ?: isDev() || isTesting() }.first()

fun setDeveloperModeEnable(isEnable: Boolean) {
    edit { dataStore.edit { it[KEY_DEVELOPER_MODE_ENABLE] = isEnable } }
}

suspend fun getChainNetworkPreference(): Int =
    dataStore.data.map { it[KEY_CHAIN_NETWORK] ?: if (isDev() || isTesting()) NETWORK_TESTNET else NETWORK_MAINNET }.first()

fun updateChainNetworkPreference(network: Int, callback: (() -> Unit)? = null) {
    edit {
        dataStore.edit { it[KEY_CHAIN_NETWORK] = network }
        callback?.invoke()
    }
}

suspend fun getThemeMode(): Int = dataStore.data.map { it[KEY_THEME_MODE] ?: AppCompatDelegate.MODE_NIGHT_YES }.first()

fun updateThemeMode(themeMode: Int) {
    edit { dataStore.edit { it[KEY_THEME_MODE] = themeMode } }
}

suspend fun getWallpaperId(): Int = dataStore.data.map { it[KEY_WALLPAPER_ID] ?: 2 }.first()

fun setWallpaperId(id: Int) {
    edit { dataStore.edit { it[KEY_WALLPAPER_ID] = id } }
}

suspend fun getQuoteMarket(): String = dataStore.data.map { it[KEY_QUOTE_MARKET] ?: QuoteMarket.binance.value }.first()

suspend fun updateQuoteMarket(market: String) {
    dataStore.edit { it[KEY_QUOTE_MARKET] = market }
}

suspend fun isHideWalletBalance(): Boolean = dataStore.data.map { it[KEY_HIDE_WALLET_BALANCE] ?: false }.first()

suspend fun setHideWalletBalance(isHide: Boolean) {
    dataStore.edit { it[KEY_HIDE_WALLET_BALANCE] = isHide }
}

suspend fun isFreeGasPreferenceEnable(): Boolean = dataStore.data.map { it[KEY_FREE_GAS_ENABLE] ?: AppConfig.isFreeGas() }.first()

suspend fun setFreeGasPreferenceEnable(isEnable: Boolean) {
    dataStore.edit { it[KEY_FREE_GAS_ENABLE] = isEnable }
}

fun isStakingGuideDisplayed(): Boolean {
    return sharedPreferencesTraditional.getBoolean(KEY_IS_STAKING_GUIDE_PAGE_DISPLAYED, false)
}

fun setStakingGuideDisplayed() {
    sharedPreferencesTraditional.edit().putBoolean(KEY_IS_STAKING_GUIDE_PAGE_DISPLAYED, true).apply()
}

fun isNotificationPermissionChecked(): Boolean {
    return sharedPreferencesTraditional.getBoolean(KEY_IS_NOTIFICATION_PERMISSION_CHECKED, false)
}

fun setNotificationPermissionChecked() {
    sharedPreferencesTraditional.edit().putBoolean(KEY_IS_NOTIFICATION_PERMISSION_CHECKED, true).apply()
}

suspend fun setMeowDomainClaimed(isClaimed: Boolean) {
    dataStore.edit { it[KEY_IS_MEOW_DOMAIN_CLAIMED] = isClaimed }
}

suspend fun updateInboxReadListPref(list: String) {
    dataStore.edit { it[KEY_INBOX_READ_LIST] = list }
}

suspend fun getNotificationReadList(): String = dataStore.data.map {
    it[KEY_NOTIFICATION_READ_LIST] ?: "" }.first()

suspend fun updateNotificationListPref(list: String) {
    dataStore.edit { it[KEY_NOTIFICATION_READ_LIST] = list }
}

suspend fun getCurrencyFlag(): String = dataStore.data.map { it[KEY_CURRENCY_FLAG] ?: Currency.USD.flag }.first()

suspend fun updateCurrencyFlag(flag: String, callback: (() -> Unit)? = null) {
    dataStore.edit {
        it[KEY_CURRENCY_FLAG] = flag
        callback?.invoke()
    }
}

suspend fun isRootDetectedDialogShown(): Boolean {
    return dataStore.data.map { it[KEY_IS_ROOT_DETECTED_DIALOG_SHOWN] ?: false }.first()
}

suspend fun setRootDetectedDialogShown() {
    dataStore.edit { it[KEY_IS_ROOT_DETECTED_DIALOG_SHOWN] = true }
}

suspend fun isProfileSwitchTipsShown(): Boolean {
    return dataStore.data.map { it[KEY_IS_PROFILE_SWITCH_TIPS_SHOWN] ?: false }.first()
}

suspend fun setProfileSwitchTipsShown() {
    dataStore.edit { it[KEY_IS_PROFILE_SWITCH_TIPS_SHOWN] = true }
}

suspend fun isShowMoveDialog(): Boolean {
    return dataStore.data.map { it[KEY_DO_NOT_SHOW_MOVE_DIALOG] ?: false }.first().not()
}

suspend fun setDoNotShowMoveDialog(notShow: Boolean) {
    dataStore.edit { it[KEY_DO_NOT_SHOW_MOVE_DIALOG] = notShow }
}

suspend fun isShowBackupDialog(): Boolean {
    return dataStore.data.map { it[KEY_DO_NOT_SHOW_BACKUP_DIALOG] ?: false }.first().not()
}

suspend fun setDoNotShowBackupDialog(notShow: Boolean) {
    dataStore.edit { it[KEY_DO_NOT_SHOW_BACKUP_DIALOG] = notShow }
}

fun getSelectedWalletAddress(): String? {
    return sharedPreferencesTraditional.getString(KEY_SELECTED_WALLET_ADDRESS, null)
}

fun updateSelectedWalletAddress(address: String) {
    sharedPreferencesTraditional.edit().putString(KEY_SELECTED_WALLET_ADDRESS, address).apply()
}

fun setUploadedAddressSet(addressSet: Set<String>) {
    sharedPreferencesTraditional.edit().putStringSet(KEY_TOKEN_UPLOADED_ADDRESS_SET, addressSet).apply()
}

fun getUploadedAddressSet(): Set<String> {
    return sharedPreferencesTraditional.getStringSet(KEY_TOKEN_UPLOADED_ADDRESS_SET, setOf()) ?: setOf()
}

fun getCOALinkCheckedAddressSet(): Set<String> {
    return sharedPreferencesTraditional.getStringSet(KEY_COA_LINK_CHECKED_ADDRESS_SET, setOf()) ?: setOf()
}

fun setCOALinkCheckedAddresssSet(addressSet: Set<String>) {
    sharedPreferencesTraditional.edit().putStringSet(KEY_COA_LINK_CHECKED_ADDRESS_SET, addressSet).apply()
}

private fun edit(unit: suspend () -> Unit) {
    scope.launch { unit.invoke() }
}