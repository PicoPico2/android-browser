package com.example.privatebrowser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Executors

/** No browsing URLs or cookies are sent to the filter provider. */
internal class FilterStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "easylist-subset.txt"))
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var rules = FilterRules()
        private set
    @Volatile var status = "未更新（同梱の基本ルールのみ）"
        private set

    init {
        worker.execute {
            runCatching {
                if (file.baseFile.exists()) file.openRead().bufferedReader().use { reader ->
                    rules = FilterRules.parse(reader.lineSequence())
                    status = summary()
                }
            }
        }
    }

    fun summary() = "ホスト ${rules.blockedHosts.size} / 非対応ルール ${rules.skipped}（限定構文）"

    fun update(done: (String) -> Unit) {
        worker.execute {
            val result = runCatching {
                val connection = URL("https://easylist.to/easylist/easylist.txt").openConnection() as HttpsURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false
                val bytes = try {
                    check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                    connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 12 * 1024 * 1024) { "リストが大きすぎます" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                } finally { connection.disconnect() }
                val content = bytes.toString(Charsets.UTF_8)
                check(content.trimStart().startsWith("[Adblock")) { "フィルター形式ではありません" }
                val parsed = FilterRules.parse(content.lineSequence())
                check(parsed.blockedHosts.isNotEmpty()) { "対応するルールがありません" }
                val output = file.startWrite()
                try { output.write(bytes); file.finishWrite(output) }
                catch (e: Exception) { file.failWrite(output); throw e }
                rules = parsed
                status = summary()
                status
            }.getOrElse { "更新失敗。前のルールを維持します" }
            main.post { done(result) }
        }
    }

    fun dispose() { worker.shutdownNow() }
}
