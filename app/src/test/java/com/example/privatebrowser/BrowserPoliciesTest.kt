package com.example.privatebrowser

import org.junit.Assert.*
import org.junit.Test

class BrowserPoliciesTest {
    @Test fun vaultExpiryAndBackground() {
        var now = 0L
        val policy = VaultSessionPolicy { now }
        assertFalse(policy.canAutofill())
        policy.onAuthenticated()
        assertTrue(policy.canAutofill())
        assertTrue(policy.requiresFreshAuthenticationForExport())
        now = 300_000L
        assertFalse(policy.canAutofill())
        policy.onAuthenticated()
        policy.onBackground()
        assertFalse(policy.canAutofill())
        now += 30_000L
        policy.onForeground()
        assertFalse(policy.canAutofill())
    }
    @Test fun lockImmediatelyRevokesSession() {
        val policy = VaultSessionPolicy { 0L }
        policy.onAuthenticated(); policy.lock()
        assertFalse(policy.canAutofill())
    }
    @Test fun shortBackgroundPreservesRemainingSession() {
        var now = 0L
        val policy = VaultSessionPolicy { now }
        policy.onAuthenticated(); policy.onBackground()
        now = 1000L; policy.onForeground()
        assertTrue(policy.canAutofill())
    }
    @Test fun retireByLastUseAndProtectWork() {
        val tabs = listOf(RetainedTab(1, 10), RetainedTab(2, 5, pinned = true), RetainedTab(3, 1), RetainedTab(4, 0, active = true))
        assertEquals(listOf(3L, 1L), tabsToRetire(tabs, 2))
    }
    @Test fun neverRetirePlayingOrDirtyTabs() {
        assertTrue(tabsToRetire(listOf(RetainedTab(1, 0, playing = true), RetainedTab(2, 0, dirtyForm = true)), 1).isEmpty())
    }
}
