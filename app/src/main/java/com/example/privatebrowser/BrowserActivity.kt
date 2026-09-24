package com.example.privatebrowser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.view.ViewGroup
import android.view.KeyEvent
import android.webkit.CookieManager
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
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
    var desktopMode by mutableStateOf(false)
    @Volatile var requestPageHost: String = ""
    @Volatile var youtubeEnabled = true
    var youtubeEarlyScript: androidx.webkit.ScriptHandler? = null
    @Volatile var requestBlockingLevel: BlockingLevel = BlockingLevel.STANDARD
    @Volatile var isActive = false
    val detectedMedia = mutableStateListOf<DetectedMedia>()

    fun setActive(active: Boolean) {
        isActive = active
        webView.evaluateJavascript(PageScripts.mediaGate(active), null)
    }

    var pendingUrl: String? = null
    fun ensureLoaded() { pendingUrl?.let { pendingUrl = null; webView.loadUrl(it) } }
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
        youtubeEarlyScript?.remove()
        youtubeEarlyScript = null
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
        synchronized(BrowserActivity::class.java) { activeActivities++ }
        platform = BrowserPlatform(this)
        val windowId = intent.getStringExtra(EXTRA_WINDOW_ID) ?: "primary"
        setContent {
            PrivateBrowserTheme {
                BrowserScreen(
                    profileId = profileId,
                    profileName = intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty(),
                    windowId = windowId,
                    openAdjacent = {
                        startActivity(Intent(this, BrowserActivity::class.java).apply {
                            putExtra(EXTRA_PROFILE_ID, profileId)
                            putExtra(EXTRA_PROFILE_NAME, intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty())
                            putExtra(EXTRA_WINDOW_ID, java.util.UUID.randomUUID().toString())
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                        })
                    },
                ) { finishAndRemoveTask() }
            }
        }
    }

    override fun onDestroy() {
        persistSession?.invoke()
        shortcutHandler = null
        if (::platform.isInitialized) platform.dispose()
        super.onDestroy()
        // A profile owns this dedicated process. End it only when explicitly leaving the profile;
        // configuration changes are handled by Compose disposal without killing the new Activity.
        val remaining = synchronized(BrowserActivity::class.java) { (--activeActivities).coerceAtLeast(0).also { activeActivities = it } }
        if (isFinishing && remaining == 0) android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        platform.ctrlDown = event.isCtrlPressed
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
        if (::platform.isInitialized) { platform.ctrlDown = false; platform.shiftDown = false }
        super.onPause()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_WINDOW_ID = "window_id"
        @Volatile private var configuredSuffix: String? = null
        private var activeActivities = 0

        internal fun profileSuffix(id: String): String = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserScreen(profileId: String, profileName: String, windowId: String, openAdjacent: () -> Unit, closeProfile: () -> Unit) {
    val context = LocalContext.current
    val activity = context as BrowserActivity
    val platform = activity.platform
    val focus = LocalFocusManager.current
    val addressFocus = remember { FocusRequester() }
    val preferences = remember(profileId) { context.getSharedPreferences("ui_" + BrowserActivity.profileSuffix(profileId), Context.MODE_PRIVATE) }
    val filters = remember { FilterStore(context.applicationContext) }
    val blockingPreferences = remember(profileId) { SiteBlockingPreferences(context, profileId) }
    val sessionStore = remember(profileId, windowId) { TabSessionStore(context.applicationContext, profileId, windowId) }
    val downloads = remember(profileId) { DownloadRepository(context.applicationContext, profileId) }
    val hlsDownloads = remember(profileId) { HlsDownloadRepository(context.applicationContext, profileId) }
    val mediaCatalog = remember(profileId) { MediaCatalog(context.applicationContext, profileId) }
    remember(profileId) { ExtensionPreferences(context.applicationContext, profileId) }
    val restored = remember { sessionStore.read() }
    val initialYoutubeEnabled = remember(profileId) { preferences.getBoolean("youtube_ads", true) }
    val tabs = remember {
        mutableStateListOf<BrowserTab>().apply {
            restored.tabs.forEach { saved ->
                add(createWebView(context, saved.id, blockingPreferences, platform, filters, downloads, saved.url, false, initialYoutubeEnabled).apply { title = saved.title })
            }
            if (isEmpty()) add(createWebView(context, 1L, blockingPreferences, platform, filters, downloads, youtubeEnabled = initialYoutubeEnabled))
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
    var youtubeEnabled by remember { mutableStateOf(initialYoutubeEnabled) }
    var mediaRevision by remember { mutableIntStateOf(0) }
    val selectedStreamIds = remember { mutableStateListOf<String>() }
    val compactTabListState = rememberLazyListState()
    val expandedTabListState = rememberLazyListState()
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
        current.setActive(false)
    }
    fun select(tab: BrowserTab) {
        if (tab.id != selectedId) {
            leaveCurrent()
            selectedId = tab.id
            tab.lastUsed = android.os.SystemClock.elapsedRealtime()
            addressInput = tab.url
            tab.ensureLoaded()
            tab.webView.onResume()
            tab.setActive(true)
        }
        expandedTabs = false
    }
    fun addTab(url: String = HOME_URL, foreground: Boolean = true) {
        if (foreground) leaveCurrent()
        val tab = createWebView(context, nextId++, blockingPreferences, platform, filters, downloads, url, youtubeEnabled = youtubeEnabled)
        tabs += tab
        if (foreground) {
            selectedId = tab.id
            addressInput = url
            tab.webView.onResume()
            tab.setActive(true)
        } else tab.setActive(false)
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
            replacement.setActive(true)
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
    fun openPlayer(media: DetectedMedia) {
        context.startActivity(Intent(context, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL, media.url)
            putExtra(PlayerActivity.EXTRA_PROFILE_ID, profileId)
            putExtra(PlayerActivity.EXTRA_MEDIA_ID, media.id)
            putExtra(PlayerActivity.EXTRA_USER_AGENT, media.userAgent)
            putExtra(PlayerActivity.EXTRA_REFERER, media.referer)
            putExtra(PlayerActivity.EXTRA_POSTER, media.posterUrl)
        })
    }
    fun downloadMedia(media: DetectedMedia) {
        if (media.isHls) {
            val job = hlsDownloads.create(media)
            val command = Intent(context, HlsExportService::class.java).apply {
                putExtra(HlsExportService.EXTRA_JOB_ID, job.id)
                putExtra(HlsExportService.EXTRA_PROFILE_ID, profileId)
                putExtra(HlsExportService.EXTRA_URL, media.url)
                putExtra(HlsExportService.EXTRA_TITLE, media.title)
                putExtra(HlsExportService.EXTRA_USER_AGENT, media.userAgent)
                putExtra(HlsExportService.EXTRA_REFERER, media.referer)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, command)
            mediaRevision++
            android.widget.Toast.makeText(context, "m3u8をMP4へ保存します", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val id = downloads.enqueue(media.url, media.userAgent, "attachment; filename=\"${media.title}.mp4\"", "video/mp4", media.referer)
            android.widget.Toast.makeText(context, if (id == null) "開始できません" else "ダウンロードを開始しました", android.widget.Toast.LENGTH_SHORT).show()
        }
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
        while (panel == "ads" || panel == "downloads") {
            if (panel == "ads") {
                filterStatus = filters.status
                displayedBlockedCount = current.blockedCount.get()
                displayedBlockReason = current.lastBlockedReason.get()
            } else mediaRevision++
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(editing) { if (editing) addressFocus.requestFocus() }
    LaunchedEffect(current.id, current.url) { if (!editing) addressInput = current.url }
    LaunchedEffect(current.id) { current.ensureLoaded(); current.setActive(true) }
    LaunchedEffect(expandedTabs, selectedId) {
        if (expandedTabs) tabs.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }?.let { expandedTabListState.scrollToItem(it) }
    }
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
                tabs[index] = createWebView(context, old.id, blockingPreferences, platform, filters, downloads, url, false, youtubeEnabled).apply {
                    this.title = title
                    error = "ページの描画処理が終了しました。「再試行」で復元できます"
                }
                saveSession()
            }
        }
        platform.openLink = { url, foreground -> addTab(url, foreground) }
        activity.shortcutHandler = { event ->
            when {
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (current.webView.canGoBack()) current.webView.goBack()
                    true
                }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
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
        title = { Text(when (panel) { "ads" -> "広告ブロック"; "bookmarks" -> "ブックマーク"; "vault" -> "パスワード管理"; "closed" -> "閉じたタブ"; "downloads" -> "ダウンロード"; "streams" -> "ストリーミング動画"; else -> "設定" }) },
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
                                    if (FilterRules.domainMatches(current.siteHost, "youtube.com")) {
                                        current.webView.evaluateJavascript(
                                            "try{localStorage.setItem('__privateYoutubeAdblockDisabled','${if (level == BlockingLevel.OFF) "1" else "0"}')}catch(e){}",
                                            null,
                                        )
                                        configureYoutubeEarly(current, youtubeEnabled && level != BlockingLevel.OFF)
                                    }
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
                                configureYoutubeEarly(tab, it && tab.requestBlockingLevel != BlockingLevel.OFF)
                                if (FilterRules.domainMatches(FilterRules.hostOf(tab.url), "youtube.com")) tab.webView.reload()
                                else tab.webView.evaluateJavascript(if (it && tab.requestBlockingLevel != BlockingLevel.OFF) PageScripts.youtube else PageScripts.stopYoutube, null)
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
                    "downloads" -> {
                        if (current.detectedMedia.isEmpty()) Text("このページから動画をまだ検出していません。動画を再生してから再度開いてください。")
                        current.detectedMedia.forEach { media ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    MediaThumbnail(media.posterUrl)
                                    Text(media.title, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text("${formatDuration(media.durationMs)} / ${media.quality} / ${formatBytes(media.estimatedBytes)}")
                                    Row {
                                        TextButton(onClick = { downloadMedia(media) }) { Text("Download") }
                                        TextButton(onClick = { mediaCatalog.add(media); mediaRevision++; openPlayer(media) }) { Text("Stream") }
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                        val entries = downloads.list()
                        val hlsEntries = hlsDownloads.list()
                        if (entries.isEmpty() && hlsEntries.isEmpty()) Text("ダウンロードはありません")
                        hlsEntries.forEach { item ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("m3u8 → MP4 / ${item.statusLabel}")
                                Row {
                                    if (item.status == HlsDownloadStatus.FAILED) TextButton(onClick = {
                                        hlsDownloads.markQueued(item.id)
                                        androidx.core.content.ContextCompat.startForegroundService(context, item.toServiceIntent(context, profileId))
                                    }) { Text("再試行") }
                                    TextButton(onClick = { hlsDownloads.remove(item.id); mediaRevision++ }) { Text("一覧から削除") }
                                }
                            }
                        }
                        entries.forEach { item ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(downloadStatus(item))
                                Row {
                                    if (item.status == DownloadManager.STATUS_SUCCESSFUL) TextButton(onClick = { downloads.open(item.id) }) { Text("開く") }
                                    if (item.status == DownloadManager.STATUS_FAILED) TextButton(onClick = { downloads.retry(item.id) }) { Text("再試行") }
                                    if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING || item.status == DownloadManager.STATUS_PAUSED) TextButton(onClick = { downloads.cancel(item.id) }) { Text("中止") }
                                    TextButton(onClick = { downloads.forget(item.id) }) { Text("一覧から削除") }
                                }
                            }
                        }
                    }
                    "streams" -> {
                        mediaRevision // observe local mutations
                        val streams = mediaCatalog.list()
                        if (streams.isEmpty()) Text("Streamで追加した動画はありません")
                        streams.forEach { media ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).combinedClickable(
                                onClick = {
                                    if (selectedStreamIds.isEmpty()) openPlayer(media)
                                    else if (media.id in selectedStreamIds) selectedStreamIds.remove(media.id) else selectedStreamIds.add(media.id)
                                },
                                onLongClick = { if (media.id !in selectedStreamIds) selectedStreamIds.add(media.id) },
                            ), colors = CardDefaults.cardColors(containerColor = if (media.id in selectedStreamIds) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    MediaThumbnail(media.posterUrl, Modifier.size(112.dp, 63.dp))
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(media.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${formatDuration(media.durationMs)} / ${media.quality} / ${FilterRules.hostOf(media.pageUrl)}")
                                    }
                                }
                            }
                        }
                        if (selectedStreamIds.isNotEmpty()) TextButton(onClick = {
                            mediaCatalog.remove(selectedStreamIds.toSet()); selectedStreamIds.clear(); mediaRevision++
                        }) { Text("選択した${selectedStreamIds.size}件を削除") }
                    }
                    else -> {
                        Text("URLをステータスバーと同じ帯に表示（実験的）")
                        Switch(overlayAddress, onCheckedChange = { overlayAddress = it; preferences.edit().putBoolean("overlay_address", it).apply() })
                        Text("タップできない場合はオフに戻すか左のURLボタンを使ってください。")
                        Text("タブをプロフィール別に保存します。自動休止・削除は後続実装です。")
                        sessionStore.error?.let { Text(it) }
                        Text("現在の表示: " + if (current.desktopMode) "PC" else "モバイル", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { current.desktopMode = platform.toggleDesktop(current.webView); panel = "" }) { Text("このタブのPC／モバイル表示を切替") }
                        Text("YouTube・ニコニコ・ニコ生は初期状態でPC表示です。")
                        TextButton(onClick = { openAdjacent(); panel = "" }) { Text("隣に新しいウィンドウを開く") }
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
                Row(Modifier.fillMaxWidth(0.58f).clickable { editAddress() }, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (current.desktopMode) "PC" else "M", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 4.dp))
                    Text(current.url, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.width(56.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    ToolbarKey("‹", "戻る", { current.webView.goBack() }, current.canGoBack)
                    ToolbarKey("›", "進む", { current.webView.goForward() }, current.canGoForward)
                    ToolbarKey("↻", "更新／停止", { if (current.progress in 1..99) current.webView.stopLoading() else current.webView.reload() })
                    ToolbarKey("⌂", "ホーム", { navigate(HOME_URL) })
                    ToolbarKey("URL", "URLを編集", { editAddress() })
                    ToolbarKey("★", "ブックマーク", { panel = "bookmarks" })
                    ToolbarKey("↓", "ダウンロード", {
                        MediaDetection.read(current.webView, current.title, current.url) { media ->
                            current.detectedMedia.clear(); current.detectedMedia.addAll(media)
                        }
                        panel = "downloads"
                    })
                    ToolbarKey("▶", "ストリーミング動画", { panel = "streams" })
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
                    LazyColumn(Modifier.weight(1f), state = compactTabListState) {
                    items(tabs, key = { it.id }) { tab ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().height(56.dp).pointerInput(tab.id) {
                                var distance = 0f
                                val threshold = 48.dp.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = { distance = 0f },
                                    onDragCancel = { distance = 0f },
                                    onDragEnd = {
                                        when { distance < -threshold -> { select(tab); expandedTabs = true }; distance > threshold -> close(tab) }
                                        distance = 0f
                                    },
                                    onHorizontalDrag = { change, amount -> distance += amount; change.consume() },
                                )
                            }.background(if (tab.id == current.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { finishEditing(); select(tab) },
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
                LazyColumn(Modifier.weight(1f), state = expandedTabListState) {
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
    downloads: DownloadRepository,
    initialUrl: String = HOME_URL,
    loadImmediately: Boolean = true,
    youtubeEnabled: Boolean = true,
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
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(true)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }
    tab = BrowserTab(id, webView).apply {
        url = initialUrl
        pendingUrl = if (loadImmediately) null else initialUrl
        this.youtubeEnabled = youtubeEnabled
    }
    platform.applyDisplayPolicy(webView, initialUrl)
    tab.desktopMode = platform.isDesktop(webView)
    val initialYoutubeLevel = blockingPreferences.level(FilterRules.hostOf(initialUrl))
    configureYoutubeEarly(tab, youtubeEnabled && initialYoutubeLevel != BlockingLevel.OFF)
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.isForMainFrame && FilterRules.domainMatches(request.url.host.orEmpty(), "youtube.com")) {
                val targetLevel = blockingPreferences.level(request.url.host.orEmpty())
                configureYoutubeEarly(tab, tab.youtubeEnabled && targetLevel != BlockingLevel.OFF)
            }
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
            tab.desktopMode = platform.isDesktop(view)
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
            MediaDetection.read(view, tab.title, url) { media ->
                if (tab.isActive || tab.detectedMedia.isEmpty()) {
                    tab.detectedMedia.clear()
                    tab.detectedMedia.addAll(media)
                }
            }
            view.evaluateJavascript(PageScripts.mediaGate(tab.isActive), null)
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
        val id = downloads.enqueue(url, userAgent, contentDisposition, mimeType, webView.url)
        android.widget.Toast.makeText(context, if (id == null) "ダウンロードを開始できません" else "ダウンロードを開始しました", android.widget.Toast.LENGTH_SHORT).show()
    }
    platform.installLinkMenu(webView)
    if (loadImmediately) webView.loadUrl(initialUrl)
    return tab
}

private fun configureYoutubeEarly(tab: BrowserTab, enabled: Boolean) {
    tab.youtubeEarlyScript?.remove()
    tab.youtubeEarlyScript = null
    if (enabled && androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
        tab.youtubeEarlyScript = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
            tab.webView, PageScripts.youtubeDocumentStart, setOf("https://*.youtube.com"),
        )
    }
}

internal fun normalizeUrl(input: String): String {
    val value = input.trim()
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    return if (value.contains('.') && !value.contains(' ')) "https://$value"
    else "https://www.google.com/search?q=${URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")}"
}

internal fun replacementIndexAfterClose(closedIndex: Int, remainingCount: Int): Int =
    closedIndex.coerceIn(0, (remainingCount - 1).coerceAtLeast(0))

private fun downloadStatus(item: BrowserDownload): String = when (item.status) {
    DownloadManager.STATUS_SUCCESSFUL -> "完了"
    DownloadManager.STATUS_FAILED -> "失敗"
    DownloadManager.STATUS_PAUSED -> "一時停止"
    DownloadManager.STATUS_PENDING -> "待機中"
    else -> if (item.total > 0) "${item.downloaded * 100 / item.total}%" else "ダウンロード中"
}

@Composable
private fun MediaThumbnail(url: String, modifier: Modifier = Modifier.fillMaxWidth().height(126.dp)) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, url) {
        if (url.startsWith("https://") || url.startsWith("http://")) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val connection = java.net.URL(url).openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }
                connection.getInputStream().use { input -> android.graphics.BitmapFactory.decodeStream(input) }
            }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("動画") }
}

private fun formatDuration(ms: Long): String = if (ms <= 0) "時間不明" else "%d:%02d".format(ms / 60000, ms / 1000 % 60)
private fun formatBytes(bytes: Long): String = if (bytes <= 0) "サイズ推定中" else "%.1f MB".format(bytes / 1048576.0)

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
