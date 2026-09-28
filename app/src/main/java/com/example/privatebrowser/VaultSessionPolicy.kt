package com.example.privatebrowser

/** Policy only, not authentication. No credentials are stored or unlocked by this class. */
internal class VaultSessionPolicy(private val now: () -> Long) {
    private var authenticatedAt: Long? = null
    private var backgroundAt: Long? = null

    fun onAuthenticated() { authenticatedAt = now(); backgroundAt = null }
    fun lock() { authenticatedAt = null; backgroundAt = null }
    fun onBackground() { backgroundAt = now() }
    fun onForeground() {
        val background = backgroundAt
        if (background != null && now() - background >= 30_000L) lock()
        backgroundAt = null
    }
    fun canAutofill(): Boolean {
        val at = authenticatedAt ?: return false
        return now() - at in 0 until 300_000L && backgroundAt == null
    }
    // Display and copying always require a fresh auth operation in the default policy.
    fun requiresFreshAuthenticationForExport() = true
}
