package com.example.privatebrowser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Activity-owned callbacks: never grant arbitrary web permissions or launch external intents. */
internal class BrowserPlatform(private val activity: ComponentActivity) {
    var altDown = false
    var shiftDown = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var fullView: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null
    private var overlay: FrameLayout? = null
    private var oldStatusVisible = true
    private var oldNavigationVisible = true
    private var oldBehavior = 0
    private val desktopViews = java.util.WeakHashMap<WebView, String>()
    private val imageSearchViews = java.util.WeakHashMap<WebView, String>()
    private val imageOverview = java.util.WeakHashMap<WebView, Boolean>()
    private val resourceErrors = java.util.WeakHashMap<WebView, MutableList<String>>()
    private val recentFallbacks = java.util.WeakHashMap<WebView, Pair<Long, Int>>()
    var rendererLost: ((WebView) -> Unit)? = null
    var openLink: ((String, Boolean) -> Unit)? = null
    val isFullscreen: Boolean get() = fullView != null

    private val picker = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback
        fileCallback = null
        val selected = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            ?.filter { uri ->
                uri.scheme == "content" &&
                    android.content.pm.PackageManager.PERMISSION_GRANTED == activity.checkUriPermission(
                        uri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
            }?.toTypedArray()?.takeIf { it.isNotEmpty() }
        callback?.onReceiveValue(selected)
    }

    fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        try {
            // The system picker grants access only to the explicitly selected documents.
            picker.launch(params.createIntent().apply { addCategory(Intent.CATEGORY_OPENABLE) })
        } catch (_: RuntimeException) {
            fileCallback = null
            callback.onReceiveValue(null)
            message("ファイル選択アプリを開けません")
        }
        return true
    }

    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (isFullscreen) { callback.onCustomViewHidden(); return }
        val decor = activity.window.decorView as ViewGroup
        val insets = ViewCompat.getRootWindowInsets(decor)
        oldStatusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        oldNavigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val controller = WindowCompat.getInsetsController(activity.window, decor)
        oldBehavior = controller.systemBarsBehavior
        fullView = view
        fullCallback = callback
        overlay = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            addView(view, FrameLayout.LayoutParams(-1, -1))
            decor.addView(this, ViewGroup.LayoutParams(-1, -1))
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.requestFocus()
    }

    fun hideFullscreen(): Boolean {
        if (!isFullscreen) return false
        val callback = fullCallback
        fullCallback = null
        fullView = null
        overlay?.removeAllViews()
        (overlay?.parent as? ViewGroup)?.removeView(overlay)
        overlay = null
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = oldBehavior
        if (oldStatusVisible) controller.show(WindowInsetsCompat.Type.statusBars())
        else controller.hide(WindowInsetsCompat.Type.statusBars())
        if (oldNavigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
        else controller.hide(WindowInsetsCompat.Type.navigationBars())
        callback?.onCustomViewHidden()
        activity.window.decorView.requestLayout()
        activity.window.decorView.invalidate()
        return true
    }

    fun handleNavigation(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
            if (request.isForMainFrame && (imageSearchViews.containsKey(view) || isImageSearchContext(view.url.orEmpty())) && isAppDownloadUrl(uri.toString())) {
                if (!imageSearchViews.containsKey(view)) openImageSearch(view)
                else message("アプリ案内への移動を停止しました。Web版の画像検索を使ってください")
                return true
            }
            if (request.isForMainFrame && imageSearchViews.containsKey(view) && !isGoogleWebHost(uri.host.orEmpty())) {
                imageSearchViews.remove(view)?.let { view.settings.userAgentString = it }
                imageOverview.remove(view)?.let { view.settings.loadWithOverviewMode = it }
            }
            return false
        }
        if (uri.scheme.equals("about", true)) return false
        if (!request.isForMainFrame) return true
        if (uri.scheme.equals("intent", true)) {
            val parsed = runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
            val fallback = parsed?.getStringExtra("browser_fallback_url")
            val googleApp = parsed?.`package` in setOf("com.google.android.googlequicksearchbox", "com.google.ar.lens") ||
                uri.host == "search.app.goo.gl"
            if (googleApp && (isImageSearchContext(view.url.orEmpty()) || fallback == null || isAppDownloadUrl(fallback))) {
                if (imageSearchViews.containsKey(view)) message("アプリへの誘導を停止しました。Web版のカメラボタンを選んでください")
                else openImageSearch(view)
                return true
            }
            if (fallback != null && isAppDownloadUrl(fallback)) {
                message("アプリのダウンロードページへの移動を停止しました")
                return true
            }
            // parseUri already decodes extras. Do not URL-decode a second time.
            if (fallback != null && safeHttpsFallback(fallback)) {
                val now = android.os.SystemClock.elapsedRealtime()
                val previous = recentFallbacks[view]
                val count = if (previous != null && now - previous.first < 5000L) previous.second + 1 else 1
                recentFallbacks[view] = now to count
                if (count <= 3) view.loadUrl(fallback)
                else message("アプリ誘導の繰り返しを停止しました。別のWeb版リンクを開いてください")
                return true
            }
        }
        message("外部アプリへの移動を停止しました。Web版のリンクを使用してください")
        return true
    }

    private fun desktopAgent(original: String): String = original
        .replaceFirst(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
        .replace("; wv", "").replace(" Version/4.0", "").replace(" Mobile", "")

    fun toggleDesktop(view: WebView) {
        hideFullscreen()
        imageSearchViews.remove(view)?.let { view.settings.userAgentString = it }
        imageOverview.remove(view)?.let { view.settings.loadWithOverviewMode = it }
        val original = desktopViews.remove(view)
        if (original != null) {
            view.settings.userAgentString = original
            view.settings.loadWithOverviewMode = false
            message("モバイル表示")
        } else {
            desktopViews[view] = view.settings.userAgentString
            view.settings.userAgentString = desktopAgent(view.settings.userAgentString)
            view.settings.loadWithOverviewMode = true
            message("PC表示。このタブだけに適用します")
        }
        view.requestLayout()
        view.reload()
    }

    fun openImageSearch(view: WebView) {
        hideFullscreen()
        if (!imageSearchViews.containsKey(view)) {
            imageSearchViews[view] = view.settings.userAgentString
            imageOverview[view] = view.settings.loadWithOverviewMode
        }
        view.settings.loadWithOverviewMode = true
        view.settings.userAgentString = desktopAgent(view.settings.userAgentString)
        view.loadUrl("https://www.google.com/imghp?hl=ja")
        message("カメラアイコンから「ファイルをアップロード」を選んでください")
    }

    fun openNicoWatchPage(view: WebView) {
        val uri = Uri.parse(view.url.orEmpty())
        val host = uri.host.orEmpty()
        if (!FilterRules.domainMatches(host, "nicovideo.jp")) { message("ニコ動の動画ページで使ってください"); return }
        val id = uri.pathSegments.lastOrNull().orEmpty()
        if (!Regex("(?:sm|so|nm)?[0-9]+").matches(id)) { message("動画IDを確認できません"); return }
        hideFullscreen()
        view.loadUrl("https://www.nicovideo.jp/watch/$id")
    }

    fun noteError(view: WebView, request: WebResourceRequest, code: Int) {
        val errors = resourceErrors.getOrPut(view) { mutableListOf() }
        errors.add("${request.url.host.orEmpty()}: $code (${if (request.isForMainFrame) "ページ" else "リソース"})")
        while (errors.size > 20) errors.removeAt(0)
    }
    fun showDiagnostics(view: WebView) {
        view.evaluateJavascript("""
            (function(){return JSON.stringify({
                host:location.hostname,path:location.pathname,
                viewport:[innerWidth,innerHeight],
                documentSize:[document.documentElement.scrollWidth,document.documentElement.scrollHeight],
                ready:document.readyState,fullscreen:!!document.fullscreenElement,
                bodyOverflow:document.body?getComputedStyle(document.body).overflow:null,
                videos:Array.from(document.querySelectorAll('video')).map(function(v){return {
                    ready:v.readyState,network:v.networkState,error:v.error?v.error.code:null,paused:v.paused
                }})
            });})()
        """.trimIndent()) { result ->
            if (activity.isFinishing || activity.isDestroyed) return@evaluateJavascript
            val text = "WebView: " + WebView.getCurrentWebViewPackage()?.versionName +
                "\nUA: " + view.settings.userAgentString + "\n" + result +
                "\n読み込みエラー（ホストとコードのみ）:\n" + resourceErrors[view].orEmpty().joinToString("\n")
            android.app.AlertDialog.Builder(activity).setTitle("表示診断").setMessage(text)
                .setPositiveButton("コピー") { _, _ ->
                    (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("表示診断", text))
                }.setNegativeButton("閉じる", null).show()
        }
    }

    fun installLinkMenu(view: WebView) {
        var altGesture = false
        var shiftGesture = false
        var downX = 0f
        var downY = 0f
        var moved = false
        val slop = android.view.ViewConfiguration.get(activity).scaledTouchSlop
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    altGesture = altDown || event.metaState and android.view.KeyEvent.META_ALT_ON != 0
                    shiftGesture = shiftDown || event.metaState and android.view.KeyEvent.META_SHIFT_ON != 0
                    downX = event.x; downY = event.y; moved = false
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) moved = true
                    false
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val hit = view.hitTestResult
                    val isLink = hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                    val intercept = altGesture && !moved && isLink
                    altGesture = false
                    if (intercept) {
                        val foreground = shiftGesture
                        val message = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
                            val url = msg.data.getString("url") ?: if (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) hit.extra else null
                            if (url != null && FilterRules.hostOf(url).isNotEmpty()) openLink?.invoke(url, foreground)
                            true
                        }.obtainMessage()
                        view.requestFocusNodeHref(message)
                        val cancel = android.view.MotionEvent.obtain(event)
                        cancel.action = android.view.MotionEvent.ACTION_CANCEL
                        view.onTouchEvent(cancel)
                        cancel.recycle()
                    }
                    intercept
                }
                android.view.MotionEvent.ACTION_CANCEL -> { altGesture = false; false }
                else -> false
            }
        }
        view.setOnLongClickListener {
            val hit = view.hitTestResult
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    hit.extra?.let { showLinkMenu(it) }
                    hit.extra != null
                }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    // For a linked image hit.extra is the image URL, not necessarily the anchor.
                    val message = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
                        msg.data.getString("url")?.let { showLinkMenu(it) }
                        true
                    }.obtainMessage()
                    view.requestFocusNodeHref(message)
                    true
                }
                else -> false // Preserve text selection and the website's own interactions.
            }
        }
    }

    private fun showLinkMenu(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") return
        android.app.AlertDialog.Builder(activity)
            .setTitle("リンク操作")
            .setItems(arrayOf("新しいタブで開く", "バックグラウンドで開く", "リンクをコピー", "共有")) { _, item ->
                when (item) {
                    0 -> openLink?.invoke(url, true)
                    1 -> openLink?.invoke(url, false)
                    2 -> (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("リンク", url))
                    3 -> activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }, "リンクを共有"))
                }
            }.show()
    }

    private fun message(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    fun dispose() {
        rendererLost = null
        openLink = null
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        hideFullscreen()
    }
}

internal fun safeHttpsFallback(url: String): Boolean = runCatching {
    val uri = java.net.URI(url)
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

internal fun isGoogleWebHost(host: String): Boolean = listOf("google.com", "google.co.jp").any { FilterRules.domainMatches(host.lowercase(), it) }
internal fun isAppDownloadUrl(url: String): Boolean = runCatching {
    val uri = java.net.URI(url)
    val host = uri.host.orEmpty().lowercase()
    host == "play.google.com" || host == "apps.apple.com" || host == "itunes.apple.com" ||
        (host == "google.com" || host == "www.google.com") && uri.path.orEmpty().startsWith("/intl/") && uri.path.orEmpty().contains("/search/about")
}.getOrDefault(false)

internal fun isImageSearchContext(url: String): Boolean = runCatching {
    val uri = java.net.URI(url)
    val host = uri.host.orEmpty().lowercase()
    val query = uri.rawQuery.orEmpty().split('&')
    host == "lens.google.com" || isGoogleWebHost(host) &&
        (uri.path.orEmpty().startsWith("/imghp") || "tbm=isch" in query || "udm=2" in query)
}.getOrDefault(false)
