package com.example.privatebrowser

/** UA policy is application-owned; cookies are site data and cannot reliably select WebView's UA. */
internal object SiteDisplayPolicy {
    fun prefersDesktop(url: String): Boolean {
        val host = FilterRules.hostOf(url)
        return FilterRules.domainMatches(host, "youtube.com") ||
            FilterRules.domainMatches(host, "nicovideo.jp")
    }
}
