package com.example.privatebrowser

import android.webkit.WebView
import android.webkit.CookieManager
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors

internal object MediaDetection {
    private val worker = Executors.newFixedThreadPool(2)
    val script = """
        (function(){
          var found=new Map();
          function add(url,video){
            if(!url||!/^(https?:)/i.test(url))return;
            if(!/\.m3u8(?:$|\?)/i.test(url)&&!video)return;
            found.set(url,{url:url,poster:video&&video.poster||'',duration:video&&isFinite(video.duration)?Math.round(video.duration*1000):0,
              width:video&&video.videoWidth||0,height:video&&video.videoHeight||0});
          }
          document.querySelectorAll('video').forEach(function(v){add(v.currentSrc||v.src,v);v.querySelectorAll('source').forEach(function(s){add(s.src,v);});});
          try{performance.getEntriesByType('resource').forEach(function(e){if(/\.m3u8(?:$|\?)/i.test(e.name))add(e.name,null);});}catch(e){}
          return JSON.stringify(Array.from(found.values()).slice(0,50));
        })();
    """.trimIndent()

    fun read(view: WebView, title: String, pageUrl: String, callback: (List<DetectedMedia>) -> Unit) {
        view.evaluateJavascript(script) { encoded ->
            val json = runCatching { org.json.JSONTokener(encoded).nextValue() as String }.getOrDefault("[]")
            val array = runCatching { JSONArray(json) }.getOrNull() ?: JSONArray()
            val result = buildList {
                for (i in 0 until array.length()) array.optJSONObject(i)?.let { o ->
                    val url = o.optString("url")
                    if (url.startsWith("https://") || url.startsWith("http://")) add(DetectedMedia(
                        mediaId(url), url, pageUrl, title, o.optString("poster"), o.optLong("duration"),
                        o.optInt("width"), o.optInt("height"), 0L, view.settings.userAgentString, pageUrl,
                    ))
                }
            }
            callback(result)
            if (result.isNotEmpty()) worker.execute {
                val estimated = result.map { media ->
                    media.copy(estimatedBytes = estimateBytes(media))
                }
                view.post { callback(estimated) }
            }
        }
    }

    private fun estimateBytes(media: DetectedMedia): Long = runCatching {
        if (!media.isHls) {
            val head = open(media.url, "HEAD", media).useConnection { it.contentLengthLong.coerceAtLeast(0L) }
            if (head > 0) return@runCatching head
            return@runCatching open(media.url, "GET", media).apply { setRequestProperty("Range", "bytes=0-0") }
                .useConnection { connection -> connection.getHeaderField("Content-Range")
                    ?.substringAfterLast('/')?.toLongOrNull() ?: connection.contentLengthLong.coerceAtLeast(0L) }
        }
        estimateHls(media.url, media, media.durationMs, 0, 0L)
    }.getOrDefault(0L)

    private fun estimateHls(url: String, media: DetectedMedia, knownDurationMs: Long, depth: Int, assumedBandwidth: Long): Long {
        if (depth > 1) return 0L
        val text = open(url, "GET", media).useConnection { connection ->
            connection.inputStream.bufferedReader().use { it.readText().take(1_000_000) }
        }
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        var bestBandwidth = 0L
        var bestUrl: String? = null
        lines.forEachIndexed { index, line ->
            if (line.startsWith("#EXT-X-STREAM-INF", true)) {
                val bandwidth = Regex("(?:AVERAGE-)?BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val candidate = lines.drop(index + 1).firstOrNull { !it.startsWith('#') }
                if (candidate != null && bandwidth > bestBandwidth) {
                    bestBandwidth = bandwidth
                    bestUrl = URI(url).resolve(candidate).toString()
                }
            }
        }
        if (bestUrl != null) {
            val nested = estimateHls(bestUrl!!, media, knownDurationMs, depth + 1, bestBandwidth)
            if (nested > 0) return nested
            if (knownDurationMs > 0 && bestBandwidth > 0) return bestBandwidth * knownDurationMs / 8_000L
        }
        val byteRanges = lines.filter { it.startsWith("#EXT-X-BYTERANGE:", true) }
            .sumOf { it.substringAfter(':').substringBefore('@').toLongOrNull() ?: 0L }
        if (byteRanges > 0) return byteRanges
        val durationMs = lines.filter { it.startsWith("#EXTINF:", true) }
            .sumOf { ((it.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: 0.0) * 1000).toLong() }
        val bandwidth = bestBandwidth.takeIf { it > 0 } ?: assumedBandwidth
        return if (bandwidth > 0 && durationMs > 0) bandwidth * durationMs / 8_000L else 0L
    }

    private fun open(url: String, method: String, media: DetectedMedia): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = 7_000
            readTimeout = 7_000
            setRequestProperty("User-Agent", media.userAgent)
            setRequestProperty("Referer", media.referer)
            CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
        }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
        try { block(this) } finally { disconnect() }
}
