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
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder
import java.security.MessageDigest
import java.io.ByteArrayInputStream

private const val HOME_URL = "https://www.google.com"

private class BrowserTab(val id: Long, val webView: WebView) {
    var title by mutableStateOf("新しいタブ")
    var url by mutableStateOf(HOME_URL)
    var progress by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var siteHost by mutableStateOf("")
    var blockingLevel by mutableStateOf(BlockingLevel.STANDARD)
    @Volatile var requestBlockingLevel: BlockingLevel = BlockingLevel.STANDARD

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
                BrowserScreen(
                    profileId = profileId,
                    profileName = intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty(),
                ) { finishAndRemoveTask() }
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
private fun BrowserScreen(profileId: String, profileName: String, closeProfile: () -> Unit) {
    val context = LocalContext.current
    val blockingPreferences = remember(profileId) { SiteBlockingPreferences(context, profileId) }
    val tabs = remember { mutableStateListOf(createWebView(context, 1L, blockingPreferences)) }
    var selectedId by remember { mutableStateOf<Long?>(1L) }
    var addressInput by remember { mutableStateOf(HOME_URL) }
    var nextId by remember { mutableStateOf(2L) }
    var showSiteControls by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    fun addTab() {
        tabs.firstOrNull { it.id == selectedId }?.webView?.onPause()
        val tab = createWebView(context, nextId++, blockingPreferences)
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

    if (showSiteControls) {
        AlertDialog(
            onDismissRequest = { showSiteControls = false },
            title = { Text("このサイトのブロック設定") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(current.siteHost.ifBlank { "サイトを読み込み中" })
                    BlockingLevel.entries.forEach { level ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = current.blockingLevel == level,
                                onClick = {
                                    current.blockingLevel = level
                                    current.requestBlockingLevel = level
                                    if (current.siteHost.isNotBlank()) {
                                        blockingPreferences.setLevel(current.siteHost, level)
                                    }
                                    showSiteControls = false
                                    current.webView.reload()
                                },
                            )
                            Column {
                                Text(level.label)
                                if (level == BlockingLevel.STRICT) Text("サイトが壊れる可能性があります")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showSiteControls = false }) { Text("閉じる") } },
        )
    }
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("ブラウザメニュー") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("サイト別ブロック: ${current.blockingLevel.label}")
                    Text("パスワード保存: 未実装")
                    Text("安全なKeystore設計と認証UIが完成するまで、パスワードは保存しません。")
                }
            },
            confirmButton = { TextButton({ showMenu = false }) { Text("閉じる") } },
        )
    }

    Scaffold(
        topBar = {
            BrowserTopBar(
                tab = current,
                addressInput = addressInput,
                onAddressChange = { addressInput = it },
                onNavigate = { current.webView.loadUrl(normalizeUrl(addressInput)) },
                onReload = {
                    if (current.progress in 1..99) current.webView.stopLoading() else current.webView.reload()
                },
                onSiteControls = { showSiteControls = true },
                onMenu = { showMenu = true },
            )
        },
        bottomBar = {
            SleipnirTabShelf(
                tabs = tabs,
                selectedId = current.id,
                onSelect = ::select,
                onClose = ::close,
                onAdd = ::addTab,
                canGoBack = current.canGoBack,
                canGoForward = current.canGoForward,
                onBack = { current.webView.goBack() },
                onForward = { current.webView.goForward() },
                onHome = { current.webView.loadUrl(HOME_URL) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            key(current.id) {
                AndroidView(
                    factory = {
                        (current.webView.parent as? ViewGroup)?.removeView(current.webView)
                        current.webView
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                if (!current.url.startsWith("https://")) {
                    Text(
                        "安全な HTTPS 接続ではありません",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                if (current.progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { current.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                current.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(8.dp),
                    )
                }
            }
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

@Composable
private fun BrowserTopBar(
    tab: BrowserTab,
    addressInput: String,
    onAddressChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onReload: () -> Unit,
    onSiteControls: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarKey("☆", "ブックマーク（未実装）", onClick = {})
            OutlinedTextField(
                value = addressInput,
                onValueChange = onAddressChange,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onNavigate() }),
                leadingIcon = {
                    Text(if (tab.url.startsWith("https://")) "●" else "!", color = if (tab.url.startsWith("https://")) Color(0xFF1687B8) else MaterialTheme.colorScheme.error)
                },
                placeholder = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
            ToolbarKey(if (tab.progress in 1..99) "×" else "↻", "再読み込み／停止", onReload)
            ToolbarKey("盾", "サイト別ブロック", onSiteControls)
            ToolbarKey("⋮", "メニュー", onMenu)
        }
    }
}

@Composable
private fun ToolbarKey(label: String, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
    }
}

private val tabColors = listOf(
    Color(0xFF91B8E9), Color(0xFFA7C5E3), Color(0xFFD5B2E6),
    Color(0xFFB9DEBE), Color(0xFFFFC0AB), Color(0xFFFFE38C),
)

@Composable
private fun SleipnirTabShelf(
    tabs: List<BrowserTab>,
    selectedId: Long,
    onSelect: (BrowserTab) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onAdd: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF10A9DF)))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    TabTile(
                        tab = tab,
                        selected = tab.id == selectedId,
                        color = tabColors[index % tabColors.size],
                        onSelect = { onSelect(tab) },
                        onClose = { onClose(tab) },
                    )
                }
                AddTabTile(onAdd)
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarKey("‹", "戻る", onBack, enabled = canGoBack)
                Spacer(Modifier.width(12.dp))
                ToolbarKey("⌂", "ホーム", onHome)
                Spacer(Modifier.width(12.dp))
                ToolbarKey("›", "進む", onForward, enabled = canGoForward)
                Spacer(Modifier.weight(1f))
                Text("タブ ${tabs.indexOfFirst { it.id == selectedId } + 1} / ${tabs.size}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun TabTile(
    tab: BrowserTab,
    selected: Boolean,
    color: Color,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier.width(if (selected) 200.dp else 176.dp).height(66.dp).padding(end = 3.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) color else color.copy(alpha = 0.72f)),
        elevation = CardDefaults.cardElevation(if (selected) 5.dp else 0.dp),
    ) {
        Row(
            Modifier.fillMaxSize().clickable(onClick = onSelect).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (selected) "●" else "○", color = Color(0xFF087EAE), style = MaterialTheme.typography.labelSmall)
                Text(
                    tab.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (color.luminance() > .45f) Color(0xFF202124) else Color.White,
                )
            }
            TextButton(onClick = onClose, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) {
                Text("×", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun AddTabTile(onAdd: () -> Unit) {
    Box(
        modifier = Modifier.width(92.dp).height(66.dp)
            .background(Color(0xFF9EDCF2))
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋", color = Color(0xFF008EC4), style = MaterialTheme.typography.headlineMedium)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    id: Long,
    blockingPreferences: SiteBlockingPreferences,
): BrowserTab {
    lateinit var tab: BrowserTab
    val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }
    tab = BrowserTab(id, webView)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            tab.url = url
            tab.siteHost = LocalRequestBlocker.host(url)
            tab.blockingLevel = blockingPreferences.level(tab.siteHost)
            tab.requestBlockingLevel = tab.blockingLevel
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

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!request.isForMainFrame && LocalRequestBlocker.shouldBlock(request.url.toString(), tab.requestBlockingLevel)) {
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    204,
                    "No Content",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }
            return null
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
