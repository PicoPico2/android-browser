# Codex実装指示・第2段階コードパッケージ

## このファイルの使い方

このファイル1つに、仕様、適用指示、10ファイル分の完全なKotlinソースを収録しています。
ZIPを解凍する必要はありません。GitHubの現在の作業ブランチのルートへ
CODEX_PHASE2.mdとしてコミットし、そのブランチを選択した新しいCodexタスクに、
「CODEX_PHASE2.mdを読み、差分を適用しビルドしてください」と指示してください。

生成コードの基準は、ユーザーが提供したコミット
fa14d4b73e389f63eaa45d698cf8931ce03043e2と、第1段階の修正候補です。
現在のブランチに追加変更があれば内容を比較し、ユーザーの変更を保全して統合してください。
このファイル自体をAndroidソースとしてコンパイル対象に入れないでください。

**状態: 実装候補。Gradleが作成環境に存在せず、コンパイル・JUnit・Galaxy実機検証は未実施。**
差分の空白検査のみ実施。ビルド成功やサイト互換性の解決を主張しないでください。
提出された10ファイルを起点に、Codex側でビルド不備・接続漏れを修正することを依頼します。
パスワード保管庫は未実装であり、今回のコードで認証・暗号化が完成したと扱わないこと。

## Codexへの実行指示

1. 現在のブランチ、HEAD、AGENTS.md、README、CI、依存関係、既存のBrowserActivityを確認。
   mainの初期版を作業対象にしない。実機に入れた版のブランチを基準にする。
2. 本書の「合意済み仕様」を優先し、下の各ソースを対応するパスへ適用する。
   既存の独立した修正は消さない。新しい有料サービス、サーバー、閲覧情報送信は追加しない。
3. BrowserPlatform.ktは第1段階の全画面、ファイル選択、intent処理も含む完全版。
   BrowserActivity.ktだけを取り出さない。AppTheme.kt、プロフィール分離は既存実装を保持。
4. gradle testDebugUnitTest assembleDebug を実行。
   このプロジェクトにはGradle Wrapperがないため ./gradlew 前提にしない。
   JDK17・Gradle8.11.1・SDK36は既存CIに合わせる。
5. ビルドが通らなければ原因を修正。コマンド実行不能の場合は検証不能として報告し、
   実行できたチェックだけを列挙する。テスト追加をテスト成功と扱わない。
6. 初回PRではUIとWebView接続、フィルター基盤の変更を分けて説明。
   未実装・実機未確認の項目を明示し、対象コミット付きでAPKを提示。
7. スクショ確認なしにニコ動、Fantia、X、Google画像検索を解決済みとしない。
   原因を調べずにUser-Agent、Cookie、広範囲JS注入を一律変更しない。
8. 次項の広告ブロック完成条件を別の作業単位として仕上げる。
   今回の限定パーサーをEasyList完全対応、YouTube広告除去済みと報告しない。
9. パスワード保管庫は後述の認証仕様を設計・テストの基準にする。
   VaultSessionPolicyだけを認証済みの証拠として実データの復号へ使わない。
10. 低優先度のタブ自動整理要件をバックログに残す。コード中の候補選定は未接続。
    自動削除を初期状態で勝手に有効化しない。

## 合意済み仕様（第1段階の仕様より優先）

| 領域 | 通常 | 操作時 |
|---|---|---|
| 左バー | 幅56dp、全機能を縦に配置 | 戻る、進む、更新/停止、ホーム、新規ホームタブ、URL編集、ブックマーク、ダウンロード、広告ブロック、パスワード、設定 |
| 右バー | 幅56dp、タブを縦一覧で常時表示 | タップで左へ幅224dpに展開。サムネイル・タイトル・閉じる。選択後に56dpへ戻す |
| URL | 最上部中央、幅約50%、文字11sp、高さはステータスバー程度 | 64dp高の行を安全領域まで降ろす。左半分に入力＋コピー、右半分にブックマークバー |
| 下端 | アプリ余白は118dpから40dpへ縮小 | システムが必要な下部Insetsより小さくはしない |
| ページ | 選択中のWebViewを表示 | 右一覧展開時にWebViewの幅を変えず上に重ねる |

右側に広告ブロックや設定、新規タブ作成ボタンを置かない。
右バーは格納用ボタンではなく、各タブのアイコン（未取得時は短い文字）そのものを並べる。
最初のタップは一覧展開、次のタップで選択。外側タップはキャンセル。
以前の「グループ列」の解釈より今回の常時タブ一覧を優先。グループ自体は未実装。

最上部の同じ帯へのURL配置は初期状態で有効。
Galaxyがタップを受け取ってしまう場合の逃げ道として、左に「URL」ボタンを残す。
設定で実験的配置をオフにするとステータスバー直下に薄いURL行を置く。
同じ帯で確実に操作可能かは実機確認が必要。OSの通知領域を乗っ取る権限は使わない。

編集中のEnter/移動で遷移して縮小、戻る/Esc/ページ外側タップでキャンセルして縮小。
コピーは編集中の未確定文字列ではなく、現在ページの完全なURL。
ブックマークはプロフィールごとの端末内保存。追加、選択、削除が可能。
ブックマークバーだけは合意通りURL編集中の右半分に置く。

下部余白はmax(40dp, OS下部Insets)。Galaxyタスクバーの高さが40dpを超えると、
見た目が厳密に1/3にはならないが、システムのボタンとの重なりは避ける。

## リンクとキー

| 操作 | 動作 |
|---|---|
| Alt＋リンクのタップ/左クリック | 背景タブでリンク先を開く |
| Alt＋Shift＋リンクのタップ/左クリック | 新規タブで開いて移動 |
| 長押し | 新規/背景タブ、コピー、共有 |
| Alt＋左右 | 現在タブの戻る/進む |
| Ctrl＋L | URL編集を開く |
| Ctrl＋Tab / Ctrl＋Shift＋Tab | 次/前のタブ |
| Ctrl＋W | 現在タブを閉じる |
| Ctrl＋T | 補助操作としてホームを新規に開く。これをリンク操作の代わりと説明しない |
| Ctrl＋R / F5 | 更新 |
| Esc / 戻る | 全画面終了、編集/展開取消をページ履歴より優先 |

Alt+タップはActivityのキー状態とMotionEventの修飾キーを使用し、
リンク上の指離し時に通常クリックをキャンセルしてリンクを解決する。
ドラッグは対象外。リンク付き画像はrequestFocusNodeHrefでリンク先を取得。
端末・WebView実装によってイベント順序が違うため、二重遷移・取り違えを実機検証する。
URLのないJSボタンやフォームPOSTは一般リンクとして扱わない。
ページ全面に常時ジェスチャー層を載せない。

## タブ保持（自動整理の優先度は低い）

今回: タブ切替でWebViewを作り直さず保持。FrameLayoutの子を入れ替える。
最後のタブを閉じても新規タブが残る。サムネイルは224x126の静止画で端末メモリのみ。
タブを離れる時と展開時に取得し、定期的な連続撮影はしない。
サムネイルは概ね24件まで保持。未表示タブ/動画のGPU面ではプレビューが得られないことがある。

後続:
- LRU（最後に使った時刻）順で休止。作成順ではない。
- 選択中、固定、再生中、未送信フォームのあるタブを保護。
- 休止では一覧、URL、復元可能な履歴/位置を残す。復帰は再読み込みになる場合を明示。
- 総タブ数上限による自動削除はユーザー設定で有効化。復元用の閉じたタブ履歴も設計。
- RAMとディスクキャッシュを混同しない。キャッシュ量だけでWebViewメモリの使用量を判断しない。
- プロセス終了/回転後のセッション復元は現時点で未実装。通常切替の保持とは別。
- TabRetentionPolicy.ktは保護対象と順序の単体テスト可能な候補選定のみで、UIには未接続。
- dirtyForm/playingの検出を実装するまで、検出不能を「保護不要」とみなして削除しない。

## 広告ブロック：今回の実装と完成条件

### 今回の実装

- 基本ホストリストを拡充し、ホスト境界を見て判定。文字列containsでは判定しない。
- 標準: 基本ホストのリソース・主文書遷移を遮断。
- 強力: EasyListから取り込んだ対応ルール、単純なCSS広告枠非表示を追加。
- EasyList更新ボタン。HTTPSの固定公式URLから取得し、上限12MiB、
  タイムアウト・形式検査・AtomicFile書込。失敗時は旧リストを維持。
- リスト取得処理とパースはバックグラウンド。ページ通信ごとにファイルを読まない。
- 対応構文: ||host^、@@||host^、domain##.class / domain###id、その#@#例外。
  オプション、パスルール、正規表現、複雑なCSS、scriptletなどは未対応件数として集計。
- リソース遮断件数をタブ単位に集計。
- 主文書が既知広告URLへ移動する時、元ページに残り「今回開く」で解除可能。
- 操作なしの新規ウィンドウを拒否。通常のtarget=_blankは一時WebViewでURLを解決してタブへ渡す。
- 一時ウィンドウのJavaScriptは無効。window.openerを使うOAuth等とは互換性がない場合がある。
- 外部アプリ用Intentは自動起動せず、第1段階のWebフォールバックを維持。
- サイト別OFF。Google等の正規ドメイン全体や全第三者通信の一律遮断はしない。

限定構文のため未対応の例外もあり、サイト互換性には限界がある。
リスト更新は手動のみ。まだ主要ブロッカー相当の「本格強化完了」ではない。
CSSはトップ文書へ読み込み後に注入。iframe・Shadow DOM・高度なサイト別対策は未対応。
Service Worker、WebSocket、blob、リダイレクトのすべてを覆えるとは限らない。
YouTube等の動画広告は除去保証なし。

### Codexに引き継ぐ完成条件

- 既存の保守されている無料フィルターエンジンを比較し、Android/Kotlinとの接続性、
  対応構文、ライセンス・配布条件、実測の処理時間/メモリを確認して選ぶ。
  汎用構文の巨大な独自パーサーをここから増築しない。
- EasyListに加え日本向けリストを候補にする。URL・ライセンスを公式元から確認し、
  本文やJSを無検証で実行しない。
- 条件付き例外、リソース種別、ドメイン指定を正しく適用。
- Service Worker経路を評価。WebViewごとの設定と結びつけられないリクエストは
  推測で他タブの強設定を流用しない。
- 同一タブの広告転送とポップアップを別々に検証。hasGesture=falseだけで全転送を止めない。
- オフ/標準/強の差、ブロック理由、リスト版・更新日時を左の盾から確認可能にする。
- 自動更新は頻度を制限し、失敗時に前版へ戻せるようにする。
- 認証・決済・動画の正常動作と広告遮断を同時に検証。失敗サイトは例外規則を狭くする。
- サイト固有の修正前にログで原因を確認。Cookie/Authorization/URLクエリ/
  署名付きメディアURLを診断ログへ出さない。
- 外部フィルターの配布にはライセンス表記を保持する。今回の納品物にEasyList本文は同梱していない。

公式出典:
https://easylist.to/
https://developer.android.com/reference/android/webkit/WebViewClient

## パスワードマネージャーの合意済み認証仕様（後続実装）

WebサイトのログインCookieと保管庫の解除状態は独立させる。
「サインインしたことがある」だけで保管庫を永久に解除しない。

| イベント/操作 | 条件 |
|---|---|
| 初回解除 | 端末の指紋認証。認証キャンセル/失敗は解除しない |
| 自動入力 | 最終の成功した認証から5分以内。入力先HTTPS originと保存先を照合しユーザーが選んで入力 |
| 表示/コピー | 初期設定は毎回新しい認証。後から追加する任意の猶予は最大1分等を明示 |
| 画面ロック/プロフィール切替 | 即時ロック |
| バックグラウンド移行 | バックグラウンド中は利用禁止、30秒以上なら解除破棄 |
| 有効期限 | 操作のたびに無期限延長しない |
| コピー | 機密フラグを付けてプレビュー抑制。30秒後に自分のコピーが残っている場合だけ消す |

VaultSessionPolicy.ktは期限判定の参考実装のみ。
時計にはSystemClock.elapsedRealtimeを使い、壁時計変更で延長させない。
実際の秘密の保護はAndroid Keystoreの認証条件と暗号化に結び付ける。
保存パスワードを平文SharedPreferences/ログ/URL/クリップボード履歴へ保存しない。
認証成功時に全件を平文化して5分置く設計は避け、必要な資格情報だけを必要な間だけ復号する。
表示/コピーの新しい認証はKeystoreの鍵使用にも結び付ける設計を検証する。
CryptoObjectと有効期間型の鍵の制約を整理し、単なるbooleanチェックで代用しない。

端末ロック時には期限型の鍵が一時的に有効でも、アプリ側のロック判定で必ず拒否する。
画面ロック/プロフィール切替/バックグラウンド復帰時の接続とプロセス再起動をテストする。
ポリシークラスのonAuthenticatedを、アプリ起動やサイトログイン成功から直接呼ばない。
コピー済みの内容を他者が既に取得した場合は消去しても回収できない。
入力先の同一origin確認、ページの入れ替わり、iframe、フィッシング、
TLSエラー、フォーム送信先の扱いを設計してから自動入力を有効化する。
保管庫画面のサムネイル・画面キャプチャ対策と、秘密を含むページのプレビュー抑制も追加する。

今回の鍵ボタンは利用不可の説明のみ。実パスワードを投入して試さない。
この段階ではパスワード保存・暗号化・指紋UIは生成していない。

公式出典:
https://developer.android.com/identity/sign-in/biometric-auth
https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling

## 合格条件と未解決項目

- 横画面で左に全操作、右にタブのみ。タブが多くてもスクロールできる。
- 右56→224dp、選択して戻る。WebViewの幅が展開で変わらない。
- URL中央縮小、編集時に左50%・ブックマーク右50%。URLコピーが完全な文字列。
- ステータスバー同帯でタップできなければ左URLボタンと設定OFFで確実に復旧。
- 下部は40dp相当。ただしOSが必要な高さを優先。キーボードで入力欄が消えない。
- 普通のリンク、画像リンク、Alt+タップ、Alt+クリック、Alt+Shift、
  ドラッグ後の指離し、通常クリックで元ページの誤遷移・二重タブがない。
- 全画面→戻る→再度全画面、ファイル選択→キャンセル→再選択が動く。
- 広告リソース遮断、CSS非表示、主文書移動の停止、今回だけ許可を個別検証。
- target=_blank、OAuthポップアップの互換性を別々に評価。後者は現実装で未保証。
- 更新失敗で前リスト保持、UIが固まらない。設定OFFで壊れたサイトを復帰可能。
- フィルタールールの未対応件数を隠さない。
- ニコ動の黒画面、Fantia購入履歴の音声のみ、Xログイン、Google Lensは未解決扱い。
- ユーザーが使う実URLでの検証なしに広告完全除去・サイト修正を宣言しない。

以下は各ファイルの完全な内容です。コードブロックの見出しにある相対パスへ配置してください。


## app/src/main/java/com/example/privatebrowser/BrowserActivity.kt

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

private const val HOME_URL = "https://www.google.com"

private class BrowserTab(val id: Long, val webView: WebView) {
    var icon by mutableStateOf<android.graphics.Bitmap?>(null)
    var thumbnail by mutableStateOf<android.graphics.Bitmap?>(null)
    var lastUsed by mutableLongStateOf(android.os.SystemClock.elapsedRealtime())
    val blockedCount = java.util.concurrent.atomic.AtomicInteger(0)
    var blockedNavigation by mutableStateOf<String?>(null)
    var title by mutableStateOf("新しいタブ")
    var url by mutableStateOf(HOME_URL)
    var progress by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var siteHost by mutableStateOf("")
    var blockingLevel by mutableStateOf(BlockingLevel.STANDARD)
    @Volatile var requestBlockingLevel: BlockingLevel = BlockingLevel.STANDARD

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

    fun destroy() {
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
    val tabs = remember { mutableStateListOf(createWebView(context, 1L, blockingPreferences, platform, filters)) }
    var selectedId by remember { mutableStateOf(1L) }
    var nextId by remember { mutableStateOf(2L) }
    var addressInput by remember { mutableStateOf(HOME_URL) }
    var editing by remember { mutableStateOf(false) }
    var expandedTabs by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf("") }
    var overlayAddress by remember { mutableStateOf(preferences.getBoolean("overlay_address", true)) }
    var filterStatus by remember { mutableStateOf(filters.status) }
    var updating by remember { mutableStateOf(false) }
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
    fun saveBookmarks() {
        val array = org.json.JSONArray()
        bookmarks.forEach { (title, url) -> array.put(org.json.JSONObject().put("title", title).put("url", url)) }
        preferences.edit().putString("bookmarks", array.toString()).apply()
    }
    fun leaveCurrent() {
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
        tabs.remove(tab)
        tab.destroy()
    }
    fun finishEditing() { editing = false; focus.clearFocus() }
    fun navigate(url: String) { current.webView.loadUrl(normalizeUrl(url)); finishEditing() }
    fun copyUrl() {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("URL", current.url))
    }
    fun editAddress() { expandedTabs = false; addressInput = current.url; editing = true }
    BackHandler {
        when {
            platform.hideFullscreen() -> Unit
            editing -> finishEditing()
            expandedTabs -> expandedTabs = false
            current.webView.canGoBack() -> current.webView.goBack()
            else -> closeProfile()
        }
    }
    LaunchedEffect(editing) { if (editing) addressFocus.requestFocus() }
    LaunchedEffect(current.id, current.url) { if (!editing) addressInput = current.url }
    androidx.compose.runtime.SideEffect {
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
        title = { Text(when (panel) { "ads" -> "広告ブロック"; "bookmarks" -> "ブックマーク"; "vault" -> "パスワード管理"; else -> "設定" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (panel) {
                    "ads" -> {
                        Text(current.siteHost)
                        Text("このページの遮断件数: " + current.blockedCount.get())
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
                        Text("標準: 既知広告の通信・移動を遮断\n強力: 更新リスト＋広告枠非表示を追加")
                        Text(filterStatus)
                        TextButton(enabled = !updating, onClick = {
                            updating = true; filterStatus = "更新中"
                            filters.update { filterStatus = it; updating = false }
                        }) { Text("EasyListを更新") }
                        Text("限定構文に対応。更新後はページを再読み込みしてください。")
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
                    "vault" -> Text("まだ利用できません。保存・自動入力は有効になっていません。")
                    else -> {
                        Text("URLをステータスバーと同じ帯に表示（実験的）")
                        Switch(overlayAddress, onCheckedChange = { overlayAddress = it; preferences.edit().putBoolean("overlay_address", it).apply() })
                        Text("タップできない場合はオフに戻すか左のURLボタンを使ってください。")
                        Text("タブは保持します。古いタブの自動休止・削除は後続実装です。")
                        TextButton(onClick = closeProfile) { Text("プロフィールへ戻る") }
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
                        TextButton(onClick = ::copyUrl) { Text("コピー") }
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
                    ToolbarKey("＋", "新しいホームタブ", { addTab() })
                    ToolbarKey("URL", "URLを編集", { editAddress() })
                    ToolbarKey("★", "ブックマーク", { panel = "bookmarks" })
                    ToolbarKey("↓", "ダウンロード", { runCatching { context.startActivity(android.content.Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } })
                    ToolbarKey("盾", "広告ブロック", { filterStatus = filters.status; panel = "ads" })
                    ToolbarKey("鍵", "パスワード管理", { panel = "vault" })
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
                        current.error?.let { Text(it, Modifier.background(MaterialTheme.colorScheme.errorContainer).padding(8.dp)) }
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
                LazyColumn(Modifier.width(56.dp).fillMaxHeight()) {
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
            }
            Spacer(Modifier.fillMaxWidth().height(maxOf(40.dp, systemPadding.calculateBottomPadding())))
        }
        if (expandedTabs) Surface(
            modifier = Modifier.align(Alignment.CenterEnd).width(224.dp)
                .padding(top = systemPadding.calculateTopPadding() + topHeight, bottom = maxOf(40.dp, systemPadding.calculateBottomPadding())).fillMaxHeight(),
            shadowElevation = 8.dp,
        ) {
            LazyColumn {
                items(tabs, key = { it.id }) { tab ->
                    Column(Modifier.fillMaxWidth().background(
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
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity.shortcutHandler = null
            platform.openLink = null
            filters.dispose()
            tabs.toList().forEach { it.destroy() }
        }
    }
}
@Composable
private fun ToolbarKey(label: String, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(2.dp),
        modifier = Modifier.size(56.dp, 48.dp).semantics { contentDescription = description },
    ) { Text(label, fontSize = if (label.length > 1) 12.sp else 24.sp) }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    id: Long,
    blockingPreferences: SiteBlockingPreferences,
    platform: BrowserPlatform,
    filters: FilterStore,
    initialUrl: String = HOME_URL,
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
    tab = BrowserTab(id, webView)
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.isForMainFrame && blocks(request.url.toString())) {
                tab.blockedCount.incrementAndGet()
                tab.blockedNavigation = request.url.toString()
                return true
            }
            return platform.handleNavigation(view, request)
        }

        private fun blocks(url: String): Boolean =
            tab.requestBlockingLevel != BlockingLevel.OFF && (
                LocalRequestBlocker.shouldBlock(url, tab.requestBlockingLevel) ||
                    (tab.requestBlockingLevel == BlockingLevel.STRICT && filters.rules.blocks(url)))

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (url != null) tab.url = url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            tab.url = url
            tab.siteHost = LocalRequestBlocker.host(url)
            tab.blockingLevel = blockingPreferences.level(tab.siteHost)
            tab.requestBlockingLevel = tab.blockingLevel
            tab.error = null
            tab.blockedCount.set(0)
            tab.blockedNavigation = null
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.url = url
            tab.title = view.title?.takeIf(String::isNotBlank) ?: url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
            if (tab.requestBlockingLevel == BlockingLevel.STRICT) {
                val selectors = filters.rules.selectors(FilterRules.hostOf(url)) + setOf(".adsbygoogle")
                val css = selectors.take(3000).joinToString(",") + "{display:none!important}"
                val script = "(function(){if(!document.getElementById('private-browser-ad-style')){var s=document.createElement('style');s.id='private-browser-ad-style';s.textContent=" +
                    org.json.JSONObject.quote(css) + ";(document.head||document.documentElement).appendChild(s);}})()"
                view.evaluateJavascript(script, null)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) tab.error = "ページを表示できません（${error.errorCode}）"
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!request.isForMainFrame && blocks(request.url.toString())) {
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
                        val blocked = tab.requestBlockingLevel != BlockingLevel.OFF && (
                            LocalRequestBlocker.shouldBlock(url, tab.requestBlockingLevel) ||
                                (tab.requestBlockingLevel == BlockingLevel.STRICT && filters.rules.blocks(url)))
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
    webView.loadUrl(initialUrl)
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
```

## app/src/main/java/com/example/privatebrowser/BrowserPlatform.kt

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
```

## app/src/main/java/com/example/privatebrowser/AdBlocker.kt

```kotlin
package com.example.privatebrowser

import android.content.Context
import java.net.URI

enum class BlockingLevel(val label: String) {
    OFF("オフ"),
    STANDARD("標準"),
    STRICT("強力"),
}

class SiteBlockingPreferences(context: Context, private val profileId: String) {
    private val preferences = context.applicationContext
        .getSharedPreferences("site_blocking", Context.MODE_PRIVATE)

    fun level(host: String): BlockingLevel = runCatching {
        BlockingLevel.valueOf(preferences.getString(key(host), null) ?: BlockingLevel.STANDARD.name)
    }.getOrDefault(BlockingLevel.STANDARD)

    fun setLevel(host: String, level: BlockingLevel) {
        preferences.edit().putString(key(host), level.name).apply()
    }

    private fun key(host: String) = "$profileId:${host.lowercase()}"
}

internal object LocalRequestBlocker {
    private val standardDomains = setOf(
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com",
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "ads.yahoo.com",
        "ads-twitter.com",
    )
    private val strictDomains = standardDomains + setOf(
        "adnxs.com",
        "casalemedia.com",
        "openx.net",
        "pubmatic.com",
        "rubiconproject.com",
    )
    private val essentialDomains = setOf(
        "google.com",
        "google.co.jp",
        "gstatic.com",
        "googleapis.com",
        "recaptcha.net",
        "hcaptcha.com",
        "stripe.com",
        "paypal.com",
    )

    fun shouldBlock(url: String, level: BlockingLevel): Boolean {
        if (level == BlockingLevel.OFF) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        val path = uri.path.orEmpty().lowercase()
        val domains = if (level == BlockingLevel.STRICT) strictDomains else standardDomains
        if (domains.any { domain -> host.isDomainOrSubdomain(domain) }) return true
        if (essentialDomains.any { domain -> host.isDomainOrSubdomain(domain) }) return false
        if (listOf("captcha", "login", "signin", "oauth", "checkout", "payment").any(path::contains)) return false
        return false
    }

    fun host(url: String): String = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")

    private fun String.isDomainOrSubdomain(domain: String): Boolean = this == domain || endsWith(".$domain")
}
```

## app/src/main/java/com/example/privatebrowser/FilterRules.kt

```kotlin
package com.example.privatebrowser

import java.net.URI

/** Deliberately limited grammar. Unsupported syntax is counted, never broadened. */
internal data class FilterRules(
    val blockedHosts: Set<String> = emptySet(),
    val allowedHosts: Set<String> = emptySet(),
    val cosmetic: Map<String, Set<String>> = emptyMap(),
    val cosmeticExceptions: Map<String, Set<String>> = emptyMap(),
    val skipped: Int = 0,
) {
    fun blocks(url: String): Boolean {
        val host = hostOf(url)
        if (host.isEmpty()) return false
        return !matches(host, allowedHosts) && matches(host, blockedHosts)
    }

    fun selectors(host: String): Set<String> {
        val hidden = cosmetic.filterKeys { it.isEmpty() || domainMatches(host, it) }.values.flatten().toSet()
        val exceptions = cosmeticExceptions.filterKeys { it.isEmpty() || domainMatches(host, it) }.values.flatten().toSet()
        return hidden - exceptions
    }

    companion object {
        private val domain = Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,}")
        private val selector = Regex("[.#][A-Za-z_][A-Za-z0-9_-]*")

        fun parse(lines: Sequence<String>): FilterRules {
            val deny = mutableSetOf<String>()
            val allow = mutableSetOf<String>()
            val hide = mutableMapOf<String, MutableSet<String>>()
            val show = mutableMapOf<String, MutableSet<String>>()
            var skipped = 0
            lines.forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() || line.startsWith("!") || line.startsWith("[") -> Unit
                    line.contains("#@#") || line.contains("##") -> {
                        val exception = line.contains("#@#")
                        val pieces = line.split(if (exception) "#@#" else "##", limit = 2)
                        val domains = pieces[0].lowercase().split(',')
                        if (!selector.matches(pieces[1]) || domains.any { it.isNotEmpty() && !domain.matches(it) }) skipped++
                        else domains.forEach { host ->
                            (if (exception) show else hide).getOrPut(host) { mutableSetOf() }.add(pieces[1])
                        }
                    }
                    else -> {
                        val exception = line.startsWith("@@")
                        val rule = line.removePrefix("@@")
                        val host = rule.removePrefix("||").removeSuffix("^").lowercase()
                        if (rule.startsWith("||") && rule.endsWith("^") && domain.matches(host)) {
                            (if (exception) allow else deny).add(host)
                        } else skipped++
                    }
                }
            }
            return FilterRules(deny.toSet(), allow.toSet(), hide.mapValues { it.value.toSet() }, show.mapValues { it.value.toSet() }, skipped)
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

## app/src/main/java/com/example/privatebrowser/FilterStore.kt

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

/** No browsing URLs or cookies are sent to the filter provider. */
internal class FilterStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "easylist-subset.txt"))
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var rules = FilterRules()
        private set
    @Volatile var status = "未更新（同梱の基本ルールのみ）"
        private set

    init {
        worker.execute {
            runCatching {
                if (file.baseFile.exists()) file.openRead().bufferedReader().use { reader ->
                    rules = FilterRules.parse(reader.lineSequence())
                    status = summary()
                }
            }
        }
    }

    fun summary() = "ホスト ${rules.blockedHosts.size} / 非対応ルール ${rules.skipped}（限定構文）"

    fun update(done: (String) -> Unit) {
        worker.execute {
            val result = runCatching {
                val connection = URL("https://easylist.to/easylist/easylist.txt").openConnection() as HttpsURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false
                val bytes = try {
                    check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                    connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 12 * 1024 * 1024) { "リストが大きすぎます" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                } finally { connection.disconnect() }
                val content = bytes.toString(Charsets.UTF_8)
                check(content.trimStart().startsWith("[Adblock")) { "フィルター形式ではありません" }
                val parsed = FilterRules.parse(content.lineSequence())
                check(parsed.blockedHosts.isNotEmpty()) { "対応するルールがありません" }
                val output = file.startWrite()
                try { output.write(bytes); file.finishWrite(output) }
                catch (e: Exception) { file.failWrite(output); throw e }
                rules = parsed
                status = summary()
                status
            }.getOrElse { "更新失敗。前のルールを維持します" }
            main.post { done(result) }
        }
    }

    fun dispose() { worker.shutdownNow() }
}
```

## app/src/main/java/com/example/privatebrowser/VaultSessionPolicy.kt

```kotlin
package com.example.privatebrowser

/** Policy only, not authentication. No credentials are stored or unlocked by this class. */
internal class VaultSessionPolicy(private val now: () -> Long) {
    private var authenticatedAt: Long? = null
    private var backgroundAt: Long? = null

    fun onAuthenticated() { authenticatedAt = now(); backgroundAt = null }
    fun lock() { authenticatedAt = null; backgroundAt = null }
    fun onBackground() { backgroundAt = now() }
    fun onForeground() {
        val background = backgroundAt
        if (background != null && now() - background >= 30_000L) lock()
        backgroundAt = null
    }
    fun canAutofill(): Boolean {
        val at = authenticatedAt ?: return false
        return now() - at in 0 until 300_000L && backgroundAt == null
    }
    // Display and copying always require a fresh auth operation in the default policy.
    fun requiresFreshAuthenticationForExport() = true
}
```

## app/src/main/java/com/example/privatebrowser/TabRetentionPolicy.kt

```kotlin
package com.example.privatebrowser

/** Candidate selection only. Not wired to destructive tab closure. */
internal data class RetainedTab(
    val id: Long,
    val lastUsed: Long,
    val active: Boolean = false,
    val pinned: Boolean = false,
    val playing: Boolean = false,
    val dirtyForm: Boolean = false,
)

internal fun tabsToRetire(tabs: List<RetainedTab>, maximum: Int): List<Long> = tabs
    .filterNot { it.active || it.pinned || it.playing || it.dirtyForm }
    .sortedBy { it.lastUsed }
    .take((tabs.size - maximum.coerceAtLeast(1)).coerceAtLeast(0))
    .map { it.id }
```

## app/src/test/java/com/example/privatebrowser/BrowserPlatformTest.kt

```kotlin
package com.example.privatebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPlatformTest {
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

## app/src/test/java/com/example/privatebrowser/FilterRulesTest.kt

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
        val rules = FilterRules.parse(sequenceOf("||example.com/ad*", "||example.com^\$script"))
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

## app/src/test/java/com/example/privatebrowser/BrowserPoliciesTest.kt

```kotlin
package com.example.privatebrowser

import org.junit.Assert.*
import org.junit.Test

class BrowserPoliciesTest {
    @Test fun vaultExpiryAndBackground() {
        var now = 0L
        val policy = VaultSessionPolicy { now }
        assertFalse(policy.canAutofill())
        policy.onAuthenticated()
        assertTrue(policy.canAutofill())
        assertTrue(policy.requiresFreshAuthenticationForExport())
        now = 300_000L
        assertFalse(policy.canAutofill())
        policy.onAuthenticated()
        policy.onBackground()
        assertFalse(policy.canAutofill())
        now += 30_000L
        policy.onForeground()
        assertFalse(policy.canAutofill())
    }
    @Test fun lockImmediatelyRevokesSession() {
        val policy = VaultSessionPolicy { 0L }
        policy.onAuthenticated(); policy.lock()
        assertFalse(policy.canAutofill())
    }
    @Test fun shortBackgroundPreservesRemainingSession() {
        var now = 0L
        val policy = VaultSessionPolicy { now }
        policy.onAuthenticated(); policy.onBackground()
        now = 1000L; policy.onForeground()
        assertTrue(policy.canAutofill())
    }
    @Test fun retireByLastUseAndProtectWork() {
        val tabs = listOf(RetainedTab(1, 10), RetainedTab(2, 5, pinned = true), RetainedTab(3, 1), RetainedTab(4, 0, active = true))
        assertEquals(listOf(3L, 1L), tabsToRetire(tabs, 2))
    }
    @Test fun neverRetirePlayingOrDirtyTabs() {
        assertTrue(tabsToRetire(listOf(RetainedTab(1, 0, playing = true), RetainedTab(2, 0, dirtyForm = true)), 1).isEmpty())
    }
}
```

