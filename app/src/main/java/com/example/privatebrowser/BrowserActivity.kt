package com.example.privatebrowser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.content.Intent
import android.view.ViewGroup
import android.view.KeyEvent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Switch

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi

private const val HOME_URL = "https://www.google.com"

private class BrowserTab(val id: Long, val webView: WebView) {
    var icon by mutableStateOf<android.graphics.Bitmap?>(null)
    var thumbnail by mutableStateOf<android.graphics.Bitmap?>(null)
    var lastUsed by mutableLongStateOf(android.os.SystemClock.elapsedRealtime())
    val blockedCount = java.util.concurrent.atomic.AtomicInteger(0)
    val lastBlockedReason = java.util.concurrent.atomic.AtomicReference("")
    var blockedNavigation by mutableStateOf<String?>(null)
    var title by mutableStateOf("新しいタブ")
    var url by mutableStateOf(HOME_URL)
    var progress by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var siteHost by mutableStateOf("")
    var blockingLevel by mutableStateOf(BlockingLevel.STANDARD)
    @Volatile var requestPageHost: String = ""
    @Volatile var youtubeEnabled = true
    @Volatile var requestBlockingLevel: BlockingLevel = BlockingLevel.STANDARD

    var pendingUrl: String? = null
    fun ensureLoaded() { pendingUrl?.let { pendingUrl = null; webView.loadUrl(it) } }
    fun pauseSelectedMedia() = webView.evaluateJavascript(PageScripts.pauseMedia, null)
    fun resumeSelectedMedia() = webView.evaluateJavascript(PageScripts.resumeMedia, null)
    fun capture() {
        if (webView.width <= 0 || webView.height <= 0) return
        thumbnail = runCatching {
            val image = android.graphics.Bitmap.createBitmap(224, 126, android.graphics.Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(image)
            canvas.scale(224f / webView.width, 224f / webView.width)
            webView.draw(canvas)
            image
        }.getOrNull()
    }

    private var destroyed = false
    fun destroy() {
        if (destroyed) return
        destroyed = true
        thumbnail = null
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
    internal lateinit var platform: BrowserPlatform
    internal var persistSession: (() -> Unit)? = null
    internal var resumePage: (() -> Unit)? = null
    internal var shortcutHandler: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val profileId = requireNotNull(intent.getStringExtra(EXTRA_PROFILE_ID))
        val suffix = profileSuffix(profileId)
        if (configuredSuffix == null) {
            WebView.setDataDirectorySuffix(suffix)
            configuredSuffix = suffix
        }
        check(configuredSuffix == suffix) { "A browser process cannot switch profiles" }
        super.onCreate(savedInstanceState)
        activeActivities.incrementAndGet()
        platform = BrowserPlatform(this)
        val windowId = intent.getStringExtra(EXTRA_WINDOW_ID) ?: "primary"
        setContent {
            PrivateBrowserTheme {
                BrowserScreen(
                    profileId = profileId,
                    profileName = intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty(),
                    windowId = windowId,
                ) { finishAndRemoveTask() }
            }
        }
    }

    override fun onDestroy() {
        persistSession?.invoke()
        shortcutHandler = null
        if (::platform.isInitialized) platform.dispose()
        val lastActivity = activeActivities.decrementAndGet() == 0
        super.onDestroy()
        // Adjacent windows share this profile process. Never terminate a surviving window.
        if (isFinishing && lastActivity && !isChangingConfigurations) Process.killProcess(Process.myPid())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        platform.altDown = event.isAltPressed
        platform.shiftDown = event.isShiftPressed
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (platform.isFullscreen) {
                if (event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_BACK) {
                    return platform.hideFullscreen()
                }
                return super.dispatchKeyEvent(event)
            }
            if (shortcutHandler?.invoke(event) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        persistSession?.invoke()
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        resumePage?.invoke()
    }
    override fun onPause() {
        if (::platform.isInitialized) { platform.altDown = false; platform.shiftDown = false }
        super.onPause()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_WINDOW_ID = "window_id"
        @Volatile private var configuredSuffix: String? = null
        private val activeActivities = java.util.concurrent.atomic.AtomicInteger(0)

        internal fun profileSuffix(id: String): String = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserScreen(profileId: String, profileName: String, windowId: String, closeProfile: () -> Unit) {
    val context = LocalContext.current
    val activity = context as BrowserActivity
    val platform = activity.platform
    val focus = LocalFocusManager.current
    val addressFocus = remember { FocusRequester() }
    val preferences = remember(profileId) { context.getSharedPreferences("ui_" + BrowserActivity.profileSuffix(profileId), Context.MODE_PRIVATE) }
    val filters = remember { FilterStore(context.applicationContext) }
    val blockingPreferences = remember(profileId) { SiteBlockingPreferences(context, profileId) }
    val sessionStore = remember(profileId, windowId) { TabSessionStore(context.applicationContext, profileId, windowId) }
    val restored = remember { sessionStore.read() }
    val tabs = remember {
        mutableStateListOf<BrowserTab>().apply {
            restored.tabs.forEach { saved ->
                add(createWebView(context, saved.id, blockingPreferences, platform, filters, saved.url, false).apply { title = saved.title })
            }
            if (isEmpty()) add(createWebView(context, 1L, blockingPreferences, platform, filters))
        }
    }
    val closedTabs = remember { mutableStateListOf<SavedTab>().apply { addAll(restored.closed) } }
    var selectedId by remember { mutableStateOf(restored.selected.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id) }
    var nextId by remember { mutableStateOf((tabs.map { it.id } + closedTabs.map { it.id }).maxOrNull()!!.plus(1L)) }
    var addressInput by remember { mutableStateOf(HOME_URL) }
    var editing by remember { mutableStateOf(false) }
    var expandedTabs by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf("") }
    var overlayAddress by remember { mutableStateOf(preferences.getBoolean("overlay_address", true)) }
    var filterStatus by remember { mutableStateOf(filters.status) }
    var displayedBlockedCount by remember { mutableIntStateOf(0) }
    var displayedBlockReason by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }
    var youtubeEnabled by remember { mutableStateOf(preferences.getBoolean("youtube_ads", true)) }
    val bookmarks = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            runCatching {
                val array = org.json.JSONArray(preferences.getString("bookmarks", "[]"))
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(item.getString("title") to item.getString("url"))
                }
            }
        }
    }
    val current = tabs.first { it.id == selectedId }
    fun saveSession(flush: Boolean = false) {
        sessionStore.save(TabSession(tabs.map { SavedTab(it.id, it.url, it.title) }, selectedId, closedTabs.toList()))
        if (flush) sessionStore.flush()
    }
    fun exitProfile() {
        saveSession(true)
        val error = sessionStore.error
        if (error == null) closeProfile()
        else android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
    }
    fun saveBookmarks() {
        val array = org.json.JSONArray()
        bookmarks.forEach { (title, url) -> array.put(org.json.JSONObject().put("title", title).put("url", url)) }
        preferences.edit().putString("bookmarks", array.toString()).apply()
    }
    fun leaveCurrent() {
        platform.hideFullscreen()
        current.capture()
        tabs.filter { it.thumbnail != null }.sortedByDescending { it.lastUsed }.drop(24).forEach { it.thumbnail = null }
        current.pauseSelectedMedia()
        current.webView.onPause()
    }
    fun select(tab: BrowserTab) {
        if (tab.id != selectedId) {
            leaveCurrent()
            selectedId = tab.id
            tab.lastUsed = android.os.SystemClock.elapsedRealtime()
            addressInput = tab.url
            tab.ensureLoaded()
            tab.webView.onResume()
            tab.resumeSelectedMedia()
        }
        expandedTabs = false
    }
    fun addTab(url: String = HOME_URL, foreground: Boolean = true) {
        if (foreground) leaveCurrent()
        val tab = createWebView(context, nextId++, blockingPreferences, platform, filters, url)
        tabs += tab
        if (foreground) {
            selectedId = tab.id
            addressInput = url
            tab.webView.onResume()
        } else tab.webView.onPause()
    }
    fun close(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        if (tabs.size == 1) addTab()
        if (tab.id == selectedId) {
            val replacement = tabs[if (index + 1 < tabs.size) index + 1 else index - 1]
            selectedId = replacement.id
            addressInput = replacement.url
            replacement.lastUsed = android.os.SystemClock.elapsedRealtime()
            replacement.webView.onResume()
            replacement.resumeSelectedMedia()
        }
        closedTabs.add(SavedTab(tab.id, tab.url, tab.title))
        while (closedTabs.size > 30) closedTabs.removeAt(0)
        tabs.remove(tab)
        tab.destroy()
        tabs.first { it.id == selectedId }.ensureLoaded()
        saveSession()
    }
    fun reopen(tab: SavedTab? = closedTabs.lastOrNull()) {
        if (tab == null) return
        addTab(tab.url)
        closedTabs.remove(tab)
        saveSession()
    }
    fun finishEditing() { editing = false; focus.clearFocus() }
    fun navigate(url: String) { current.webView.loadUrl(normalizeUrl(url)); finishEditing() }
    fun copyUrl() {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("URL", current.url))
        android.widget.Toast.makeText(context, "URLをコピーしました", android.widget.Toast.LENGTH_SHORT).show()
    }
    fun editAddress() { expandedTabs = false; addressInput = current.url; editing = true }
    fun openAdjacentWindow() {
        val adjacentId = java.util.UUID.randomUUID().toString()
        context.startActivity(Intent(context, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_PROFILE_ID, profileId)
            putExtra(BrowserActivity.EXTRA_PROFILE_NAME, profileName)
            putExtra(BrowserActivity.EXTRA_WINDOW_ID, adjacentId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        })
    }
    BackHandler {
        when {
            platform.hideFullscreen() -> Unit
            editing -> finishEditing()
            expandedTabs -> expandedTabs = false
            current.webView.canGoBack() -> current.webView.goBack()
            else -> exitProfile()
        }
    }
    LaunchedEffect(panel, current.id) {
        if (panel == "ads") while (true) {
            filterStatus = filters.status
            displayedBlockedCount = current.blockedCount.get()
            displayedBlockReason = current.lastBlockedReason.get()
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(editing) { if (editing) addressFocus.requestFocus() }
    LaunchedEffect(current.id, current.url) { if (!editing) addressInput = current.url }
    LaunchedEffect(current.id) { current.ensureLoaded() }
    androidx.compose.runtime.SideEffect {
        saveSession()
        activity.persistSession = { saveSession(true) }
        tabs.forEach { it.youtubeEnabled = youtubeEnabled }
        activity.resumePage = {
            // Look up the current instance: the renderer may have died while Compose was stopped.
            tabs.firstOrNull { it.id == selectedId }?.let { active ->
                if (active.error == null) active.ensureLoaded()
                active.webView.onResume()
                active.webView.requestLayout()
                active.webView.invalidate()
                active.webView.evaluateJavascript("Array.from(document.querySelectorAll('video')).some(function(v){return !!v.error;})") { failed ->
                    if (failed == "true" && tabs.any { it === active }) active.error = "動画の読み込みに失敗しました。「再試行」でページを読み直せます"
                }
            }
        }
        platform.rendererLost = { view ->
            val index = tabs.indexOfFirst { it.webView === view }
            if (index >= 0) {
                val old = tabs[index]
                val url = old.url
                val title = old.title
                platform.hideFullscreen()
                old.destroy()
                tabs[index] = createWebView(context, old.id, blockingPreferences, platform, filters, url, false).apply {
                    this.title = title
                    error = "ページの描画処理が終了しました。「再試行」で復元できます"
                }
                saveSession()
            }
        }
        platform.openLink = { url, foreground -> addTab(url, foreground) }
        activity.shortcutHandler = { event ->
            when {
                event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (current.webView.canGoBack()) current.webView.goBack()
                    true
                }
                event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (current.webView.canGoForward()) current.webView.goForward()
                    true
                }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_T -> { if (event.repeatCount == 0) addTab(); true }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_W -> { if (event.repeatCount == 0) close(current); true }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_L -> { editAddress(); true }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_TAB -> {
                    val index = tabs.indexOf(current)
                    select(tabs[(index + (if (event.isShiftPressed) -1 else 1) + tabs.size) % tabs.size]); true
                }
                (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_R) || event.keyCode == KeyEvent.KEYCODE_F5 -> { current.webView.reload(); true }
                event.keyCode == KeyEvent.KEYCODE_ESCAPE && (editing || expandedTabs) -> { finishEditing(); expandedTabs = false; true }
                else -> false
            }
        }
    }
    if (panel.isNotEmpty()) AlertDialog(
        onDismissRequest = { panel = "" },
        title = { Text(when (panel) { "ads" -> "広告ブロック"; "bookmarks" -> "ブックマーク"; "vault" -> "パスワード管理"; "closed" -> "閉じたタブ"; else -> "設定" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (panel) {
                    "ads" -> {
                        Text(current.siteHost)
                        Text("このページの遮断件数: " + displayedBlockedCount)
                        Text(displayedBlockReason)
                        BlockingLevel.entries.forEach { level ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(current.blockingLevel == level, onClick = {
                                    current.blockingLevel = level
                                    current.requestBlockingLevel = level
                                    blockingPreferences.setLevel(current.siteHost, level)
                                    current.blockedCount.set(0)
                                    current.webView.reload()
                                })
                                Text(level.label)
                            }
                        }
                        Text("標準: 広告・日本語フィルターと広告枠の非表示\n強力: 追跡・DNSフィルターを追加")
                        Text("YouTube広告対策（実験的）")
                        Switch(youtubeEnabled, onCheckedChange = {
                            youtubeEnabled = it
                            preferences.edit().putBoolean("youtube_ads", it).apply()
                            tabs.forEach { tab ->
                                tab.youtubeEnabled = it
                                tab.webView.evaluateJavascript(if (it && tab.requestBlockingLevel != BlockingLevel.OFF) PageScripts.youtube else PageScripts.stopYoutube, null)
                            }
                        })
                        Text("ページ内広告と広告スキップを補助します。動画広告の完全除去は未保証です。")
                        Text(filterStatus)
                        TextButton(enabled = !updating, onClick = {
                            updating = true; filterStatus = "更新中"
                            filters.update { filterStatus = it; updating = false }
                        }) { Text("フィルターを更新") }
                        Text("初回・7日経過時は自動更新。未対応構文は件数に含めて除外します。更新後はページを再読み込みしてください。")
                    }
                    "bookmarks" -> {
                        TextButton(onClick = {
                            if (bookmarks.none { it.second == current.url }) { bookmarks += current.title to current.url; saveBookmarks() }
                        }) { Text("このページを追加") }
                        bookmarks.toList().forEach { item ->
                            Row {
                                TextButton(onClick = { navigate(item.second); panel = "" }, modifier = Modifier.weight(1f)) { Text(item.first, maxLines = 2) }
                                TextButton(onClick = { bookmarks.remove(item); saveBookmarks() }) { Text("削除") }
                            }
                        }
                    }
                    "closed" -> {
                        if (closedTabs.isEmpty()) Text("閉じたタブはありません")
                        closedTabs.toList().asReversed().forEach { saved ->
                            TextButton(onClick = { reopen(saved); panel = "" }) { Text(saved.title.ifBlank { saved.url }, maxLines = 2) }
                        }
                    }
                    "vault" -> Text("まだ利用できません。保存・自動入力は有効になっていません。")
                    else -> {
                        Text("URLをステータスバーと同じ帯に表示（実験的）")
                        Switch(overlayAddress, onCheckedChange = { overlayAddress = it; preferences.edit().putBoolean("overlay_address", it).apply() })
                        Text("タップできない場合はオフに戻すか左のURLボタンを使ってください。")
                        Text("タブをプロフィール別に保存します。自動休止・削除は後続実装です。")
                        TextButton(onClick = { openAdjacentWindow(); panel = "" }) { Text("隣に新しいウィンドウを開く") }
                        sessionStore.error?.let { Text(it) }
                        TextButton(onClick = { platform.toggleDesktop(current.webView); panel = "" }) { Text("このタブのPC／モバイル表示を切替") }
                        TextButton(onClick = { platform.openImageSearch(current.webView); panel = "" }) { Text("Google画像検索（Web版）") }
                        TextButton(onClick = { platform.openNicoWatchPage(current.webView); panel = "" }) { Text("ニコ動の通常視聴ページを開く") }
                        TextButton(onClick = { platform.showDiagnostics(current.webView) }) { Text("ページの表示診断") }
                        TextButton(onClick = { platform.hideFullscreen(); current.webView.requestLayout(); current.webView.reload(); panel = "" }) { Text("表示・動画読み込みを再試行") }
                        TextButton(onClick = ::exitProfile) { Text("プロフィールへ戻る") }
                    }
                }
            }
        },
        confirmButton = { TextButton({ panel = "" }) { Text("閉じる") } },
    )
    val systemPadding = WindowInsets.systemBars.asPaddingValues()
    val topHeight = maxOf(24.dp, systemPadding.calculateTopPadding())
    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            if (!overlayAddress || editing) Spacer(Modifier.height(systemPadding.calculateTopPadding()))
            if (editing) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = addressInput, onValueChange = { addressInput = it },
                            modifier = Modifier.weight(1f).focusRequester(addressFocus), singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { navigate(addressInput) }),
                        )
                        ToolbarKey("copy", "URLをコピー", ::copyUrl)
                    }
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { panel = "bookmarks" }) { Text("★") }
                        bookmarks.forEach { item ->
                            TextButton(onClick = { navigate(item.second) }, modifier = Modifier.widthIn(max = 144.dp)) {
                                Text(item.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else Box(Modifier.fillMaxWidth().height(topHeight), contentAlignment = Alignment.Center) {
                Text(current.url, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.5f).clickable { editAddress() }.padding(horizontal = 6.dp))
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.width(56.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    ToolbarKey("‹", "戻る", { current.webView.goBack() }, current.canGoBack)
                    ToolbarKey("›", "進む", { current.webView.goForward() }, current.canGoForward)
                    ToolbarKey("↻", "更新／停止", { if (current.progress in 1..99) current.webView.stopLoading() else current.webView.reload() })
                    ToolbarKey("⌂", "ホーム", { navigate(HOME_URL) })
                    ToolbarKey("URL", "URLを編集", { editAddress() })
                    ToolbarKey("★", "ブックマーク", { panel = "bookmarks" })
                    ToolbarKey("↓", "ダウンロード", { runCatching { context.startActivity(android.content.Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } })
                    ToolbarKey("shield", "広告ブロック", { filterStatus = filters.status; panel = "ads" })
                    ToolbarKey("key", "パスワード管理", { panel = "vault" })
                    ToolbarKey("⋮", "設定", { panel = "settings" })
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AndroidView(
                        factory = { android.widget.FrameLayout(context) },
                        update = { container ->
                            if (container.childCount != 1 || container.getChildAt(0) !== current.webView) {
                                container.removeAllViews()
                                (current.webView.parent as? ViewGroup)?.removeView(current.webView)
                                container.addView(current.webView, android.widget.FrameLayout.LayoutParams(-1, -1))
                            }
                        }, modifier = Modifier.fillMaxSize(),
                    )
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        if (current.progress in 1..99) LinearProgressIndicator(progress = { current.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        current.error?.let { error ->
                            Row(Modifier.background(MaterialTheme.colorScheme.errorContainer), verticalAlignment = Alignment.CenterVertically) {
                                Text(error, Modifier.weight(1f).padding(8.dp))
                                TextButton(onClick = {
                                    current.error = null
                                    if (current.pendingUrl != null) current.ensureLoaded() else current.webView.reload()
                                }) { Text("再試行") }
                            }
                        }
                        current.blockedNavigation?.let { destination ->
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("広告URLへの移動を停止", Modifier.weight(1f))
                                    TextButton(onClick = { current.blockedNavigation = null; current.webView.loadUrl(destination) }) { Text("今回開く") }
                                    TextButton(onClick = { current.blockedNavigation = null }) { Text("閉じる") }
                                }
                            }
                        }
                    }
                    if (expandedTabs || editing) Box(Modifier.fillMaxSize().clickable { expandedTabs = false; finishEditing() })
                }
                Column(Modifier.width(56.dp).fillMaxHeight()) {
                    ToolbarKey("＋", "新しいタブ", { addTab() })
                    LazyColumn(Modifier.weight(1f)) {
                    items(tabs, key = { it.id }) { tab ->
                        val threshold = with(LocalDensity.current) { 48.dp.toPx() }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                                .background(if (tab.id == current.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .pointerInput(tab.id, selectedId) {
                                    var distance = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { distance = 0f },
                                        onDragCancel = {},
                                        onDragEnd = {
                                            when {
                                                distance > threshold -> close(tab)
                                                distance < -threshold -> { select(tab); expandedTabs = true }
                                            }
                                        },
                                        onHorizontalDrag = { change, amount -> distance += amount; change.consume() },
                                    )
                                }.clickable { select(tab) },
                        ) {
                            val icon = tab.icon
                            if (icon != null) Image(icon.asImageBitmap(), contentDescription = tab.title, modifier = Modifier.size(24.dp))
                            else Text((if (tab.id == current.id) "● " else "") + tab.title.take(2), maxLines = 1)
                        }
                    }
                    }
                    Box(Modifier.size(56.dp, 48.dp).combinedClickable(onClick = { reopen() }, onLongClick = { panel = "closed" }), contentAlignment = Alignment.Center) {
                        Text("↶", fontSize = 26.sp, modifier = Modifier.semantics { contentDescription = "閉じたタブを復元。長押しで履歴" })
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(maxOf(40.dp, systemPadding.calculateBottomPadding())))
        }
        if (expandedTabs) Surface(
            modifier = Modifier.align(Alignment.CenterEnd).width(224.dp)
                .padding(top = systemPadding.calculateTopPadding() + topHeight, bottom = maxOf(40.dp, systemPadding.calculateBottomPadding())).fillMaxHeight(),
            shadowElevation = 8.dp,
        ) {
            Column {
                ToolbarKey("＋", "新しいタブ", { addTab(); expandedTabs = false })
                LazyColumn(Modifier.weight(1f)) {
                items(tabs, key = { it.id }) { tab ->
                    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
                    var dragOffset by remember(tab.id) { androidx.compose.runtime.mutableFloatStateOf(0f) }
                    Column(Modifier.fillMaxWidth().offset { androidx.compose.ui.unit.IntOffset(dragOffset.toInt(), 0) }.pointerInput(tab, selectedId) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { distance = 0f; dragOffset = 0f },
                            onDragCancel = { distance = 0f; dragOffset = 0f },
                            onDragEnd = { if (distance > threshold) close(tab); distance = 0f; dragOffset = 0f },
                            onHorizontalDrag = { change, amount -> distance += amount; dragOffset = distance.coerceAtLeast(0f); change.consume() },
                        )
                    }.background(
                        if (tab.id == current.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    ).clickable { select(tab) }.padding(8.dp)) {
                        val thumbnail = tab.thumbnail
                        if (thumbnail != null) Image(thumbnail.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(117.dp))
                        else Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) { Text("プレビューなし") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tab.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { close(tab) }) { Text("×") }
                        }
                    }
                }
                }
                Box(Modifier.fillMaxWidth().height(48.dp).combinedClickable(onClick = { reopen(); expandedTabs = false }, onLongClick = { panel = "closed" }), contentAlignment = Alignment.Center) {
                    Text("↶ 閉じたタブを復元")
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity.shortcutHandler = null
            platform.rendererLost = null
            platform.openLink = null
            saveSession(true)
            activity.persistSession = null
            activity.resumePage = null
            sessionStore.dispose()
            filters.dispose()
            tabs.toList().forEach { it.destroy() }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolbarKey(label: String, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    val context = LocalContext.current
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.size(56.dp, 48.dp).combinedClickable(
            enabled = enabled, onClick = onClick,
            onLongClick = { android.widget.Toast.makeText(context, description, android.widget.Toast.LENGTH_SHORT).show() },
        ).semantics { contentDescription = description },
    ) {
        if (label in setOf("shield", "key", "copy")) {
            val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
            Canvas(Modifier.size(24.dp)) {
                val scale = size.width / 24f
                fun point(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * scale, y * scale)
                val stroke = Stroke(1.8f * scale)
                when (label) {
                    "shield" -> drawPath(Path().apply {
                        moveTo(12*scale, 2*scale); lineTo(21*scale, 6*scale); lineTo(20*scale, 14*scale)
                        quadraticBezierTo(18*scale, 20*scale, 12*scale, 23*scale)
                        quadraticBezierTo(6*scale, 20*scale, 4*scale, 14*scale)
                        lineTo(3*scale, 6*scale); close()
                    }, color, style = stroke)
                    "key" -> {
                        drawCircle(color, 5*scale, point(7f, 9f), style = stroke)
                        drawLine(color, point(11f,12f), point(21f,22f), 2*scale)
                        drawLine(color, point(17f,18f), point(20f,15f), 2*scale)
                    }
                    else -> {
                        drawRect(color, point(8f,8f), androidx.compose.ui.geometry.Size(13*scale,14*scale), style = stroke)
                        drawLine(color, point(3f,17f), point(3f,2f), 2*scale)
                        drawLine(color, point(3f,2f), point(16f,2f), 2*scale)
                    }
                }
            }
        } else Text(label, fontSize = if (label.length > 1) 12.sp else 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    id: Long,
    blockingPreferences: SiteBlockingPreferences,
    platform: BrowserPlatform,
    filters: FilterStore,
    initialUrl: String = HOME_URL,
    loadImmediately: Boolean = true,
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
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(true)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }
    tab = BrowserTab(id, webView).apply { url = initialUrl; pendingUrl = if (loadImmediately) null else initialUrl }
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.isForMainFrame) platform.applyDefaultDisplayMode(view, request.url.toString())
            if (request.isForMainFrame && blocks(request.url.toString())) {
                tab.blockedCount.incrementAndGet()
                tab.blockedNavigation = request.url.toString()
                return true
            }
            return platform.handleNavigation(view, request)
        }

        private fun blocks(url: String, type: String = "document"): Boolean {
            if (tab.requestBlockingLevel == BlockingLevel.OFF) return false
            return when (filters.decision(url, tab.requestPageHost, type, tab.requestBlockingLevel)) {
                -1 -> false
                1 -> { tab.lastBlockedReason.set("更新フィルター: " + FilterRules.hostOf(url)); true }
                else -> LocalRequestBlocker.shouldBlock(url, tab.requestBlockingLevel).also { blocked ->
                    if (blocked) tab.lastBlockedReason.set("同梱ルール: " + FilterRules.hostOf(url))
                }
            }
        }
        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            val recovery = platform.rendererLost
            if (recovery != null) recovery(view) else tab.destroy()
            return true
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (url != null) tab.url = url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            tab.pendingUrl = null
            tab.url = url
            tab.siteHost = LocalRequestBlocker.host(url)
            tab.requestPageHost = tab.siteHost
            tab.blockingLevel = blockingPreferences.level(tab.siteHost)
            tab.requestBlockingLevel = tab.blockingLevel
            tab.error = null
            tab.blockedCount.set(0)
            tab.lastBlockedReason.set("")
            tab.blockedNavigation = null
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.url = url
            tab.title = view.title?.takeIf(String::isNotBlank) ?: url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
            if (tab.requestBlockingLevel != BlockingLevel.OFF) {
                val selectors = if (FilterRules.domainMatches(FilterRules.hostOf(url), "youtube.com")) emptySet() else
                    filters.rules.selectors(FilterRules.hostOf(url)) + setOf(".adsbygoogle")
                view.evaluateJavascript(PageScripts.cosmetic(selectors), null)
                if (tab.youtubeEnabled) view.evaluateJavascript(PageScripts.youtube, null)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            platform.noteError(view, request, error.errorCode)
            if (request.isForMainFrame) tab.error = "ページを表示できません（${error.errorCode}）"
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            platform.noteError(view, request, response.statusCode)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!request.isForMainFrame && blocks(request.url.toString(), requestType(request))) {
                tab.blockedCount.incrementAndGet()
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
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
            if (!isUserGesture) { tab.blockedCount.incrementAndGet(); return false }
            // Resolve an explicitly clicked target=_blank without running arbitrary popup scripts.
            val popup = WebView(context).apply {
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptEnabled = false
            }
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var finished = false
            fun release() { if (!finished) { finished = true; popup.stopLoading(); popup.destroy() } }
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return true
                    val url = request.url.toString()
                    if (request.url.scheme == "about") return false
                    if (FilterRules.hostOf(url).isNotBlank()) {
                        val decision = filters.decision(url, tab.requestPageHost, "document", tab.requestBlockingLevel)
                        val blocked = tab.requestBlockingLevel != BlockingLevel.OFF && decision != -1 &&
                            (decision == 1 || LocalRequestBlocker.shouldBlock(url, tab.requestBlockingLevel))
                        if (blocked) { tab.blockedCount.incrementAndGet(); tab.blockedNavigation = url }
                        else platform.openLink?.invoke(url, true)
                    }
                    handler.post { release() }
                    return true
                }
            }
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: run { release(); return false }
            transport.webView = popup
            resultMsg.sendToTarget()
            handler.postDelayed({ release() }, 10000L)
            return true
        }
        override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) =
            platform.showFullscreen(view, callback)

        override fun onHideCustomView() { platform.hideFullscreen() }

        override fun onShowFileChooser(
            view: WebView,
            callback: android.webkit.ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean = platform.chooseFiles(callback, params)

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            // Quantizing progress avoids up to 100 whole toolbar recompositions per navigation.
            val displayed = if (newProgress == 100) 100 else (newProgress / 5) * 5
            if (displayed != tab.progress) tab.progress = displayed
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) tab.title = title
        }
        override fun onReceivedIcon(view: WebView, icon: android.graphics.Bitmap?) { tab.icon = icon }
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
    platform.installLinkMenu(webView)
    platform.applyDefaultDisplayMode(webView, initialUrl)
    if (loadImmediately) webView.loadUrl(initialUrl)
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

/** Use explicit request metadata; do not guess script/XHR from a URL extension. */
private fun requestType(request: WebResourceRequest): String {
    if (request.isForMainFrame) return "document"
    val destination = request.requestHeaders.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", true) }?.value.orEmpty()
    return when (destination) {
        "script", "image", "font" -> destination
        "style" -> "stylesheet"
        "video", "audio" -> "media"
        "iframe", "frame" -> "subdocument"
        else -> ""
    }
}
