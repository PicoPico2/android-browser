package com.example.privatebrowser

import org.junit.Assert.*
import org.junit.Test

class FilterRulesTest {
    @Test fun matchingAndExceptions() {
        val rules = FilterRules.parse(sequenceOf("||ads.example.com^", "@@||safe.ads.example.com^"))
        assertTrue(rules.blocks("https://cdn.ads.example.com/a"))
        assertFalse(rules.blocks("https://safe.ads.example.com/a"))
        assertFalse(rules.blocks("https://notads.example.com/a"))
        assertFalse(rules.blocks("https://ads.example.com.evil.test/a"))
    }
    @Test fun doesNotBroadenUnsupportedRule() {
        val rules = FilterRules.parse(sequenceOf("||example.com^\$redirect=noopjs", "||example.com^\$third-party"))
        assertFalse(rules.blocks("https://example.com/"))
        assertEquals(2, rules.skipped)
    }
    @Test fun scopedCssAndException() {
        val rules = FilterRules.parse(sequenceOf("example.com##.advert", "safe.example.com#@#.advert"))
        assertEquals(setOf(".advert"), rules.selectors("www.example.com"))
        assertTrue(rules.selectors("safe.example.com").isEmpty())
        assertTrue(rules.selectors("another.test").isEmpty())
    }
    @Test fun rejectsExecutableAndAdvancedCosmetics() {
        val rules = FilterRules.parse(sequenceOf("example.com##+js(alert)", "example.com##.a{color:red}"))
        assertTrue(rules.cosmetic.isEmpty())
        assertEquals(2, rules.skipped)
    }
    @Test fun acceptsCaseAndTrailingDot() {
        assertTrue(FilterRules.parse(sequenceOf("||ads.example.com^")).blocks("https://ADS.EXAMPLE.COM./x"))
    }
}
