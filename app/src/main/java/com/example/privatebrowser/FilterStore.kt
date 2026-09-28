package com.example.privatebrowser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Executors
import org.json.JSONObject

internal data class FilterSnapshot(val base: FilterRules = FilterRules(), val tracking: FilterRules = FilterRules(), val updatedAt: Long = 0)

/** Lists are data only. Publish one immutable snapshot after all sources succeed. */
internal class FilterStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "filter-snapshot-v3.json"))
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var snapshot = FilterSnapshot()
        private set
    val rules: FilterRules get() = snapshot.base
    @Volatile var status = "未更新（同梱ルールのみ）"
        private set
    @Volatile private var disposed = false
    private val sources = linkedMapOf(
        "base" to "https://easylist.to/easylist/easylist.txt",
        "japanese" to "https://filters.adtidy.org/extension/chromium/filters/7.txt",
        "privacy" to "https://easylist.to/easylist/easyprivacy.txt",
        "dns" to "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
    )
    init {
        worker.execute {
            runCatching { file.openRead().bufferedReader().use { install(JSONObject(it.readText())) } }
            if (System.currentTimeMillis() - snapshot.updatedAt > 7L * 24 * 60 * 60 * 1000) refresh()
        }
    }
    private fun parsed(json: JSONObject): FilterSnapshot = FilterSnapshot(
        FilterRules.parse((json.getString("base") + "\n" + json.getString("japanese")).lineSequence()),
        FilterRules.parse((json.getString("privacy") + "\n" + json.getString("dns")).lineSequence()),
        json.getLong("updatedAt"),
    )
    private fun install(json: JSONObject) { snapshot = parsed(json); status = summary() }
    fun summary(): String {
        val current = snapshot
        val time = if (current.updatedAt == 0L) "未更新" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(current.updatedAt))
        return "標準 ${current.base.network.size()} / 強力追加 ${current.tracking.network.size()} 通信ルール\n" +
            "非対応 ${current.base.skipped + current.tracking.skipped} / 更新 $time"
    }
    fun decision(url: String, pageHost: String, type: String, level: BlockingLevel): Int {
        if (level == BlockingLevel.OFF) return 0
        val current = snapshot
        val base = current.base.decision(url, pageHost, type)
        val privacy = if (level == BlockingLevel.STRICT) current.tracking.decision(url, pageHost, type) else 0
        return if (base == -1 || privacy == -1) -1 else if (base == 1 || privacy == 1) 1 else 0
    }
    fun update(done: (String) -> Unit) {
        if (disposed) return
        worker.execute { refresh(); if (!disposed) main.post { if (!disposed) done(status) } }
    }
    private fun refresh() {
        status = "フィルター更新中"
        runCatching {
            val json = JSONObject()
            sources.forEach { (name, url) -> json.put(name, download(url)) }
            json.put("updatedAt", System.currentTimeMillis())
            val candidate = parsed(json)
            check(candidate.base.network.size() > 0 && candidate.tracking.network.size() > 0)
            val output = file.startWrite()
            try { output.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (e: Exception) { file.failWrite(output); throw e }
            snapshot = candidate
            status = summary()
        }.onFailure { status = "更新失敗。前のルールを維持します\n" + summary() }
    }
    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 15000
        connection.instanceFollowRedirects = false
        return try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 12 * 1024 * 1024)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            bytes.toString(Charsets.UTF_8).also {
                check(it.trimStart().startsWith("[Adblock") || it.trimStart().startsWith("!"))
                check(!it.contains("<html", ignoreCase = true))
            }
        } finally { connection.disconnect() }
    }
    fun dispose() { disposed = true; worker.shutdownNow() }
}
