package com.example.privatebrowser

import android.content.Context
import java.net.URI

enum class BlockingLevel(val label: String) {
    OFF("オフ"),
    STANDARD("標準"),
    STRICT("強力"),
}

class SiteBlockingPreferences(context: Context, private val profileId: String) {
    private val preferences = context.applicationContext
        .getSharedPreferences("site_blocking", Context.MODE_PRIVATE)

    fun level(host: String): BlockingLevel = runCatching {
        BlockingLevel.valueOf(preferences.getString(key(host), null) ?: BlockingLevel.STANDARD.name)
    }.getOrDefault(BlockingLevel.STANDARD)

    fun setLevel(host: String, level: BlockingLevel) {
        preferences.edit().putString(key(host), level.name).apply()
    }

    private fun key(host: String) = "$profileId:${host.lowercase()}"
}

internal object LocalRequestBlocker {
    private val standardDomains = setOf(
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com",
    )
    private val strictDomains = standardDomains + setOf(
        "adnxs.com",
        "casalemedia.com",
        "openx.net",
        "pubmatic.com",
        "rubiconproject.com",
    )
    private val essentialDomains = setOf(
        "google.com",
        "google.co.jp",
        "gstatic.com",
        "googleapis.com",
        "recaptcha.net",
        "hcaptcha.com",
        "stripe.com",
        "paypal.com",
    )

    fun shouldBlock(url: String, level: BlockingLevel): Boolean {
        if (level == BlockingLevel.OFF) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty().lowercase()
        if (essentialDomains.any { domain -> host.isDomainOrSubdomain(domain) }) return false
        if (listOf("captcha", "login", "signin", "oauth", "checkout", "payment").any(path::contains)) return false
        val domains = if (level == BlockingLevel.STRICT) strictDomains else standardDomains
        return domains.any { domain -> host.isDomainOrSubdomain(domain) }
    }

    fun host(url: String): String = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")

    private fun String.isDomainOrSubdomain(domain: String): Boolean = this == domain || endsWith(".$domain")
}
