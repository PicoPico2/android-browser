# Private Browser

Kotlin、Jetpack Compose、Material 3で作るAndroidブラウザの初期実装です。パッケージ名は `com.example.privatebrowser`、`minSdk 28`、`targetSdk 36` です。

## 現在実装済みの機能

- 端末内プロフィールの作成・選択、前回使用したプロフィールの表示
- プロフィール単位のWebViewデータディレクトリ（Cookie、キャッシュ、DOM storage）
- URL／検索語を入力できるアドレスバー
- 戻る、進む、再読み込み／停止、ホーム
- 軽量なタブの作成、切り替え、個別終了と、選択中タブの明示
- ページ読み込み進捗、メインフレームの読み込みエラー、非HTTPS表示の警告
- タブレットの縦横画面とシステムのダークモードに追従するCompose UI
- JavaScript、DOM Storage、Cookie（サードパーティCookieを含む）を有効にした基本Web表示
- 通常ファイルをAndroid `DownloadManager`へ渡す既存の直接ダウンロード
- 保存パスワード機能に先立つ[セキュリティ設計](docs/SECURITY.md)

## 性能とライフサイクルの改善

初期版ではページ完了コールバックの状態が画面全体に集約され、タブの選択状態とWebViewの状態が密結合していました。また、タブを閉じるUIがなく、生成したWebViewを画面終了まで解放できませんでした。

現在はWebViewをタブ生成時に一度だけ作成し、Composeの再コンポーズでは再生成しません。URL・タイトル・履歴状態はタブ単位で保持し、進捗は5%刻みに丸めて不要な再コンポーズを抑えています。タブ終了時は読み込み停止、親Viewからの切り離し、クライアントと子Viewの解放、`destroy()`を行います。ActivityのComposeツリー破棄時にも残る全タブを同様に破棄します。

> タブを切り替えても、開いているタブのWebViewはページ状態を保つためメモリ内に残ります。多数タブの休止・破棄と復元は今後の課題です。タブ状態はプロセス終了後に復元されません。

## 未実装・既知の制限

- プロフィール名変更、削除、起動時に毎回選択する設定
- タブ一覧のサムネイル、タブ一括終了、タブグループ、セッション復元
- サイト別Cookie／サードパーティCookie設定、User-Agent切り替え、デスクトップ表示
- ファイルアップロード、Web権限確認、ポップアップ／新規ウィンドウ、全画面動画、外部アプリ連携
- アドブロック（標準／強力を含む）とYouTube互換性設定
- パスワード保存・自動入力（安全な設計とテストが完了するまで未実装）
- HLS／ライブ／動画ダウンロード、DRM処理、MKVからMP4への変換、専用動画プレイヤー
- ダウンロードの中断・再開やアプリ内管理画面、Storage Access Frameworkによる保存先選択

証明書エラーの回避やDRM・アクセス制限の迂回は行いません。通常ダウンロード以外の動画保存機能、YouTubeダウンロード機能もありません。ニコニコ生放送は通常のWebView表示だけが対象であり、サイト側の仕様変更によって動作しなくなる可能性があります。

## 必要環境

- Android Studio（JDK 17を同梱する安定版を推奨）またはJDK 17
- Android SDK Platform 36 / Build Tools 36.0.0
- Gradle 8.11.1（コマンドラインでビルドする場合）

このリポジトリにはGradle Wrapper JARを含めていないため、ローカルの `gradle` コマンドまたはAndroid Studioに組み込まれたGradleを使用します。

## ビルド

```bash
gradle assembleDebug
```

生成物は `app/build/outputs/apk/debug/app-debug.apk` です。Android Studioではリポジトリ直下を開いてGradle Sync後、`app` 構成を実行します。

## テスト

```bash
# JVM単体テスト
gradle testDebugUnitTest

# 接続済みエミュレータ／端末で計装テスト
gradle connectedDebugAndroidTest

# ビルドを含む基本チェック
gradle testDebugUnitTest assembleDebug
```

## GitHub Actions

`main`へのpush、pull request、手動実行で `.github/workflows/android.yml` がNode.js 24対応の `gradle/actions/setup-gradle@v6` を使ってGradle 8.11.1をセットアップし、単体テストとデバッグAPKビルドを行います。GitHubランナーにプリインストールされた `sdkmanager` を利用するため、廃止されたAndroid SDKの `tools` パッケージは要求しません。feature branchのpushは、pull requestと同じビルドを二重実行しません。実行ページの **Artifacts** から `private-browser-debug` を取得できます。

## プロフィール分離の仕組み

プロフィール選択UIはアプリのメインプロセス、WebViewは `:browser` プロセスで動作します。ブラウザ開始時、WebViewを一つも生成する前にプロフィールID由来の値を `WebView.setDataDirectorySuffix` へ渡します。プロフィールへ戻ると専用プロセスを終了するため、次回は別のデータディレクトリで安全に開始できます。詳細と限界は[セキュリティ設計](docs/SECURITY.md)を参照してください。

Android WebViewには、既にWebViewを初期化した同一プロセス内でデータディレクトリを切り替えるAPIがありません。このため専用プロセスとデータディレクトリサフィックスを使っていますが、これは別Androidユーザー／別アプリのような完全なセキュリティ境界ではありません。同じアプリUIDを共有し、OSやWebViewの脆弱性、root化端末に対する完全分離は保証できません。現在プロフィール削除自体が未実装なので、削除に伴うWebViewデータ消去も未実装です。
