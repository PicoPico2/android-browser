package com.example.privatebrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserUrlTest {
    @Test fun preservesHttpsUrls() = assertEquals("https://example.com/a", normalizeUrl("https://example.com/a"))
    @Test fun addsSchemeToHost() = assertEquals("https://example.com", normalizeUrl("example.com"))
    @Test fun convertsWordsToSearch() = assertEquals("https://www.google.com/search?q=private%20browser", normalizeUrl("private browser"))
    @Test fun encodesNonAsciiSearchTerms() = assertEquals(
        "https://www.google.com/search?q=%E7%8C%AB%20%E5%8B%95%E7%94%BB",
        normalizeUrl("猫 動画"),
    )

    @Test fun selectsRightNeighbourAfterClosingMiddleTab() = assertEquals(1, replacementIndexAfterClose(1, 2))
    @Test fun selectsPreviousTabAfterClosingLastTab() = assertEquals(1, replacementIndexAfterClose(2, 2))
    @Test fun closingOnlyTabHasSafeIndex() = assertEquals(0, replacementIndexAfterClose(0, 0))
}
