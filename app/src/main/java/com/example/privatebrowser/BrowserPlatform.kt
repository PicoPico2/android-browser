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
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var fullView: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null
    private var overlay: FrameLayout? = null
    private var oldStatusVisible = true
    private var oldNavigationVisible = true
    private var oldBehavior = 0
    private val recentFallbacks = java.util.WeakHashMap<WebView, Pair<Long, Int>>()
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
        return true
    }

    fun handleNavigation(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) return false
        if (uri.scheme.equals("about", true)) return false
        if (!request.isForMainFrame) return true
        if (uri.scheme.equals("intent", true)) {
            val fallback = runCatching {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    .getStringExtra("browser_fallback_url")
            }.getOrNull()
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

    fun installLinkMenu(view: WebView) {
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
