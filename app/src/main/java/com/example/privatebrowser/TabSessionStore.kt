package com.example.privatebrowser

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class SavedTab(val id: Long, val url: String, val title: String)
internal data class TabSession(val tabs: List<SavedTab>, val selected: Long, val closed: List<SavedTab>)

/** Profile-scoped atomic snapshots. No cookies or passwords in this file. */
internal class TabSessionStore(context: Context, profileId: String, windowId: String = "primary") {
    private val windowSuffix = BrowserActivity.profileSuffix(windowId)
    private val file = AtomicFile(File(context.filesDir, "tabs_${BrowserActivity.profileSuffix(profileId)}_$windowSuffix.json"))
    private val worker = Executors.newSingleThreadExecutor()
    private var lastQueued = ""
    @Volatile var error: String? = null
        private set
    fun read(): TabSession = runCatching {
        file.openRead().bufferedReader().use { reader ->
            val json = JSONObject(reader.readText())
            TabSession(decode(json.optJSONArray("tabs")), json.optLong("selected", 1), decode(json.optJSONArray("closed")))
        }
    }.getOrElse { TabSession(emptyList(), 1, emptyList()) }
    private fun decode(array: JSONArray?): List<SavedTab> = buildList {
        if (array != null) for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (FilterRules.hostOf(url).isNotEmpty()) add(SavedTab(item.optLong("id", i + 1L), url, item.optString("title")))
        }
    }.distinctBy { it.id }
    private fun encode(tabs: List<SavedTab>) = JSONArray().apply {
        tabs.forEach { put(JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)) }
    }
    fun save(session: TabSession) {
        val text = JSONObject().put("tabs", encode(session.tabs)).put("selected", session.selected)
            .put("closed", encode(session.closed.takeLast(30))).toString()
        if (text == lastQueued && error == null) return
        lastQueued = text
        worker.execute {
            runCatching {
                val output = file.startWrite()
                try { output.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
                catch (e: Exception) { file.failWrite(output); throw e }
            }.onSuccess { error = null }.onFailure { error = "タブの保存に失敗しました" }
        }
    }
    fun flush() { runCatching { worker.submit {}.get(3, TimeUnit.SECONDS) }.onFailure { error = "タブ保存が完了していません" } }
    fun dispose() { flush(); worker.shutdown() }
}
