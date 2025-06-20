package com.flowfoundation.wallet.utils.error

import com.flowfoundation.wallet.utils.extensions.capitalizeV2


interface BaseError {
    val baseCode: Int
    val rawValue: String
    val errorCode: Int
        get() = baseCode + (this as Enum<*>).ordinal + 1
    val errorMessage: String
        get() = rawValue.replace(Regex("([A-Z])"), " $1").trim().capitalizeV2()
    val errorLog: String
        get() = "${this.javaClass.simpleName} - Code: $errorCode, RawValue: $rawValue"
    val errorCategory: String
        get() = this.javaClass.simpleName
}

enum class AccountError(
    override val rawValue: String,
    override val baseCode: Int = 1000
) : BaseError {
    DESERIALIZE_ACCOUNT_FAILED("deserializeAccountFailed"),
    DESERIALIZE_PREFIX_FAILED("deserializePrefixFailed"),
    DESERIALIZE_DISPLAY_TOKEN_FAILED("deserializeDisplayTokenFailed"),
    MIGRATE_PREFIX_FAILED("migratePrefixFailed"),
    SET_ANONYMOUS_FAILED("setAnonymousFiled"),
    REGISTER_USER_FAILED("registerUserFailed"),
    GET_CRYPTO_PROVIDER_FAILED("getCryptoProviderFailed"),
    INIT_FAILED("initFailed"),
    UPDATE_USER_INFO_FAILED("updateUserInfoFailed"),
    UPDATE_WALLET_INFO_FAILED("updateWalletInfoFailed"),
    GET_WALLET_FAILED("getWalletFailed"),
    WALLET_ERROR("walletError"),
    UNEXPECTED_ERROR("unexpectedError");
}

enum class WalletError(
    override val rawValue: String,
    override val baseCode: Int = 2000
) : BaseError {
    FETCH_FAILED("fetchFailed"),
    QUERY_PUBLIC_KEY_FAILED("queryPublicKeyFailed"),
    KEY_STORE_FAILED("keyStoreFailed"),
    QUERY_ACCOUNT_KEY_FAILED("queryAccountKeyFailed");
}

enum class BackupError(
    override val rawValue: String,
    override val baseCode: Int = 3000
) : BaseError {
    ADD_DEVICE_KEY_FAILED("addDeviceKeyFailed"),
    MULTI_RESTORE_FAILED("multiRestoreFailed"),
    KEYSTORE_RESTORE_FAILED("keystoreRestoreFailed"),
    PRIVATE_KEY_RESTORE_FAILED("privateKeyRestoreFailed"),
    SEED_PHRASE_RESTORE_FAILED("seedPhraseKeyRestoreFailed"),
    SYNC_ACCOUNT_INFO_FAILED("syncAccountInfoFailed"),
    RESTORE_LOGIN_FAILED("restoreLoginFailed"),
    RESTORE_IMPORT_FAILED("restoreImportFailed"),
    ADD_PUBLIC_KEY_FAILED("addPublicKeyFailed"),
    MNEMONIC_RESTORE_FAILED("mnemonicRestoreFailed");
}

enum class GoogleBackupError(
    override val rawValue: String,
    override val baseCode: Int = 4000
) : BaseError {
    ACCOUNT_SIGN_IN_FAILED("accountSignInFailed"),
    UPLOAD_BACKUP_ERROR("uploadBackupError"),
    READ_FILE_ERROR("readFileError");
}

enum class DropboxBackupError(
    override val rawValue: String,
    override val baseCode: Int = 5000
) : BaseError {
    UPLOAD_BACKUP_ERROR("uploadBackupError"),
    READ_FILE_ERROR("readFileError");
}

enum class StakingError(
    override val rawValue: String,
    override val baseCode: Int = 6000
) : BaseError {
    STAKING_QUERY_INFO_FAILED("stakingQueryInfoFailed"),
    STAKING_CHECK_SETUP_FAILED("stakingCheckSetupFailed"),
    STAKING_SETUP_FAILED("stakingSetupFailed"),
    STAKING_CREATE_DELEGATOR_ID_FAILED("stakingCreateDelegatorIdFailed"),
    STAKING_GET_DELEGATOR_INFO_FAILED("stakingGetDelegatorInfoFailed");
}

enum class EVMError(
    override val rawValue: String,
    override val baseCode: Int = 7000
) : BaseError {
    QUERY_EVM_ADDRESS_FAILED("queryEVMAddressFailed"),
    GET_ADDRESS_FAILED("getAddressFailed");
}

enum class CadenceError(
    override val rawValue: String,
    override val baseCode: Int = 8000
) : BaseError {
    NONE("none"),
    EMPTY("empty"),
    LOAD_SCRIPT_FAILED("loadScriptFailed"),
    FETCH_SCRIPT_FAILED("fetchScriptFailed"),
    EXECUTE_FAILED("cadenceExecuteFailed"),
    EMPTY_SCRIPT_SIGNATURE("emptyScriptSignature"),
    INVALID_SCRIPT_SIGNATURE("invalidScriptSignature"),
    SIGNATURE_VERIFICATION_ERROR("signatureVerificationError"),
    DECODE_SCRIPT_FAILED("decodeScriptFailed");
}

enum class MoveError(
    override val rawValue: String,
    override val baseCode: Int = 9000
) : BaseError {
    LOAD_NFT_LIST_FAILED("loadNFTListFailed"),
    LOAD_TOKEN_INFO_FAILED("loadTokenInfoFailed"),
    INVALIDATE_IDENTIFIER("invalidateIdentifier"),
    FAILED_TO_SUBMIT_TRANSACTION("failedToSubmitTransaction");
}
