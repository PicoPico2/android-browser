# Private Browser

Kotlin、Jetpack Compose、Material 3で作るAndroidブラウザの初期実装です。パッケージ名は `com.example.privatebrowser`、`minSdk 28`、`targetSdk 36` です。

## 初期版の機能

- 端末内プロフィールの作成・選択
- プロフィール単位に分離したWebViewデータ（Cookie、キャッシュ、DOM storage）
- URL／検索語を入力できるアドレスバー
- 戻る、進む、更新と、複数タブの基礎
- Android `DownloadManager` への受け渡しとダウンロード一覧画面の枠組み
- 保存パスワード機能に先立つ[セキュリティ設計](docs/SECURITY.md)

> このリポジトリは学習・プロトタイプ用です。タブとダウンロードの状態はプロセス終了後には復元されません。また、公開前にはURL遷移、証明書エラー、外部スキーム、ダウンロードをさらに堅牢化してください。

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

`main`へのpush、pull request、手動実行で `.github/workflows/android.yml` が `gradle/actions/setup-gradle@v5` を使ってGradle 8.11.1をセットアップし、単体テストとデバッグAPKビルドを行います。feature branchのpushは、pull requestと同じビルドを二重実行しません。実行ページの **Artifacts** から `private-browser-debug` を取得できます。

## プロフィール分離の仕組み

プロフィール選択UIはアプリのメインプロセス、WebViewは `:browser` プロセスで動作します。ブラウザ開始時、WebViewを一つも生成する前にプロフィールID由来の値を `WebView.setDataDirectorySuffix` へ渡します。プロフィールへ戻ると専用プロセスを終了するため、次回は別のデータディレクトリで安全に開始できます。詳細と限界は[セキュリティ設計](docs/SECURITY.md)を参照してください。
