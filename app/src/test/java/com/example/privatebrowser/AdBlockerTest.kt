package com.example.privatebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {
    @Test fun offNeverBlocks() = assertFalse(
        LocalRequestBlocker.shouldBlock("https://adsrvr.org/ad.js", BlockingLevel.OFF),
    )

    @Test fun standardBlocksKnownAdvertisingDomain() = assertTrue(
        LocalRequestBlocker.shouldBlock("https://pixel.adsrvr.org/track", BlockingLevel.STANDARD),
    )

    @Test fun strictIncludesBroaderList() = assertTrue(
        LocalRequestBlocker.shouldBlock("https://ib.adnxs.com/banner", BlockingLevel.STRICT),
    )

    @Test fun standardDoesNotUseStrictOnlyRules() = assertFalse(
        LocalRequestBlocker.shouldBlock("https://ib.adnxs.com/banner", BlockingLevel.STANDARD),
    )

    @Test fun captchaAndGoogleResourcesAreNeverBlocked() {
        assertFalse(LocalRequestBlocker.shouldBlock("https://www.google.com/recaptcha/api.js", BlockingLevel.STRICT))
        assertFalse(LocalRequestBlocker.shouldBlock("https://cdn.example.com/captcha/challenge", BlockingLevel.STRICT))
    }

    @Test fun domainMatchingDoesNotAcceptLookalikes() = assertFalse(
        LocalRequestBlocker.shouldBlock("https://notadsrvr.org/content", BlockingLevel.STRICT),
    )
}
