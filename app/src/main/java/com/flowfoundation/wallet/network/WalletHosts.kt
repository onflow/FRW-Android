package com.flowfoundation.wallet.network

import com.flowfoundation.wallet.utils.isDev

/**
 * Every backend host the app talks to, in one place.
 *
 * lilico.app is not a Flow-controlled domain and the lilico-334404 Cloud Functions project is gone,
 * so all hosts live under wallet.flow.com (see flow-wallet-aws-migration/auth-contract.md).
 *
 * The dev hosts follow the same pattern but are not provisioned yet; only release builds
 * (isDev() == false) are expected to work tonight.
 */
object WalletHosts {
    /** FRW-Server (Go): auth, register/login/import, user and account APIs. Was api.lilico.app. */
    val API: String = if (isDev()) "https://dev-api.wallet.flow.com" else "https://api.wallet.flow.com"

    /** FRW-tx-scanner (push token registration). Was scanner.lilico.app. */
    val SCANNER: String = if (isDev()) "https://dev-scanner.wallet.flow.com" else "https://scanner.wallet.flow.com"

    /** AWS Lambda replacements for us-central1-lilico-334404.cloudfunctions.net. Trailing slash kept for executeHttpFunction. */
    val FUNCTIONS: String = if (isDev()) "https://dev-functions.wallet.flow.com/" else "https://functions.wallet.flow.com/"

    /** FRW-web-next (Vercel): payer, token lists, svg2png, avatars, swap, cadence security check. Was lilico.app/api/... */
    val WEB: String = if (isDev()) "https://web-dev.api.wallet.flow.com" else "https://web.api.wallet.flow.com"

    /** Universal links and WalletConnect / FCL metadata. Was link/frw-link/fcw-link.lilico.app. */
    const val LINK: String = "https://link.wallet.flow.com"
}
