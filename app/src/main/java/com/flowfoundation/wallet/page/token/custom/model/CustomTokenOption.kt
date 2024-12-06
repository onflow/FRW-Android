package com.flowfoundation.wallet.page.token.custom.model

import com.flowfoundation.wallet.R


enum class CustomTokenOption(val layoutId: Int) {
    ADDRESS_INPUT(R.id.fragment_custom_token_address_input),
    CONTRACT_SELECT(R.id.fragment_custom_token_contract_select),
    INFO_IMPORT(R.id.fragment_custom_token_info_import)
}