# Android Browser Phase 3 — 修正コードとCodex向け指示

対象: `android-browser-codex-compare-zip-contents-with-current-repository.zip`
基準コミット: `06923dcc923d8db7dbe7b5e9e39d859dfecde57d`

## Codexに貼る指示

この `CODEX_PHASE3.md` の仕様・ファイル別コードを現在の修正ブランチへ適用してください。
`CODEX_PHASE2.md` を再適用しないでください。先にREADME・既存コード・CI・AGENTS.mdを確認してください。
下記の基準SHA-256と現在の各ファイルが一致する場合は置換できます。
一致しない場合は新しい修正が存在する可能性があるため、差分を読み、今回の変更だけを統合してください。
Xログイン・Fantia購入済み動画の動作は改善済みとして維持し、UA・Cookie設定を全サイトへ一律変更しないでください。
ニコ動の「プレイヤー以外が表示されない」は、ユーザーが広告ブロックと無関係と確認済みです。
広告解除を解決策にしないでください。診断結果と再現手順なしに原因を断定しないでください。
有料API、サーバー、外部有料サービスは追加しません。
適用後に `node scripts/test-youtube.cjs` と `gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest` を実行してください。
コンパイルエラーや既存テストの失敗は修正し、テスト結果・APK生成結果・未解決点を報告してください。
実機がなければニコ動、Google画像アップロード、YouTube広告除去を実機確認済みと報告しないでください。

## 実装内容

| 項目 | 今回の変更 |
|---|---|
| 左バー | 盾・鍵をベクター描画のアイコンに変更。長押しで機能名を表示 |
| URLコピー | コピーアイコン化、コピー完了通知 |
| 新規タブ | 左から削除し、通常・展開タブバーの最上部へ配置 |
| タブ終了 | 展開時に右ドラッグ。移動量72dp超で終了。左ドラッグ・縦スクロールは終了扱いにしない |
| 閉じたタブ | バー最下部から直前のタブを復元。長押しで最大30件の一覧。プロフィール別に保存 |
| セッション | URL・タイトル・並び順・選択中ID・閉じたタブをAtomicFileに保存。表示状態更新時に重複を除いて非同期保存。終了前は書込み完了を待つ |
| 再起動 | 選択タブから読み込み。ほかの復元タブは選択までURLを読み込まない |
| 復帰 | 生存するWebViewを再利用し、復帰だけではページをリロードしない。動画エラー時に再試行ボタン |
| 描画プロセス終了 | 当該WebViewを破棄・差し替え、URLを残してユーザー操作で復元 |
| 標準ブロック | EasyList＋AdGuard日本語フィルター、通常CSSによる広告要素非表示 |
| 強力ブロック | 標準にEasyPrivacy＋AdGuard DNSフィルターを追加 |
| 更新 | 初回・前回更新から7日経過後の起動時、および手動。全ソース成功後に一括反映。失敗時は旧スナップショット維持 |
| フィルター判定 | ホスト・サブドメイン、URLパターン、例外、domain条件、一部リソース種別。ルール数・未対応件数・更新日時・直近遮断ホストを表示 |
| YouTube | 独立した実験スイッチ。ページ内広告を非表示、スキップボタンを操作。明示的な広告状態かつ有限の短い広告動画だけ末尾へシークを試行 |
| Google画像検索 | 設定からWeb版の画像検索を開く。この操作だけPC向けUAに切替。Google外へ移る際は元に戻す。アプリストアへのfallbackを停止 |
| ニコ動 | 全画面解除時の再レイアウト、通常watchページへの移動、タブ単位のPC/モバイル切替、読み込み・動画・表示領域の診断 |

## 限界・未解決項目（省略禁止）

- **ニコ動の原因は未確定です。今回追加したのは表示復帰処理と切り分け手段であり、症状の解消を実証していません。**
- **広告エンジンは主要ブロッカー互換の完成版ではありません。** 今回は既存実装を拡張しています。成熟したエンジンの移植は未完了です。
- `@@…$document`によるページ全体許可、`third-party`（完全なPSL・フレームの起点が必要）、remote scriptlet、redirect/rewrite、手続き型CSS、正規表現フィルターなどは未対応です。条件を捨てて広い遮断ルールに変換せず、非対応として数えます。
- リソース種別は明示的な `Sec-Fetch-Dest` から判定します。分からない場合、種別制限付きルールは適用しません。URL拡張子で推測しません。
- ホスト専用ルールは集合で検索し、URLパターンは事前コンパイルします。汎用遮断パターンは4,096件（例外はこの上限で捨てない）、パターン長512文字、ワイルドカード3個までに制限します。超過は非対応件数に入ります。
- CSSは最大3,000セレクターで、CSSOMへ1件ずつ追加して無効なセレクターが全体を壊さないようにします。iframe内の表示除去やあらゆる再生成に完全対応するものではありません。
- **YouTubeはページ内広告・スキップ補助です。動画前／途中の広告がすべて消える保証はありません。** スクリプトは再生ページの広告表示マーカーに依存し、仕様変更に弱い実験機能です。不具合時は専用スイッチをオフにできます。配信ドメイン一括遮断、認証回避、DRM回避は行いません。
- **Google画像アップロードも実機確認が必要です。** 設定の「Google画像検索（Web版）」からカメラアイコン→ファイルアップロードを試します。ダウンロードページを開かないことと、アップロード成功は別に確認します。
- WebViewは元コードでもActivityのバックグラウンド移行だけでは破棄されていませんでした。今回の主な追加は再表示・失敗検出・再試行・セッション保存です。OSによる停止やプロセス終了中の動画準備を保証するものではありません。
- 保存するのはタブのURL等です。フォーム内容、POST結果、動画再生位置、ページ履歴全体、サムネイルは再起動後に復元しません。Cookie領域は従来どおりプロフィール別に維持します。
- 復元時は全ページの通信を開始しませんが、WebViewオブジェクト自体はタブごとに作成します。多数タブでの完全な遅延生成・自動休止は後続です。
- 別アプリから戻るだけで自動リロードして未送信フォームを失う変更は入れていません。再試行はユーザー操作です。

## 実機確認（Galaxy Tab S9 FE）

1. タブを3つ開き中央を選択→プロフィール選択へ戻る→同じプロフィール。URL・順序・選択中を確認。別プロフィールに混入しないことも確認。
2. 展開タブを右へ72dp以上動かして閉じる。縦スクロールや左フリックでは閉じない。最下部から復元、長押しで履歴選択。最後の1タブを閉じても操作可能なことを確認。
3. 画面回転・バックグラウンド移行・通常のプロセス終了後の再起動でタブが残ることを確認。保存後の復元であり、読み込み状態の完全保持ではない。
4. 重い動画を準備中に他アプリへ移動し、30秒・2分後に復帰。準備継続／再生失敗／プロセス終了を区別し、エラー時の再試行を確認。
5. ニコ動の問題URLで「表示診断」をコピー。URLのホスト・path、documentSizeとviewport、動画ready/network/error、fullscreenを記録。通常watchページ・PC/モバイル表示の各結果を比較。広告ブロックを原因とする扱いには戻さない。
6. 設定→Google画像検索（Web版）→カメラ→アップロード。システムのファイル選択、キャンセル、写真選択、Googleアプリ案内へ飛ばないことを確認。
7. フィルター更新の完了・更新日時・ルール数を確認。標準／強力は同じURLをリロードして比較。遮断数はページ全体の広告除去率ではない。
8. YouTubeのログイン有無・通常動画・連続再生・シーク・全画面・動画前広告・途中広告を確認。広告が残る／本編が飛ぶ／再生不能なら専用スイッチをオフにして記録。
9. XログインとFantia購入履歴からの動画再生を回帰確認。これらのサイトへPC用UAを一律適用しない。
10. 指紋・パスワード保存は今回有効化しない。既存の認証仕様・判定コードを維持する。

## 後続の要件

- タブ数やメモリ使用量に応じた休止、その後に任意の上限を超えた古いタブの自動削除。選択中・固定・再生中・未送信入力の保護が先。
- ブラウザ内でJavaScriptを貼り付け・保存し、許可したサイトで実行する拡張機能。GitHub経由を不要にする。
- 最初の拡張はX／Instagramのユーザーページで、閲覧権限のある画像・動画を整理して一覧表示する機能。
- この拡張は今回は未実装。自動実行先は正確なホスト単位で許可し、全サイトへの一律注入・Androidブリッジ公開・他プロフィールのCookie読出しはしない。
- 成熟した広告エンジン移植について、Android WebViewとの接続方法・保守状態・ライセンス・APKサイズ・WebViewで捕捉できない通信を評価する。

## 参考資料

- Android WebView API: https://developer.android.com/reference/android/webkit/WebView
- Google画像検索（PCのアップロード手順）: https://support.google.com/websearch/answer/1325808?co=GENIE.Platform%3DDesktop&hl=ja
- ABPフィルター構文: https://help.adblockplus.org/adblock-plus-help-center/how-to-write-filters
- AdGuard Filters Registry: https://github.com/AdguardTeam/FiltersRegistry
- AdGuard DNSフィルター: https://github.com/AdguardTeam/AdGuardSDNSFilter
- フィルターは各提供元のHTTPSからデータとして取得します。提供元のライセンス・配布条件は配布時にも維持してください。ダウンロードしたJavaScriptは実行しません。

## 検証結果

- 最終版 `NetworkRules.java` をJavaコンパイラでコンパイルし、判定条件32件＋20,000ホスト登録の検査が成功。
- 実装中のYouTubeスクリプトそのものをNode.jsで実行し、模擬DOMによる9ケースが成功。本編非広告・対象外ホスト・非表示タブを操作しないこと、広告スキップ・停止処理などを検査。
- 中断前に `compileDebugKotlin` がコンパイルエラーなく進んだことを確認（アイコン描画APIの非推奨警告あり）。その後の復帰処理等の小修正を含む最終版全体は、Androidビルドの再実行が必要。
- **Gradle単体テスト一式・APK生成・実機用テストAPK生成は完了未確認。** ビルド途中で実行環境が切り替わり、一時ログとプロセスを失ったため、成功扱いにしない。
- **実機試験・YouTube実サイト・Google画像アップロード・ニコ動の症状解消は未確認。**
- CIにNode.jsの模擬テストと `assembleDebugAndroidTest` を追加。実機用テストAPKのビルドと、端末上でのテスト実行は別です。

## 基準ファイルのSHA-256

元ZIPのハッシュです。`null` は新規ファイルです。

```json
{
  ".github/workflows/android.yml": "8bb8c8674dabe966b4d45455c4527610950534f3db34a891da5226b7901c5a2d",
  "app/src/androidTest/java/com/example/privatebrowser/TabSessionStoreTest.kt": null,
  "app/src/main/java/com/example/privatebrowser/BrowserActivity.kt": "4b38b927b811a31ec9c5f3081c8e027b38d27084c7a8710aeb39227407a7ccde",
  "app/src/main/java/com/example/privatebrowser/BrowserPlatform.kt": "587ca446c6e60e61ed5c9277ee3be1b41863e0d75debb026da94a11ed3ef4b8c",
  "app/src/main/java/com/example/privatebrowser/FilterRules.kt": "da0760e8fbccc15dba807ae0cbfaf561ede845d1ddd3146694168e4c3ebdfbde",
  "app/src/main/java/com/example/privatebrowser/FilterStore.kt": "6f3af1c8f76933f53fdb9ccf413451ee2be65f81bcfa51e51d4d2f248fe8629c",
  "app/src/main/java/com/example/privatebrowser/NetworkRules.java": null,
  "app/src/main/java/com/example/privatebrowser/PageScripts.kt": null,
  "app/src/main/java/com/example/privatebrowser/TabSessionStore.kt": null,
  "app/src/test/java/com/example/privatebrowser/BrowserPlatformTest.kt": "bff4d6e4e2e047f21a7781b9cbdda19a33ea16f015eae550c7987354cffadd53",
  "app/src/test/java/com/example/privatebrowser/FilterRulesTest.kt": "0e4bdbe187fe7635e740ca39ae38c64639be089e711fc3b20fb789a08246f4ed",
  "app/src/test/java/com/example/privatebrowser/NetworkRulesChecks.java": null,
  "app/src/test/java/com/example/privatebrowser/NetworkRulesTest.kt": null,
  "scripts/test-youtube.cjs": null
}
```

## 適用するファイル

各ブロックは指定パスの全文です。現在のコードと基準ハッシュが一致する場合、ブロックをプログラムで抽出して適用できます。コードを手書きで再生成する必要はありません。ハッシュが異なる場合は差分統合してください。

### `.github/workflows/android.yml`

<!-- file: .github/workflows/android.yml -->
```yaml
name: Android CI

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-24.04

    steps:
      - name: Check out repository
        uses: actions/checkout@v5

      - name: Set up Java 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v6
        with:
          gradle-version: '8.11.1'

      - name: Configure Android SDK environment
        shell: bash
        run: |
          echo "ANDROID_HOME=${RUNNER_TEMP}/android-sdk" >> "${GITHUB_ENV}"
          echo "ANDROID_SDK_ROOT=${RUNNER_TEMP}/android-sdk" >> "${GITHUB_ENV}"

      - name: Install Android SDK command-line tools
        shell: bash
        run: |
          set -euo pipefail

          mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"

          curl --fail \
            --location \
            --retry 3 \
            --output "${RUNNER_TEMP}/commandlinetools.zip" \
            "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"

          mkdir -p "${RUNNER_TEMP}/android-commandline-tools"

          unzip -q \
            "${RUNNER_TEMP}/commandlinetools.zip" \
            -d "${RUNNER_TEMP}/android-commandline-tools"

          mv \
            "${RUNNER_TEMP}/android-commandline-tools/cmdline-tools" \
            "${ANDROID_SDK_ROOT}/cmdline-tools/latest"

          echo "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin" >> "${GITHUB_PATH}"
          echo "${ANDROID_SDK_ROOT}/platform-tools" >> "${GITHUB_PATH}"

      - name: Accept Android SDK licenses
        shell: bash
        run: |
          yes | "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" \
            --sdk_root="${ANDROID_SDK_ROOT}" \
            --licenses > /dev/null || true

      - name: Install Android SDK packages
        shell: bash
        run: |
          "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" \
            --sdk_root="${ANDROID_SDK_ROOT}" \
            "platform-tools" \
            "platforms;android-36" \
            "build-tools;36.0.0"

      - name: Test bundled YouTube script
        run: node scripts/test-youtube.cjs

      - name: Test and build debug APK
        run: gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: private-browser-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
```

### `app/src/androidTest/java/com/example/privatebrowser/TabSessionStoreTest.kt`

<!-- file: app/src/androidTest/java/com/example/privatebrowser/TabSessionStoreTest.kt -->
```kotlin
package com.example.privatebrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabSessionStoreTest {
    @Test fun persistsOrderSelectionClosedTabsAndProfileIsolation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstId = "session-test-" + java.util.UUID.randomUUID()
        val secondId = "session-test-" + java.util.UUID.randomUUID()
        val first = TabSessionStore(context, firstId)
        val second = TabSessionStore(context, secondId)
        try {
            val tabs = listOf(SavedTab(8, "https://example.com/a", "A"), SavedTab(3, "https://example.com/b", "B"))
            val session = TabSession(tabs, 3, listOf(SavedTab(2, "https://example.com/closed", "Closed")))
            first.save(session); first.flush()
            assertEquals(session, first.read())
            assertTrue(second.read().tabs.isEmpty())
            first.save(session.copy(selected = 8)); first.flush()
            assertEquals(8L, first.read().selected)
            assertNull(first.error)
        } finally {
            first.dispose(); second.dispose()
            listOf(firstId, secondId).forEach { id ->
                val file = java.io.File(context.filesDir, "tabs_${BrowserActivity.profileSuffix(id)}.json")
                android.util.AtomicFile(file).delete()
            }
        }
    }
}
```

### `app/src/main/java/com/example/privatebrowser/BrowserActivity.kt`

<!-- file: app/src/main/java/com/example/privatebrowser/BrowserActivity.kt -->
```kotlin
package com.example.privatebrowser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Process
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
        platform = BrowserPlatform(this)
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
        persistSession?.invoke()
        shortcutHandler = null
        if (::platform.isInitialized) platform.dispose()
        super.onDestroy()
        // A profile owns this dedicated process. End it only when explicitly leaving the profile;
        // configuration changes are handled by Compose disposal without killing the new Activity.
        if (isFinishing) Process.killProcess(Process.myPid())
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
        @Volatile private var configuredSuffix: String? = null

        internal fun profileSuffix(id: String): String = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserScreen(profileId: String, profileName: String, closeProfile: () -> Unit) {
    val context = LocalContext.current
    val activity = context as BrowserActivity
    val platform = activity.platform
    val focus = LocalFocusManager.current
    val addressFocus = remember { FocusRequester() }
    val preferences = remember(profileId) { context.getSharedPreferences("ui_" + BrowserActivity.profileSuffix(profileId), Context.MODE_PRIVATE) }
    val filters = remember { FilterStore(context.applicationContext) }
    val blockingPreferences = remember(profileId) { SiteBlockingPreferences(context, profileId) }
    val sessionStore = remember(profileId) { TabSessionStore(context.applicationContext, profileId) }
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
                        TextButton(
                            onClick = { current.capture(); finishEditing(); expandedTabs = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(containerColor = if (tab.id == current.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
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
```

### `app/src/main/java/com/example/privatebrowser/BrowserPlatform.kt`

<!-- file: app/src/main/java/com/example/privatebrowser/BrowserPlatform.kt -->
```kotlin
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
```

### `app/src/main/java/com/example/privatebrowser/FilterRules.kt`

<!-- file: app/src/main/java/com/example/privatebrowser/FilterRules.kt -->
```kotlin
package com.example.privatebrowser

import java.net.URI

/** URL patterns and standard CSS; unsupported extension-only syntax is counted. */
internal data class FilterRules(
    val blockedHosts: Set<String> = emptySet(),
    val allowedHosts: Set<String> = emptySet(),
    val cosmetic: Map<String, Set<String>> = emptyMap(),
    val cosmeticExceptions: Map<String, Set<String>> = emptyMap(),
    val skipped: Int = 0,
    val network: NetworkRules = NetworkRules(),
) {
    fun decision(url: String, pageHost: String = "", type: String = ""): Int = network.decision(url, pageHost, type)
    fun blocks(url: String): Boolean = decision(url) == 1

    fun selectors(host: String): Set<String> {
        val hidden = cosmetic.filterKeys { it.isEmpty() || domainMatches(host, it) }.values.flatten().toSet()
        val exceptions = cosmeticExceptions.filterKeys { it.isEmpty() || domainMatches(host, it) }.values.flatten().toSet()
        return hidden - exceptions
    }

    companion object {
        private val domain = Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,}")
        private val selector = Regex("[.#][A-Za-z_][A-Za-z0-9_-]*")

        private fun safeSelector(value: String): Boolean = value.length in 1..512 &&
            value.none { it in "{};@\\\n\r" } && !value.contains("/*") && !value.contains("+js(") &&
            !value.contains(":-abp-") && !value.contains(":has-text(") && !value.contains(":style(") &&
            !value.startsWith("^") && !value.startsWith("//")

        fun parse(lines: Sequence<String>): FilterRules {
            val deny = mutableSetOf<String>()
            val allow = mutableSetOf<String>()
            val hide = mutableMapOf<String, MutableSet<String>>()
            val show = mutableMapOf<String, MutableSet<String>>()
            var skipped = 0
            val network = NetworkRules()
            lines.forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() || line.startsWith("!") || line.startsWith("[") -> Unit
                    line.contains("#@#") || line.contains("##") -> {
                        val exception = line.contains("#@#")
                        val pieces = line.split(if (exception) "#@#" else "##", limit = 2)
                        val domains = pieces[0].lowercase().split(',')
                        if (!safeSelector(pieces[1]) || domains.any { it.isNotEmpty() && !domain.matches(it) }) skipped++
                        else domains.forEach { host ->
                            (if (exception) show else hide).getOrPut(host) { mutableSetOf() }.add(pieces[1])
                        }
                    }
                    else -> {
                        val exception = line.startsWith("@@")
                        val rule = line.removePrefix("@@")
                        val host = rule.removePrefix("||").removeSuffix("^").lowercase()
                        if (network.add(line)) {
                            if (rule.startsWith("||") && rule.endsWith("^") && domain.matches(host)) (if (exception) allow else deny).add(host)
                        } else skipped++
                    }
                }
            }
            return FilterRules(deny.toSet(), allow.toSet(), hide.mapValues { it.value.toSet() }, show.mapValues { it.value.toSet() }, skipped, network)
        }

        fun hostOf(url: String): String = runCatching {
            val uri = URI(url)
            if (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) uri.host.orEmpty().lowercase().trimEnd('.') else ""
        }.getOrDefault("")

        fun domainMatches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

        private fun matches(host: String, hosts: Set<String>): Boolean {
            var suffix = host
            while (suffix.isNotEmpty()) {
                if (suffix in hosts) return true
                suffix = suffix.substringAfter('.', "")
            }
            return false
        }
    }
}
```

### `app/src/main/java/com/example/privatebrowser/FilterStore.kt`

<!-- file: app/src/main/java/com/example/privatebrowser/FilterStore.kt -->
```kotlin
package com.example.privatebrowser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Executors
import org.json.JSONObject

internal data class FilterSnapshot(val base: FilterRules = FilterRules(), val tracking: FilterRules = FilterRules(), val updatedAt: Long = 0)

/** Lists are data only. Publish one immutable snapshot after all sources succeed. */
internal class FilterStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "filter-snapshot-v3.json"))
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var snapshot = FilterSnapshot()
        private set
    val rules: FilterRules get() = snapshot.base
    @Volatile var status = "未更新（同梱ルールのみ）"
        private set
    @Volatile private var disposed = false
    private val sources = linkedMapOf(
        "base" to "https://easylist.to/easylist/easylist.txt",
        "japanese" to "https://filters.adtidy.org/extension/chromium/filters/7.txt",
        "privacy" to "https://easylist.to/easylist/easyprivacy.txt",
        "dns" to "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
    )
    init {
        worker.execute {
            runCatching { file.openRead().bufferedReader().use { install(JSONObject(it.readText())) } }
            if (System.currentTimeMillis() - snapshot.updatedAt > 7L * 24 * 60 * 60 * 1000) refresh()
        }
    }
    private fun parsed(json: JSONObject): FilterSnapshot = FilterSnapshot(
        FilterRules.parse((json.getString("base") + "\n" + json.getString("japanese")).lineSequence()),
        FilterRules.parse((json.getString("privacy") + "\n" + json.getString("dns")).lineSequence()),
        json.getLong("updatedAt"),
    )
    private fun install(json: JSONObject) { snapshot = parsed(json); status = summary() }
    fun summary(): String {
        val current = snapshot
        val time = if (current.updatedAt == 0L) "未更新" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(current.updatedAt))
        return "標準 ${current.base.network.size()} / 強力追加 ${current.tracking.network.size()} 通信ルール\n" +
            "非対応 ${current.base.skipped + current.tracking.skipped} / 更新 $time"
    }
    fun decision(url: String, pageHost: String, type: String, level: BlockingLevel): Int {
        if (level == BlockingLevel.OFF) return 0
        val current = snapshot
        val base = current.base.decision(url, pageHost, type)
        val privacy = if (level == BlockingLevel.STRICT) current.tracking.decision(url, pageHost, type) else 0
        return if (base == -1 || privacy == -1) -1 else if (base == 1 || privacy == 1) 1 else 0
    }
    fun update(done: (String) -> Unit) {
        if (disposed) return
        worker.execute { refresh(); if (!disposed) main.post { if (!disposed) done(status) } }
    }
    private fun refresh() {
        status = "フィルター更新中"
        runCatching {
            val json = JSONObject()
            sources.forEach { (name, url) -> json.put(name, download(url)) }
            json.put("updatedAt", System.currentTimeMillis())
            val candidate = parsed(json)
            check(candidate.base.network.size() > 0 && candidate.tracking.network.size() > 0)
            val output = file.startWrite()
            try { output.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (e: Exception) { file.failWrite(output); throw e }
            snapshot = candidate
            status = summary()
        }.onFailure { status = "更新失敗。前のルールを維持します\n" + summary() }
    }
    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 15000
        connection.instanceFollowRedirects = false
        return try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 12 * 1024 * 1024)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            bytes.toString(Charsets.UTF_8).also {
                check(it.trimStart().startsWith("[Adblock") || it.trimStart().startsWith("!"))
                check(!it.contains("<html", ignoreCase = true))
            }
        } finally { connection.disconnect() }
    }
    fun dispose() { disposed = true; worker.shutdownNow() }
}
```

### `app/src/main/java/com/example/privatebrowser/NetworkRules.java`

<!-- file: app/src/main/java/com/example/privatebrowser/NetworkRules.java -->
```java
package com.example.privatebrowser;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Indexed ABP URL rules. Unsupported modifiers are rejected, never broadened.
 * No remote scripts, redirect resources, response rewriting or browser-extension API emulation.
 */
public final class NetworkRules {
    private final Map<String,List<Rule>> hosts = new HashMap<>();
    private final Set<String> directDeny = new HashSet<>(), directAllow = new HashSet<>();
    private final List<Rule> generic = new ArrayList<>();
    private int size;
    private static final Pattern TRAILING_HOST_DOT = Pattern.compile("(://[^/?#:]+)\\.(?=[:/?#]|$)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
        "script", "image", "stylesheet", "font", "media", "subdocument", "document", "xmlhttprequest", "other"));
    public int size() { return size; }
    public boolean add(String input) {
        boolean exception = input.startsWith("@@");
        String value = exception ? input.substring(2) : input;
        String[] parts = value.split("\\$", 2);
        String pattern = parts[0];
        if (pattern.chars().filter(c -> c == '*').count() > 3 || pattern.isEmpty() || pattern.length() > 512 || pattern.startsWith("/") && pattern.endsWith("/")) return false;
        Set<String> include = new HashSet<>(), exclude = new HashSet<>(), types = new HashSet<>(), excludedTypes = new HashSet<>();
        boolean matchCase = false;
        if (parts.length == 2) for (String option : parts[1].split(",")) {
            if (option.equals("match-case")) matchCase = true;
            else if (option.startsWith("domain=")) {
                for (String domain : option.substring(7).split("\\|")) {
                    boolean negative = domain.startsWith("~");
                    String host = negative ? domain.substring(1) : domain;
                    if (!host.matches("[a-zA-Z0-9.-]+") || host.isEmpty()) return false;
                    (negative ? exclude : include).add(host.toLowerCase(Locale.ROOT));
                }
            } else if (TYPES.contains(option)) types.add(option);
            else if (option.startsWith("~") && TYPES.contains(option.substring(1))) excludedTypes.add(option.substring(1));
            else return false; // e.g. third-party needs a full PSL + frame origin, so do not guess.
        }
        // ABP's exception $document disables filtering for the whole page; request-only
        // matching cannot implement that semantic. Reject it rather than misinterpret it.
        if (exception && types.contains("document")) return false;
        // Host-only lists can contain hundreds of thousands of entries. Keep them as
        // suffix sets, not one compiled regular expression per hostname.
        if (parts.length == 1 && pattern.matches("\\|\\|[a-zA-Z0-9.-]+\\^")) {
            String host = pattern.substring(2, pattern.length()-1).toLowerCase(Locale.ROOT);
            if (!host.contains(".")) return false;
            if ((exception ? directAllow : directDeny).add(host)) size++;
            return true;
        }
        String hostKey = "";
        String expression;
        if (pattern.startsWith("||")) {
            String tail = pattern.substring(2);
            int i = 0;
            while (i < tail.length() && (Character.isLetterOrDigit(tail.charAt(i)) || tail.charAt(i)=='.' || tail.charAt(i)=='-')) i++;
            hostKey = tail.substring(0, i).toLowerCase(Locale.ROOT);
            if (!hostKey.contains(".") || (i < tail.length() && "^/:|".indexOf(tail.charAt(i)) < 0)) return false;
            // Require an actual hostname boundary; never match example.com.evil.test.
            expression = "^https?://(?:[^/?#:.]+\\.)*" + Pattern.quote(hostKey) + "(?=[:/?#]|$)" + compileGlob(tail.substring(i));
        } else {
            boolean left = pattern.startsWith("|");
            expression = (left ? "^" : "") + compileGlob(left ? pattern.substring(1) : pattern);
        }
        try {
            Rule rule = new Rule(Pattern.compile(expression, matchCase ? 0 : Pattern.CASE_INSENSITIVE), exception, include, exclude, types, excludedTypes, pattern);
            if (hostKey.isEmpty()) {
                // Bound generic scans, counted as unsupported if capacity is exceeded.
                if (generic.size() >= 4096 && !exception) return false;
                generic.add(rule);
            } else hosts.computeIfAbsent(hostKey, ignored -> new ArrayList<>()).add(rule);
            size++;
            return true;
        } catch (RuntimeException ignored) { return false; }
    }
    private static String compileGlob(String pattern) {
        StringBuilder out = new StringBuilder();
        for (int i=0; i<pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c=='*') {
                if (i==0 || pattern.charAt(i-1)!='*') out.append(".*");
            } else if (c=='^') out.append("(?:[^a-zA-Z0-9_.%-]|$)");
            else if (c=='|' && i==pattern.length()-1) out.append('$');
            else out.append(Pattern.quote(String.valueOf(c)));
        }
        return out.toString();
    }
    public int decision(String url, String pageHost, String type) {
        if (url.length() > 16384) return 0;
        String host;
        try { URI uri = new URI(url); host = uri.getHost(); if (host==null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return 0; }
        catch (Exception ignored) { return 0; }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length()-1);
        // Canonicalize a trailing DNS dot for matching while preserving path/query case.
        url = TRAILING_HOST_DOT.matcher(url).replaceFirst("$1");
        boolean blocked = false;
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        String suffix = host;
        while (!suffix.isEmpty()) {
            if (directAllow.contains(suffix)) return -1;
            if (directDeny.contains(suffix)) blocked = true;
            List<Rule> candidates = hosts.get(suffix);
            if (candidates != null) for (Rule rule : candidates) if (rule.matches(url, lowerUrl, pageHost, type)) {
                if (rule.exception) return -1;
                blocked = true;
            }
            int dot=suffix.indexOf('.'); suffix=dot<0 ? "" : suffix.substring(dot+1);
        }
        for (Rule rule : generic) if (rule.matches(url,lowerUrl,pageHost,type)) {
            if (rule.exception) return -1;
            blocked=true;
        }
        return blocked ? 1 : 0;
    }
    private static boolean matchesDomains(String host, Set<String> domains) {
        for (String d:domains) if (host.equals(d) || host.endsWith("."+d)) return true;
        return false;
    }
    private static final class Rule {
        final Pattern pattern; final boolean exception; final String needle;
        final Set<String> include, exclude, types, excludedTypes;
        Rule(Pattern p, boolean e, Set<String> i, Set<String> x, Set<String> t, Set<String> xt, String source) {
            pattern=p;exception=e;include=i;exclude=x;types=t;excludedTypes=xt;
            String longest="";
            for (String token:source.split("[|*^]")) if (token.length()>longest.length()) longest=token;
            needle=longest.toLowerCase(Locale.ROOT);
        }
        boolean matches(String url,String lowerUrl,String pageHost,String type) {
            if (!lowerUrl.contains(needle)) return false;
            if ((!include.isEmpty() || !exclude.isEmpty()) && pageHost.isEmpty()) return false;
            if (!include.isEmpty() && !matchesDomains(pageHost,include) || matchesDomains(pageHost,exclude)) return false;
            // Unknown request type must not turn a constrained rule into an unconditional rule.
            if ((!types.isEmpty() || !excludedTypes.isEmpty()) && type.isEmpty()) return false;
            if (!types.isEmpty() && !types.contains(type) || excludedTypes.contains(type)) return false;
            return pattern.matcher(url).find();
        }
    }
}
```

### `app/src/main/java/com/example/privatebrowser/PageScripts.kt`

<!-- file: app/src/main/java/com/example/privatebrowser/PageScripts.kt -->
```kotlin
package com.example.privatebrowser

/** Bundled, auditable scripts only; filter downloads cannot supply JavaScript. */
internal object PageScripts {
    fun cosmetic(selectors: Set<String>): String {
        val array = org.json.JSONArray(selectors.take(3000).toList()).toString()
        return """
            (function(){
                var old=document.getElementById('private-browser-ad-style');if(old)old.remove();
                var s=document.createElement('style');s.id='private-browser-ad-style';
                (document.head||document.documentElement).appendChild(s);
                $array.forEach(function(selector){
                    try {s.sheet.insertRule(selector+'{display:none!important}',s.sheet.cssRules.length);}catch(e){}
                });
            })()
        """.trimIndent()
    }
    val youtube = """
        (function(){
            if(!/(^|\.)youtube\.com$/.test(location.hostname)||window.__privateYoutubeAds)return;
            var css=document.createElement('style');css.id='private-youtube-ad-style';
            css.textContent='ytd-ad-slot-renderer,ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytd-in-feed-ad-layout-renderer,ytm-promoted-sparkles-web-renderer{display:none!important}';
            (document.head||document.documentElement).appendChild(css);
            var lastSeek=0, lastSource='', lastVideo=null, lastTime=-1;
            function tick(){
                if(document.hidden)return;
                var player=document.querySelector('.html5-video-player.ad-showing');
                if(!player){lastSource='';lastVideo=null;lastTime=-1;return;}
                var button=player.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern');
                if(button&&button.getClientRects().length&&!button.disabled){button.click();return;}
                var video=player.querySelector('video');
                // Only seek an explicitly marked, finite advertisement. Never touch an unmarked main video.
                if(video&&video.readyState>=1&&Number.isFinite(video.duration)&&video.duration>0&&video.duration<=180){
                    var now=Date.now(), source=video.currentSrc;
                    if(now-lastSeek>1500&&(lastVideo!==video||lastSource!==source||video.currentTime<lastTime-1)){
                        lastSource=source;lastVideo=video;lastTime=video.duration;lastSeek=now;
                        try{video.currentTime=video.duration;}catch(e){}
                    }
                }
            }
            var timer=setInterval(tick,750);
            function stop(){clearInterval(timer);css.remove();delete window.__privateYoutubeAds;}
            window.__privateYoutubeAds={stop:stop};
            addEventListener('pagehide',stop,{once:true});tick();
        })()
    """.trimIndent()
    val stopYoutube = "window.__privateYoutubeAds&&window.__privateYoutubeAds.stop();"
}
```

### `app/src/main/java/com/example/privatebrowser/TabSessionStore.kt`

<!-- file: app/src/main/java/com/example/privatebrowser/TabSessionStore.kt -->
```kotlin
package com.example.privatebrowser

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class SavedTab(val id: Long, val url: String, val title: String)
internal data class TabSession(val tabs: List<SavedTab>, val selected: Long, val closed: List<SavedTab>)

/** Profile-scoped atomic snapshots. No cookies or passwords in this file. */
internal class TabSessionStore(context: Context, profileId: String) {
    private val file = AtomicFile(File(context.filesDir, "tabs_${BrowserActivity.profileSuffix(profileId)}.json"))
    private val worker = Executors.newSingleThreadExecutor()
    private var lastQueued = ""
    @Volatile var error: String? = null
        private set
    fun read(): TabSession = runCatching {
        file.openRead().bufferedReader().use { reader ->
            val json = JSONObject(reader.readText())
            TabSession(decode(json.optJSONArray("tabs")), json.optLong("selected", 1), decode(json.optJSONArray("closed")))
        }
    }.getOrElse { TabSession(emptyList(), 1, emptyList()) }
    private fun decode(array: JSONArray?): List<SavedTab> = buildList {
        if (array != null) for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (FilterRules.hostOf(url).isNotEmpty()) add(SavedTab(item.optLong("id", i + 1L), url, item.optString("title")))
        }
    }.distinctBy { it.id }
    private fun encode(tabs: List<SavedTab>) = JSONArray().apply {
        tabs.forEach { put(JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)) }
    }
    fun save(session: TabSession) {
        val text = JSONObject().put("tabs", encode(session.tabs)).put("selected", session.selected)
            .put("closed", encode(session.closed.takeLast(30))).toString()
        if (text == lastQueued && error == null) return
        lastQueued = text
        worker.execute {
            runCatching {
                val output = file.startWrite()
                try { output.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
                catch (e: Exception) { file.failWrite(output); throw e }
            }.onSuccess { error = null }.onFailure { error = "タブの保存に失敗しました" }
        }
    }
    fun flush() { runCatching { worker.submit {}.get(3, TimeUnit.SECONDS) }.onFailure { error = "タブ保存が完了していません" } }
    fun dispose() { flush(); worker.shutdown() }
}
```

### `app/src/test/java/com/example/privatebrowser/BrowserPlatformTest.kt`

<!-- file: app/src/test/java/com/example/privatebrowser/BrowserPlatformTest.kt -->
```kotlin
package com.example.privatebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPlatformTest {
    @Test fun imageSearchContext() = assertTrue(isImageSearchContext("https://www.google.com/search?q=cat&udm=2"))
    @Test fun unrelatedSearchContext() = assertFalse(isImageSearchContext("https://www.google.com/search?q=cat"))
    @Test fun fakeImageHost() = assertFalse(isImageSearchContext("https://www.google.com.evil.test/imghp"))
    @Test fun recognizesAppStore() = assertTrue(isAppDownloadUrl("https://play.google.com/store/apps/details?id=test"))
    @Test fun doesNotMatchFakeStoreHost() = assertFalse(isAppDownloadUrl("https://play.google.com.evil.test/store"))
    @Test fun acceptsImageSearchWeb() = assertFalse(isAppDownloadUrl("https://www.google.com/imghp"))
    @Test fun googleHostBoundary() = assertFalse(isGoogleWebHost("google.com.evil.test"))
    @Test fun acceptsHttpsFallback() = assertTrue(safeHttpsFallback("https://example.com/?q=a%2Fb"))
    @Test fun rejectsJavascript() = assertFalse(safeHttpsFallback("javascript:alert(1)"))
    @Test fun rejectsLocalFile() = assertFalse(safeHttpsFallback("file:///sdcard/private.txt"))
    @Test fun rejectsContentUri() = assertFalse(safeHttpsFallback("content://documents/1"))
    @Test fun rejectsIntentLoop() = assertFalse(safeHttpsFallback("intent://example.com"))
    @Test fun rejectsCleartext() = assertFalse(safeHttpsFallback("http://example.com"))
    @Test fun rejectsCredentials() = assertFalse(safeHttpsFallback("https://user:password@example.com"))
    @Test fun rejectsMissingHost() = assertFalse(safeHttpsFallback("https:///path"))
}
```

### `app/src/test/java/com/example/privatebrowser/FilterRulesTest.kt`

<!-- file: app/src/test/java/com/example/privatebrowser/FilterRulesTest.kt -->
```kotlin
package com.example.privatebrowser

import org.junit.Assert.*
import org.junit.Test

class FilterRulesTest {
    @Test fun matchingAndExceptions() {
        val rules = FilterRules.parse(sequenceOf("||ads.example.com^", "@@||safe.ads.example.com^"))
        assertTrue(rules.blocks("https://cdn.ads.example.com/a"))
        assertFalse(rules.blocks("https://safe.ads.example.com/a"))
        assertFalse(rules.blocks("https://notads.example.com/a"))
        assertFalse(rules.blocks("https://ads.example.com.evil.test/a"))
    }
    @Test fun doesNotBroadenUnsupportedRule() {
        val rules = FilterRules.parse(sequenceOf("||example.com^\$redirect=noopjs", "||example.com^\$third-party"))
        assertFalse(rules.blocks("https://example.com/"))
        assertEquals(2, rules.skipped)
    }
    @Test fun scopedCssAndException() {
        val rules = FilterRules.parse(sequenceOf("example.com##.advert", "safe.example.com#@#.advert"))
        assertEquals(setOf(".advert"), rules.selectors("www.example.com"))
        assertTrue(rules.selectors("safe.example.com").isEmpty())
        assertTrue(rules.selectors("another.test").isEmpty())
    }
    @Test fun rejectsExecutableAndAdvancedCosmetics() {
        val rules = FilterRules.parse(sequenceOf("example.com##+js(alert)", "example.com##.a{color:red}"))
        assertTrue(rules.cosmetic.isEmpty())
        assertEquals(2, rules.skipped)
    }
    @Test fun acceptsCaseAndTrailingDot() {
        assertTrue(FilterRules.parse(sequenceOf("||ads.example.com^")).blocks("https://ADS.EXAMPLE.COM./x"))
    }
}
```

### `app/src/test/java/com/example/privatebrowser/NetworkRulesChecks.java`

<!-- file: app/src/test/java/com/example/privatebrowser/NetworkRulesChecks.java -->
```java
package com.example.privatebrowser;

public final class NetworkRulesChecks {
    private static int checks;
    private static void expect(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }
    public static void runAll() {
        checks=0;
        NetworkRules r=new NetworkRules();
        expect(r.add("||ads.example.com^"));
        expect(r.add("@@||safe.ads.example.com^"));
        expect(r.decision("https://ads.example.com/x", "site.test", "image")==1);
        expect(r.decision("https://cdn.ads.example.com/x", "site.test", "image")==1);
        expect(r.decision("https://ads.example.com.evil.test/x", "site.test", "image")==0);
        expect(r.decision("https://notads.example.com/x", "site.test", "image")==0);
        expect(r.decision("https://safe.ads.example.com/x", "site.test", "image")==-1);
        expect(r.decision("https://ADS.EXAMPLE.COM./x", "site.test", "image")==1);
        expect(!r.add("||test.example^$third-party"));
        expect(!r.add("||test.example^$redirect=noopjs"));
        expect(!r.add("||test.example^$unknown"));
        expect(!r.add("/unsafe.*regex/"));
        expect(!r.add("@@||test.example^$document"));
        NetworkRules scoped=new NetworkRules();
        expect(scoped.add("||cdn.example.com/ads/*$script,domain=site.test|~safe.site.test"));
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "www.site.test", "script")==1);
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "safe.site.test", "script")==0);
        expect(scoped.decision("https://cdn.example.com/app/a.js", "site.test", "script")==0);
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "site.test", "image")==0);
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "site.test", "")==0);
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "", "script")==0);
        NetworkRules generic=new NetworkRules();
        expect(generic.add("/advertisement/*"));
        expect(generic.decision("https://site.test/advertisement/banner", "site.test", "image")==1);
        expect(generic.decision("https://site.test/article", "site.test", "image")==0);
        expect(generic.add("@@/advertisement/allowed|"));
        expect(generic.decision("https://site.test/advertisement/allowed", "site.test", "image")==-1);
        expect(generic.decision("https://site.test/advertisement/allowedmore", "site.test", "image")==1);
        NetworkRules caseRule=new NetworkRules();
        expect(caseRule.add("|https://example.com/Ad|$match-case"));
        expect(caseRule.decision("https://example.com/Ad", "example.com", "image")==1);
        expect(caseRule.decision("https://example.com/ad", "example.com", "image")==0);
        expect(r.decision("javascript:alert(1)", "", "")==0);
        NetworkRules indexed=new NetworkRules();
        for(int i=0;i<20000;i++) expect(indexed.add("||ads"+i+".example.com^"));
        expect(indexed.decision("https://ads19999.example.com/x", "site.test", "image")==1);
        expect(indexed.decision("https://media.example.com/video", "site.test", "media")==0);
        System.out.println("NetworkRules: " + checks + " assertions passed");
    }
    public static void main(String[] args) { runAll(); }
}
```

### `app/src/test/java/com/example/privatebrowser/NetworkRulesTest.kt`

<!-- file: app/src/test/java/com/example/privatebrowser/NetworkRulesTest.kt -->
```kotlin
package com.example.privatebrowser

import org.junit.Test
class NetworkRulesTest {
    @Test fun boundaryExceptionsScopeAndIndex() = NetworkRulesChecks.runAll()
}
```

### `scripts/test-youtube.cjs`

<!-- file: scripts/test-youtube.cjs -->
```javascript
const vm = require('vm');
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const kotlin = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/example/privatebrowser/PageScripts.kt'), 'utf8');
const source = kotlin.match(/val youtube = """([\s\S]*?)"""\.trimIndent/)[1];
let cases=0;
function run({host='www.youtube.com',ad=false,duration=30,skip=false,hidden=false}={}){
 let seeks=0,clicks=0,intervals=0,clears=0,removed=0,position=0;
 const video={readyState:2,duration,currentSrc:'ad-video',get currentTime(){return position},set currentTime(v){seeks++;position=v}};
 const button=skip?{getClientRects:()=>[{}],disabled:false,click:()=>clicks++}:null;
 const player={querySelector:q=>q==='video'?video:button};
 const context={location:{hostname:host},window:{},document:{hidden,head:{appendChild(){}},createElement:()=>({remove:()=>removed++}),querySelector:()=>ad?player:null},setInterval:fn=>{intervals++;return 1},clearInterval:()=>clears++,addEventListener(){},Date,Number};
 vm.runInNewContext(source,context);
 return {seeks,clicks,intervals,context,stop(){context.window.__privateYoutubeAds.stop();assert.equal(clears,1);assert.equal(removed,1)}};
}
let r=run({ad:false});assert.equal(r.seeks,0);cases++;
r=run({ad:true});assert.equal(r.seeks,1);r.stop();cases++;
r=run({ad:true,duration:Infinity});assert.equal(r.seeks,0);cases++;
r=run({ad:true,duration:500});assert.equal(r.seeks,0);cases++;
r=run({ad:true,skip:true});assert.equal(r.clicks,1);assert.equal(r.seeks,0);cases++;
r=run({ad:true,hidden:true});assert.equal(r.seeks,0);cases++;
r=run({host:'youtube.com.evil.test',ad:true});assert.equal(r.intervals,0);cases++;
r=run({host:'www.nicovideo.jp',ad:true});assert.equal(r.intervals,0);cases++;
r=run();vm.runInNewContext(source,r.context);assert.equal(r.context.window.__privateYoutubeAds!==undefined,true);cases++;
console.log('YouTube script: '+cases+' simulated cases passed (not a live YouTube test)');
```

