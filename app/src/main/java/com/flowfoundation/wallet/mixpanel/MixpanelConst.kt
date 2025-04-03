package com.flowfoundation.wallet.mixpanel


enum class MixpanelEnv(val value: String) {
    DEV("development"),
    PROD("production")
}

enum class MixpanelRampSource(val value: String) {
    MOONPAY("moonpay"),
    COINBASE("coinbase"),
}

enum class MixpanelSecurityTool(val value: String) {
    BIOMETRIC("biometric"),
    PIN("pin"),
    NONE("none")
}

enum class MixpanelBackupProvider(val value: String) {
    GOOGLE_DRIVE("google_drive"),
    DROPBOX("dropbox"),
    SEED_PHRASE("seed_phrase")
}

enum class TransferAccountType(val value: String) {
    FLOW("flow"),
    CHILD("child"),
    COA("coa"),
    EVM("evm")
}

enum class AccountCreateKeyType(val value: String) {
    KEY_STORE("secure_enclave")
}

enum class RestoreType(val value: String) {
    MULTI_BACKUP("multi_backup"),
    SEED_PHRASE("seed_phrase"),
    PRIVATE_KEY("private_key"),
    KEYSTORE("keystore"),
    DEVICE_BACKUP("device_backup")
}

const val KEY_APP_ENV = "app_env"
const val KEY_FLOW_NETWORK = "flow_network"
const val KEY_CADENCE_SCRIPT_VERSION = "cadence_script_version"
const val KEY_CADENCE_VERSION = "cadence_version"
const val KEY_FW_DEVICE_ID = "fw_device_id"

const val EVENT_SCRIPT_ERROR = "script_error"
const val EVENT_DELEGATION_CREATED = "delegation_created"
const val EVENT_ON_RAMP_CLICKED = "on_ramp_clicked"
const val EVENT_COA_CREATION = "coa_creation"
const val EVENT_SECURITY_TOOL = "security_tool"
const val EVENT_MULTI_BACKUP_CREATED = "multi_backup_created"
const val EVENT_MULTI_BACKUP_CREATION_FAILED = "multi_backup_creation_failed"
const val EVENT_CADENCE_TRANSACTION_SIGNED = "cadence_transaction_signed"
const val EVENT_EVM_TRANSACTION_SIGNED = "evm_transaction_signed"
const val EVENT_FT_TRANSFER = "ft_transfer"
const val EVENT_NFT_TRANSFER = "nft_transfer"
const val EVENT_TRANSACTION_RESULT = "transaction_result"
const val EVENT_ACCOUNT_CREATED = "account_created"
const val EVENT_ACCOUNT_CREATION_TIME = "account_creation_time"
const val EVENT_ACCOUNT_RECOVERED = "account_recovered"

const val KEY_ERROR = "error"
const val KEY_SCRIPT_ID = "script_id"
const val KEY_ADDRESS = "address"
const val KEY_NODE_ID = "node_id"
const val KEY_AMOUNT = "amount"
const val KEY_SOURCE = "source"
const val KEY_TX_ID = "tx_id"
const val KEY_FLOW_ADDRESS = "flow_address"
const val KEY_ERROR_MESSAGE = "error_message"
const val KEY_TYPE = "type"
const val KEY_PROVIDERS = "providers"
const val KEY_SHA256_CADENCE = "cadence"
const val KEY_AUTHORIZERS = "authorizers"
const val KEY_PROPOSER = "proposer"
const val KEY_PAYER = "payer"
const val KEY_SUCCESS = "success"
const val KEY_EVM_ADDRESS = "evm_address"
const val KEY_FROM_ADDRESS = "from_address"
const val KEY_TO_ADDRESS = "to_address"
const val KEY_FT_IDENTIFIER = "ft_identifier"
const val KEY_NFT_IDENTIFIER = "nft_identifier"
const val KEY_IS_MOVE_ACTION = "isMove"
const val KEY_TRANSFER_FROM_TYPE = "from_type"
const val KEY_TRANSFER_TO_TYPE = "to_type"
const val KEY_IS_SUCCESSFUL = "is_successful"
const val KEY_PUBLIC_KEY = "public_key"
const val KEY_PUBLIC_KEY_TYPE = "key_type"
const val KEY_SIGN_ALGO = "sign_algo"
const val KEY_HASH_ALGO = "hash_algo"
const val KEY_RESTORE_MECHANISM = "mechanism"
const val KEY_RESTORE_MULTI_METHODS = "methods"