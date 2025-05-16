package com.flowfoundation.wallet.page.component.deeplinking

/**
 * Enum for Universal Link hosts
 */
enum class UniversalLinkHost(val host: String) {
    LILICO("link.lilico.app"),
    FRW_LINK("frw-link.lilico.app"),
    FCW_LINK("fcw-link.lilico.app"),
    WALLET_LINK("link.wallet.flow.com"),
    WC("wc");

    companion object {
        fun fromHost(host: String?): UniversalLinkHost? = values().firstOrNull { it.host == host }
        
        fun isKnownHost(host: String?): Boolean = fromHost(host) != null
    }
}

/**
 * Enum for Deep Link schemes
 */
enum class DeepLinkScheme(val scheme: String) {
    FW("fw"),
    WC("wc"),
    FRW("frw"),
    FCW("fcw"),
    LILICO("lilico"),
    TG("tg");
    
    companion object {
        fun fromScheme(scheme: String?): DeepLinkScheme? = values().firstOrNull { it.scheme == scheme }
        
        fun isKnownScheme(scheme: String?): Boolean = fromScheme(scheme) != null
    }
} 