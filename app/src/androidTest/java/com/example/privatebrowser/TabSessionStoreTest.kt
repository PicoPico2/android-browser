package com.example.privatebrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabSessionStoreTest {
    @Test fun persistsOrderSelectionClosedTabsAndProfileIsolation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstId = "session-test-" + java.util.UUID.randomUUID()
        val secondId = "session-test-" + java.util.UUID.randomUUID()
        val first = TabSessionStore(context, firstId)
        val second = TabSessionStore(context, secondId)
        try {
            val tabs = listOf(SavedTab(8, "https://example.com/a", "A"), SavedTab(3, "https://example.com/b", "B"))
            val session = TabSession(tabs, 3, listOf(SavedTab(2, "https://example.com/closed", "Closed")))
            first.save(session); first.flush()
            assertEquals(session, first.read())
            assertTrue(second.read().tabs.isEmpty())
            first.save(session.copy(selected = 8)); first.flush()
            assertEquals(8L, first.read().selected)
            assertNull(first.error)
        } finally {
            first.dispose(); second.dispose()
            listOf(firstId, secondId).forEach { id ->
                val file = java.io.File(context.filesDir, "tabs_${BrowserActivity.profileSuffix(id)}.json")
                android.util.AtomicFile(file).delete()
            }
        }
    }
}
