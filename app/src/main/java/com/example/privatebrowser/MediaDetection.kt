package com.example.privatebrowser

import android.webkit.WebView
import org.json.JSONArray

internal object MediaDetection {
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
        }
    }
}
