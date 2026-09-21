package com.example.privatebrowser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.net.URLEncoder
import java.security.MessageDigest

data class BrowserTab(val id: Long, val webView: WebView)
data class DownloadEntry(val fileName: String, val status: String)

class BrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val profileId = requireNotNull(intent.getStringExtra(EXTRA_PROFILE_ID))
        WebView.setDataDirectorySuffix(profileSuffix(profileId))
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BrowserScreen(intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty()) { finishAndRemoveTask() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"

        internal fun profileSuffix(id: String): String = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }
}

@Composable
private fun BrowserScreen(profileName: String, closeProfile: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tabs = remember { mutableStateListOf<BrowserTab>() }
    val downloads = remember { mutableStateListOf<DownloadEntry>() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf("browser") }
    var address by remember { mutableStateOf("https://www.google.com") }
    var pageTitle by remember { mutableStateOf("新しいタブ") }

    fun createTab(): BrowserTab = BrowserTab(System.nanoTime(), createWebView(context, downloads) { url, title ->
        address = url
        pageTitle = title
    })
    if (tabs.isEmpty()) tabs += createTab()
    val current = tabs[selectedTab.coerceIn(tabs.indices)]

    androidx.activity.compose.BackHandler {
        if (page != "browser") page = "browser"
        else if (current.webView.canGoBack()) current.webView.goBack()
        else closeProfile()
    }

    Scaffold(
        topBar = {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$profileName · $pageTitle", maxLines = 1, modifier = Modifier.weight(1f))
                    Button(onClick = closeProfile) { Text("プロフィール") }
                }
                if (page == "browser") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tabs.forEachIndexed { index, _ ->
                            Button(onClick = {
                                selectedTab = index
                                address = tabs[index].webView.url.orEmpty()
                                pageTitle = tabs[index].webView.title ?: "新しいタブ"
                            }) { Text(if (index == selectedTab) "タブ ${index + 1} ●" else "タブ ${index + 1}") }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedTextField(address, { address = it }, modifier = Modifier.weight(1f), singleLine = true)
                        Button(onClick = { current.webView.loadUrl(normalizeUrl(address)) }) { Text("移動") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { current.webView.goBack() }, enabled = current.webView.canGoBack()) { Text("←") }
                        IconButton(onClick = { current.webView.goForward() }, enabled = current.webView.canGoForward()) { Text("→") }
                        IconButton(onClick = { current.webView.reload() }) { Text("↻") }
                        IconButton(onClick = {
                            tabs += createTab()
                            selectedTab = tabs.lastIndex
                            address = "https://www.google.com"
                            tabs.last().webView.loadUrl(address)
                        }) { Text("＋") }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(page == "browser", { page = "browser" }, icon = { Text("□") }, label = { Text("タブ ${selectedTab + 1}/${tabs.size}") })
                NavigationBarItem(page == "downloads", { page = "downloads" }, icon = { Text("↓") }, label = { Text("ダウンロード") })
            }
        },
    ) { padding ->
        if (page == "browser") {
            key(current.id) {
                AndroidView(factory = { current.webView }, modifier = Modifier.fillMaxSize().padding(padding))
            }
        } else {
            DownloadScreen(downloads, Modifier.padding(padding))
        }
    }

    DisposableEffect(Unit) {
        onDispose { tabs.forEach { it.webView.destroy() } }
    }
}

private fun createWebView(
    context: Context,
    downloads: MutableList<DownloadEntry>,
    onPageChanged: (String, String) -> Unit,
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) = onPageChanged(url, view.title.orEmpty())
    }
    webChromeClient = WebChromeClient()
    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        downloads += DownloadEntry(fileName, "ダウンロード中")
    }
    loadUrl("https://www.google.com")
}

@Composable
private fun DownloadScreen(downloads: List<DownloadEntry>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("ダウンロード", style = MaterialTheme.typography.headlineSmall)
        if (downloads.isEmpty()) Text("ダウンロードはありません。")
        LazyColumn { items(downloads) { Text("${it.fileName} — ${it.status}", modifier = Modifier.padding(vertical = 12.dp)) } }
    }
}

internal fun normalizeUrl(input: String): String {
    val value = input.trim()
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    return if (value.contains('.') && !value.contains(' ')) "https://$value"
    else "https://www.google.com/search?q=${URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")}"
}
