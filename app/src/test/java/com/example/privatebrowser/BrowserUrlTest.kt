package com.example.privatebrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserUrlTest {
    @Test fun preservesHttpsUrls() = assertEquals("https://example.com/a", normalizeUrl("https://example.com/a"))
    @Test fun addsSchemeToHost() = assertEquals("https://example.com", normalizeUrl("example.com"))
    @Test fun convertsWordsToSearch() = assertEquals("https://www.google.com/search?q=private%20browser", normalizeUrl("private browser"))
}
