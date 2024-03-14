package com.flowfoundation.wallet.page.walletcreate

import com.flowfoundation.wallet.R


const val WALLET_CREATE_STEP_LEGAL = 0
const val WALLET_CREATE_STEP_USERNAME = 1
const val WALLET_CREATE_STEP_WARNING = 2
const val WALLET_CREATE_STEP_MNEMONIC = 3
const val WALLET_CREATE_STEP_CLOUD_PWD = 4
const val WALLET_CREATE_STEP_MNEMONIC_CHECK = 5
const val WALLET_CREATE_STEP_PIN_GUIDE = 6
const val WALLET_CREATE_STEP_PIN_CODE = 7


private val WALLET_STEP_ROOT_ID = mapOf(
    WALLET_CREATE_STEP_LEGAL to R.id.fragment_wallet_create_legal,
    WALLET_CREATE_STEP_USERNAME to R.id.fragment_wallet_create_username,
    WALLET_CREATE_STEP_WARNING to R.id.fragment_wallet_create_warning,
    WALLET_CREATE_STEP_MNEMONIC to R.id.fragment_wallet_create_mnemonic,
    WALLET_CREATE_STEP_CLOUD_PWD to R.id.fragment_wallet_create_cloud_pwd,
    WALLET_CREATE_STEP_MNEMONIC_CHECK to R.id.fragment_wallet_create_mnemonic_check,
    WALLET_CREATE_STEP_PIN_GUIDE to R.id.fragment_wallet_create_pin_guide,
    WALLET_CREATE_STEP_PIN_CODE to R.id.fragment_wallet_create_pin_code,
)

fun getRootIdByStep(step: Int) = WALLET_STEP_ROOT_ID[step]!!