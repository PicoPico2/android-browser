package com.example.privatebrowser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder
import java.security.MessageDigest

private const val HOME_URL = "https://www.google.com"

private class BrowserTab(val id: Long, val webView: WebView) {
    var title by mutableStateOf("新しいタブ")
    var url by mutableStateOf(HOME_URL)
    var progress by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    fun destroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.onPause()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.removeAllViews()
        webView.destroy()
    }
}

class BrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val profileId = requireNotNull(intent.getStringExtra(EXTRA_PROFILE_ID))
        val suffix = profileSuffix(profileId)
        if (configuredSuffix == null) {
            WebView.setDataDirectorySuffix(suffix)
            configuredSuffix = suffix
        }
        check(configuredSuffix == suffix) { "A browser process cannot switch profiles" }
        super.onCreate(savedInstanceState)
        setContent {
            PrivateBrowserTheme {
                BrowserScreen(intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty()) { finishAndRemoveTask() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // A profile owns this dedicated process. End it only when explicitly leaving the profile;
        // configuration changes are handled by Compose disposal without killing the new Activity.
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"
        @Volatile private var configuredSuffix: String? = null

        internal fun profileSuffix(id: String): String = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }
}

@Composable
private fun BrowserScreen(profileName: String, closeProfile: () -> Unit) {
    val context = LocalContext.current
    val tabs = remember { mutableStateListOf(createWebView(context, 1L)) }
    var selectedId by remember { mutableStateOf<Long?>(1L) }
    var addressInput by remember { mutableStateOf(HOME_URL) }
    var nextId by remember { mutableStateOf(2L) }

    fun addTab() {
        tabs.firstOrNull { it.id == selectedId }?.webView?.onPause()
        val tab = createWebView(context, nextId++)
        tabs += tab
        selectedId = tab.id
        addressInput = HOME_URL
    }

    val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val current = tabs[selectedIndex]

    fun select(tab: BrowserTab) {
        if (tab.id == selectedId) return
        current.webView.onPause()
        selectedId = tab.id
        addressInput = tab.url
        tab.webView.onResume()
    }

    fun close(tab: BrowserTab) {
        val oldIndex = tabs.indexOf(tab)
        val wasSelected = tab.id == selectedId
        tab.webView.onPause()
        tab.destroy()
        tabs.remove(tab)
        if (tabs.isEmpty()) {
            addTab()
        } else if (wasSelected) {
            val replacement = tabs[replacementIndexAfterClose(oldIndex, tabs.size)]
            selectedId = replacement.id
            addressInput = replacement.url
            replacement.webView.onResume()
        }
    }

    BackHandler {
        if (current.canGoBack) current.webView.goBack() else closeProfile()
    }

    Scaffold(topBar = {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$profileName · ${current.title}", maxLines = 1, modifier = Modifier.weight(1f))
                TextButton(onClick = closeProfile) { Text("プロフィール") }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    OutlinedButton(onClick = { select(tab) }) {
                        Text("${if (tab.id == current.id) "● " else ""}${index + 1}")
                    }
                    TextButton(onClick = { close(tab) }) { Text("×") }
                }
                Button(onClick = { addTab() }) { Text("＋ 新規") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("URL または検索語") },
                )
                Button(onClick = { current.webView.loadUrl(normalizeUrl(addressInput)) }) { Text("移動") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton({ current.webView.goBack() }, enabled = current.canGoBack) { Text("← 戻る") }
                TextButton({ current.webView.goForward() }, enabled = current.canGoForward) { Text("進む →") }
                TextButton({ if (current.progress in 1..99) current.webView.stopLoading() else current.webView.reload() }) {
                    Text(if (current.progress in 1..99) "停止" else "再読込")
                }
                TextButton({ current.webView.loadUrl(HOME_URL) }) { Text("ホーム") }
            }
            if (!current.url.startsWith("https://")) {
                Text("安全な HTTPS 接続ではありません", color = MaterialTheme.colorScheme.error)
            }
            if (current.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { current.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            current.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }) { padding ->
        key(current.id) {
            AndroidView(
                factory = {
                    (current.webView.parent as? ViewGroup)?.removeView(current.webView)
                    current.webView
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    LaunchedEffect(current.id, current.url) { addressInput = current.url }
    DisposableEffect(Unit) {
        onDispose {
            tabs.toList().forEach {
                it.webView.onPause()
                it.destroy()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, id: Long): BrowserTab {
    lateinit var tab: BrowserTab
    val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }
    tab = BrowserTab(id, webView)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            tab.url = url
            tab.error = null
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.url = url
            tab.title = view.title?.takeIf(String::isNotBlank) ?: url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) tab.error = "ページを表示できません（${error.errorCode}）"
        }
    }
    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            // Quantizing progress avoids up to 100 whole toolbar recompositions per navigation.
            val displayed = if (newProgress == 100) 100 else (newProgress / 5) * 5
            if (displayed != tab.progress) tab.progress = displayed
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) tab.title = title
        }
    }
    webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
    webView.loadUrl(HOME_URL)
    return tab
}

internal fun normalizeUrl(input: String): String {
    val value = input.trim()
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    return if (value.contains('.') && !value.contains(' ')) "https://$value"
    else "https://www.google.com/search?q=${URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")}"
}

internal fun replacementIndexAfterClose(closedIndex: Int, remainingCount: Int): Int =
    closedIndex.coerceIn(0, (remainingCount - 1).coerceAtLeast(0))
