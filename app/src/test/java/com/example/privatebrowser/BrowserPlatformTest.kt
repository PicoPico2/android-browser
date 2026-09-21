package com.example.privatebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPlatformTest {
    @Test fun acceptsHttpsFallback() = assertTrue(safeHttpsFallback("https://example.com/?q=a%2Fb"))
    @Test fun rejectsJavascript() = assertFalse(safeHttpsFallback("javascript:alert(1)"))
    @Test fun rejectsLocalFile() = assertFalse(safeHttpsFallback("file:///sdcard/private.txt"))
    @Test fun rejectsContentUri() = assertFalse(safeHttpsFallback("content://documents/1"))
    @Test fun rejectsIntentLoop() = assertFalse(safeHttpsFallback("intent://example.com"))
    @Test fun rejectsCleartext() = assertFalse(safeHttpsFallback("http://example.com"))
    @Test fun rejectsCredentials() = assertFalse(safeHttpsFallback("https://user:password@example.com"))
    @Test fun rejectsMissingHost() = assertFalse(safeHttpsFallback("https:///path"))
}
