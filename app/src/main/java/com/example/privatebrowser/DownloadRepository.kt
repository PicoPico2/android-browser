package com.example.privatebrowser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil

internal data class BrowserDownload(
    val id: Long, val name: String, val status: Int, val downloaded: Long, val total: Long,
)

/** Profile-scoped history and retry metadata for ordinary HTTP(S) downloads. */
internal class DownloadRepository(private val context: Context, profileId: String) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = context.getSharedPreferences(
        "browser_downloads_${BrowserActivity.profileSuffix(profileId)}", Context.MODE_PRIVATE,
    )

    fun enqueue(url: String, userAgent: String, contentDisposition: String?, mimeType: String?, referer: String?): Long? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        val name = availableName(URLUtil.guessFileName(url, contentDisposition, mimeType))
        val request = DownloadManager.Request(uri)
            .setTitle(name)
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie", it) }
        referer?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { request.addRequestHeader("Referer", it) }
        return runCatching { manager.enqueue(request) }.getOrNull()?.also { id ->
            val ids = trackedIds().toMutableSet().apply { add(id) }
            preferences.edit().putStringSet("ids", ids.map(Long::toString).toSet()).apply()
            preferences.edit().putString("request_$id", org.json.JSONObject()
                .put("url", url).put("ua", userAgent).put("cd", contentDisposition)
                .put("mime", mimeType).put("referer", referer).toString()).apply()
        }
    }

    fun list(): List<BrowserDownload> {
        val ids = trackedIds()
        if (ids.isEmpty()) return emptyList()
        val cursor = runCatching { manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray())) }.getOrNull() ?: return emptyList()
        return cursor.use { rows -> buildList { while (rows.moveToNext()) add(rows.toDownload()) } }.sortedByDescending { it.id }
    }

    fun cancel(id: Long) { manager.remove(id); forget(id) }
    fun forget(id: Long) {
        preferences.edit().putStringSet("ids", trackedIds().filterNot { it == id }.map(Long::toString).toSet()).remove("request_$id").apply()
    }
    fun retry(id: Long): Long? {
        val saved = preferences.getString("request_$id", null) ?: return null
        val o = runCatching { org.json.JSONObject(saved) }.getOrNull() ?: return null
        return enqueue(o.optString("url"), o.optString("ua"), o.optString("cd"), o.optString("mime"), o.optString("referer"))
    }
    fun open(id: Long): Boolean {
        val uri = manager.getUriForDownloadedFile(id) ?: return false
        val type = manager.getMimeTypeForDownloadedFile(id) ?: "*/*"
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
    private fun trackedIds(): Set<Long> = preferences.getStringSet("ids", emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()
    private fun availableName(name: String): String {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!java.io.File(directory, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (java.io.File(directory, "$base ($index)$extension").exists()) index++
        return "$base ($index)$extension"
    }
    private fun Cursor.toDownload() = BrowserDownload(
        getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
        getString(getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty(),
        getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
        getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
        getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
    )
}
