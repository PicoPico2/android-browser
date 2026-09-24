package com.example.privatebrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class DetectedMedia(
    val id: String,
    val url: String,
    val pageUrl: String,
    val title: String,
    val posterUrl: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val estimatedBytes: Long,
    val userAgent: String,
    val referer: String,
) {
    val isHls: Boolean get() = url.substringBefore('?').endsWith(".m3u8", true)
    val quality: String get() = if (height > 0) "${height}p" else "自動"
}

/** Profile-scoped stream tiles. Authentication cookies stay in WebView CookieManager. */
internal class MediaCatalog(context: Context, profileId: String) {
    private val preferences = context.getSharedPreferences(
        "media_${BrowserActivity.profileSuffix(profileId)}", Context.MODE_PRIVATE,
    )

    fun list(): List<DetectedMedia> = decode(preferences.getString("tiles", "[]").orEmpty())

    fun add(media: DetectedMedia) {
        val items = list().filterNot { it.id == media.id }.toMutableList().apply { add(0, media) }.take(200)
        preferences.edit().putString("tiles", encode(items)).apply()
    }

    fun remove(ids: Set<String>) {
        preferences.edit().putString("tiles", encode(list().filterNot { it.id in ids })).apply()
    }

    fun position(id: String): Long = preferences.getLong("position_$id", 0L)
    fun savePosition(id: String, positionMs: Long) = preferences.edit().putLong("position_$id", positionMs).apply()

    private fun encode(items: List<DetectedMedia>) = JSONArray().apply {
        items.forEach { m -> put(JSONObject()
            .put("id", m.id).put("url", m.url).put("page", m.pageUrl).put("title", m.title)
            .put("poster", m.posterUrl).put("duration", m.durationMs).put("width", m.width)
            .put("height", m.height).put("size", m.estimatedBytes).put("ua", m.userAgent).put("referer", m.referer)) }
    }.toString()

    private fun decode(text: String): List<DetectedMedia> = runCatching {
        val array = JSONArray(text)
        buildList {
            for (i in 0 until array.length()) array.optJSONObject(i)?.let { o ->
                val url = o.optString("url")
                if (url.startsWith("https://") || url.startsWith("http://")) add(DetectedMedia(
                    o.optString("id"), url, o.optString("page"), o.optString("title"), o.optString("poster"),
                    o.optLong("duration"), o.optInt("width"), o.optInt("height"), o.optLong("size"),
                    o.optString("ua"), o.optString("referer"),
                ))
            }
        }
    }.getOrDefault(emptyList())
}

internal fun mediaId(url: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
