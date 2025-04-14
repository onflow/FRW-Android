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

enum class LLError(
    override val rawValue: String,
    override val baseCode: Int = 1000
) : BaseError {
    AES_KEY_ENCRYPTION_FAILED("aesKeyEncryptionFailed"),
    AES_ENCRYPTION_FAILED("aesEncryptionFailed"),
    MISSING_USER_INFO_WHILE_BACKUP("missingUserInfoWhileBackup"),
    ALREADY_HAVE_WALLET("alreadyHaveWallet"),
    EMPTY_WALLET("emptyWallet"),
    DECRYPT_BACKUP_FAILED("decryptBackupFailed"),
    INCORRECT_PHRASE("incorrectPhrase"),
    EMPTY_ENCRYPT_KEY("emptyEncryptKey"),
    RESTORE_LOGIN_FAILED("restoreLoginFailed"),
    ACCOUNT_NOT_FOUND("accountNotFound"),
    FETCH_USER_INFO_FAILED("fetchUserInfoFailed"),
    INVALID_ADDRESS("invalidAddress"),
    INVALID_CADENCE("invalidCadence"),
    SIGN_FAILED("signFailed"),
    DECODE_FAILED("decodeFailed"),
    UNKNOWN("unknown");
}

enum class WalletError(
    override val rawValue: String,
    override val baseCode: Int = 2000
) : BaseError {
    FETCH_FAILED("fetchFailed"),
    FETCH_BALANCE_FAILED("fetchBalanceFailed"),
    EXISTING_MNEMONIC_MISMATCH("existingMnemonicMismatch"),
    STORE_AND_ACTIVE_MNEMONIC_FAILED("storeAndActiveMnemonicFailed"),
    MNEMONIC_MISSING("mnemonicMissing"),
    EMPTY_PUBLIC_KEY("emptyPublicKey"),
    INSUFFICIENT_BALANCE("insufficientBalance"),
    SECURITY_VERIFY_FAILED("securityVerifyFailed"),
    COLLECTION_IS_NIL("collectionIsNil"),
    NO_PRIMARY_WALLET_ADDRESS("noPrimaryWalletAddress");
}

enum class BackupError(
    override val rawValue: String,
    override val baseCode: Int = 3000
) : BaseError {
    MISSING_USER_NAME("missingUserName"),
    MISSING_MNEMONIC("missingMnemonic"),
    MISSING_UID("missingUid"),
    HEX_STRING_TO_DATA_FAILED("hexStringToDataFailed"),
    DECRYPT_MNEMONIC_FAILED("decryptMnemonicFailed"),
    TOP_VC_NOT_FOUND("topVCNotFound"),
    FILE_IS_NOT_EXIST_ON_CLOUD("fileIsNotExistOnCloud"),
    CLOUD_FILE_DATA("cloudFileData"),
    UNAUTHORIZED("unauthorized");
}

enum class GoogleBackupError(
    override val rawValue: String,
    override val baseCode: Int = 4000
) : BaseError {
    MISSING_LOGIN_USER("missingLoginUser"),
    NO_DRIVE_SCOPE("noDriveScope"),
    CREATE_FILE_ERROR("createFileError");
}

enum class CloudBackupError(
    override val rawValue: String,
    override val baseCode: Int = 5000
) : BaseError {
    INIT_ERROR("initError"),
    INVALID_LOAD_DATA("invalidLoadData"),
    CHECK_FILE_UPLOADED_STATUS_ERROR("checkFileUploadedStatusError"),
    OPEN_FILE_ERROR("openFileError"),
    OPENED_FILE_DATA_IS_NIL("opendFileDataIsNil"),
    NO_DATA_TO_SAVE("noDataToSave"),
    SAVE_TO_DATA_FAILED("saveToDataFailed"),
    FILE_IS_NOT_EXIST("fileIsNotExist");
}

enum class NFTError(
    override val rawValue: String,
    override val baseCode: Int = 6000
) : BaseError {
    NO_COLLECTION_INFO("noCollectionInfo"),
    INVALID_TOKEN_ID("invalidTokenId"),
    SEND_INVALID_ADDRESS("sendInvalidAddress");
}

enum class StakingError(
    override val rawValue: String,
    override val baseCode: Int = 7000
) : BaseError {
    STAKING_DISABLED("stakingDisabled"),
    STAKING_NEED_SETUP("stakingNeedSetup"),
    STAKING_SETUP_FAILED("stakingSetupFailed"),
    STAKING_CREATE_DELEGATOR_ID_FAILED("stakingCreateDelegatorIdFailed"),
    UNKNOWN("unknown");
}

enum class EVMError(
    override val rawValue: String,
    override val baseCode: Int = 8000
) : BaseError {
    ADDRESS_ERROR("addressError"),
    RPC_ERROR("rpcError"),
    CREATE_ACCOUNT("createAccount"),
    FIND_ADDRESS("findAddress"),
    TRANSACTION_RESULT("transactionResult");
}

enum class CadenceError(
    override val rawValue: String,
    override val baseCode: Int = 9000
) : BaseError {
    NONE("none"),
    EMPTY("empty"),
    TRANSACTION_FAILED("transactionFailed"),
    ARGUMENT("argument"),
    CONTRACT_NAME_IS_EMPTY("contractNameIsEmpty"),
    TOKEN_ADDRESS_EMPTY("tokenAddressEmpty"),
    STORAGE_PATH_EMPTY("storagePathEmpty");
}

enum class MoveError(
    override val rawValue: String,
    override val baseCode: Int = 10000
) : BaseError {
    LOAD_NFT_LIST_FAILED("loadNFTListFailed"),
    LOAD_TOKEN_INFO_FAILED("loadTokenInfoFailed"),
    INVALIDATE_IDENTIFIER("invalidateIdentifier"),
    FAILED_TO_SUBMIT_TRANSACTION("failedToSubmitTransaction");
}

enum class TokenBalanceProviderError(
    override val rawValue: String,
    override val baseCode: Int = 11000
) : BaseError {
    COLLECTION_NOT_FOUND("collectionNotFound");
}