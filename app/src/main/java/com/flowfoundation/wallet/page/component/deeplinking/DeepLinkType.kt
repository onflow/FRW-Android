package com.flowfoundation.wallet.page.component.deeplinking

/**
 * Enum for Universal Link hosts
 */
enum class UniversalLinkHost(val host: String) {
    // Legacy lilico.app hosts: no longer in the manifest intent filter (Flow doesn't control the
    // domain), kept only so links scanned or pasted inside the app still parse. Nothing is fetched from them.
    LILICO("link.lilico.app"),
    FRW_LINK("frw-link.lilico.app"),
    FCW_LINK("fcw-link.lilico.app"),
    WALLET_LINK("link.wallet.flow.com"),
    WC("wc");

    companion object {
        fun fromHost(host: String?): UniversalLinkHost? = entries.firstOrNull {
            it.host.equals(host, ignoreCase = true) 
        }
        
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
    TG("tg"),
    HTTP("http"),
    HTTPS("https");
    
    companion object {
        fun fromScheme(scheme: String?): DeepLinkScheme? = entries.firstOrNull {
            it.scheme.equals(scheme, ignoreCase = true) 
        }
        
        fun isKnownScheme(scheme: String?): Boolean = fromScheme(scheme) != null
    }
} 