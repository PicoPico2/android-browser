package com.example.privatebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPlatformTest {
    @Test fun imageSearchContext() = assertTrue(isImageSearchContext("https://www.google.com/search?q=cat&udm=2"))
    @Test fun unrelatedSearchContext() = assertFalse(isImageSearchContext("https://www.google.com/search?q=cat"))
    @Test fun fakeImageHost() = assertFalse(isImageSearchContext("https://www.google.com.evil.test/imghp"))
    @Test fun recognizesAppStore() = assertTrue(isAppDownloadUrl("https://play.google.com/store/apps/details?id=test"))
    @Test fun doesNotMatchFakeStoreHost() = assertFalse(isAppDownloadUrl("https://play.google.com.evil.test/store"))
    @Test fun acceptsImageSearchWeb() = assertFalse(isAppDownloadUrl("https://www.google.com/imghp"))
    @Test fun googleHostBoundary() = assertFalse(isGoogleWebHost("google.com.evil.test"))
    @Test fun acceptsHttpsFallback() = assertTrue(safeHttpsFallback("https://example.com/?q=a%2Fb"))
    @Test fun rejectsJavascript() = assertFalse(safeHttpsFallback("javascript:alert(1)"))
    @Test fun rejectsLocalFile() = assertFalse(safeHttpsFallback("file:///sdcard/private.txt"))
    @Test fun rejectsContentUri() = assertFalse(safeHttpsFallback("content://documents/1"))
    @Test fun rejectsIntentLoop() = assertFalse(safeHttpsFallback("intent://example.com"))
    @Test fun rejectsCleartext() = assertFalse(safeHttpsFallback("http://example.com"))
    @Test fun rejectsCredentials() = assertFalse(safeHttpsFallback("https://user:password@example.com"))
    @Test fun rejectsMissingHost() = assertFalse(safeHttpsFallback("https:///path"))
}
