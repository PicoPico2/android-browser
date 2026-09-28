package com.example.privatebrowser

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

internal enum class HlsDownloadStatus { QUEUED, RUNNING, COMPLETE, FAILED }

internal data class HlsDownload(
    val id: String,
    val title: String,
    val url: String,
    val userAgent: String,
    val referer: String,
    val status: HlsDownloadStatus,
    val error: String = "",
) {
    val statusLabel: String get() = when (status) {
        HlsDownloadStatus.QUEUED -> "待機中"
        HlsDownloadStatus.RUNNING -> "保存中"
        HlsDownloadStatus.COMPLETE -> "完了"
        HlsDownloadStatus.FAILED -> if (error.isBlank()) "失敗" else "失敗: $error"
    }

    fun toServiceIntent(context: Context, profileId: String) = Intent(context, HlsExportService::class.java).apply {
        putExtra(HlsExportService.EXTRA_JOB_ID, id)
        putExtra(HlsExportService.EXTRA_PROFILE_ID, profileId)
        putExtra(HlsExportService.EXTRA_URL, url)
        putExtra(HlsExportService.EXTRA_TITLE, title)
        putExtra(HlsExportService.EXTRA_USER_AGENT, userAgent)
        putExtra(HlsExportService.EXTRA_REFERER, referer)
    }
}

internal class HlsDownloadRepository(context: Context, profileId: String) {
    private val preferences = context.getSharedPreferences(
        "hls_downloads_${BrowserActivity.profileSuffix(profileId)}", Context.MODE_PRIVATE,
    )

    fun create(media: DetectedMedia): HlsDownload {
        val job = HlsDownload(java.util.UUID.randomUUID().toString(), media.title, media.url, media.userAgent, media.referer, HlsDownloadStatus.QUEUED)
        write(listOf(job) + list())
        return job
    }

    fun list(): List<HlsDownload> = runCatching {
        val array = JSONArray(preferences.getString("jobs", "[]"))
        buildList {
            for (i in 0 until array.length()) array.optJSONObject(i)?.let { o ->
                val url = o.optString("url")
                if (url.startsWith("https://") || url.startsWith("http://")) add(HlsDownload(
                    o.optString("id"), o.optString("title"), url, o.optString("ua"), o.optString("referer"),
                    runCatching { HlsDownloadStatus.valueOf(o.optString("status")) }.getOrDefault(HlsDownloadStatus.FAILED),
                    o.optString("error"),
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun update(id: String, status: HlsDownloadStatus, error: String = "") = write(list().map {
        if (it.id == id) it.copy(status = status, error = error.take(160)) else it
    })
    fun markQueued(id: String) = update(id, HlsDownloadStatus.QUEUED)
    fun remove(id: String) = write(list().filterNot { it.id == id })

    private fun write(items: List<HlsDownload>) {
        val array = JSONArray()
        items.take(200).forEach { item -> array.put(JSONObject()
            .put("id", item.id).put("title", item.title).put("url", item.url).put("ua", item.userAgent)
            .put("referer", item.referer).put("status", item.status.name).put("error", item.error)) }
        preferences.edit().putString("jobs", array.toString()).apply()
    }
}
