package com.example.privatebrowser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

@UnstableApi
class HlsExportService : Service() {
    private var transformer: Transformer? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var output: File? = null
    private var displayName = "video.mp4"
    private var jobs: HlsDownloadRepository? = null
    private var jobId = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, "動画ダウンロード", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (transformer != null) {
            markIntentFailed(intent, "別のm3u8を保存中です。完了後に再試行してください")
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        jobId = intent.getStringExtra(EXTRA_JOB_ID).orEmpty()
        jobs = HlsDownloadRepository(applicationContext, profileId)
        jobs?.update(jobId, HlsDownloadStatus.RUNNING)
        displayName = safeName(intent.getStringExtra(EXTRA_TITLE).orEmpty()) + ".mp4"
        startForeground(NOTIFICATION_ID, notification("準備中", 0))
        val headers = buildMap {
            intent.getStringExtra(EXTRA_USER_AGENT)?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
            intent.getStringExtra(EXTRA_REFERER)?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
            android.webkit.CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        }
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        val mediaSource = DefaultMediaSourceFactory(this).setDataSourceFactory(http)
        val assetLoader = ExoPlayerAssetLoader.Factory(this, DefaultDecoderFactory(this), Clock.DEFAULT, mediaSource)
        output = File.createTempFile("hls_", ".mp4", externalCacheDir ?: cacheDir)
        transformer = Transformer.Builder(this).setAssetLoaderFactory(assetLoader).setUsePlatformDiagnostics(false)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val saved = output?.let(::publish) == true
                    if (saved) jobs?.update(jobId, HlsDownloadStatus.COMPLETE)
                    else jobs?.update(jobId, HlsDownloadStatus.FAILED, "保存先への書き込みに失敗しました")
                    finish(if (saved) "保存完了: $displayName" else "保存先への書き込みに失敗しました")
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    jobs?.update(jobId, HlsDownloadStatus.FAILED, exportException.message.orEmpty())
                    finish("変換に失敗しました: ${exportException.message.orEmpty()}")
                }
            }).build()
        runCatching {
            transformer?.start(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(), output!!.absolutePath)
            pollProgress()
        }.onFailure {
            jobs?.update(jobId, HlsDownloadStatus.FAILED, it.javaClass.simpleName)
            finish("開始できません: ${it.javaClass.simpleName}")
        }
        return START_NOT_STICKY
    }

    private fun pollProgress() {
        val current = transformer ?: return
        val holder = ProgressHolder()
        if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification("MP4へ保存中", holder.progress))
        }
        handler.postDelayed(::pollProgress, 1000)
    }

    private fun publish(file: File): Boolean = runCatching {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, uniqueDisplayName(displayName))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { destination -> file.inputStream().use { it.copyTo(destination) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        file.delete()
        true
    }.getOrDefault(false)

    private fun uniqueDisplayName(requested: String): String {
        val dot = requested.lastIndexOf('.')
        val base = requested.substring(0, dot.coerceAtLeast(0)).ifBlank { "video" }
        val ext = if (dot > 0) requested.substring(dot) else ".mp4"
        var candidate = requested
        var index = 1
        while (contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.DISPLAY_NAME}=? AND ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
                arrayOf(candidate, "${Environment.DIRECTORY_DOWNLOADS}%"), null)?.use { it.moveToFirst() } == true) {
            candidate = "$base ($index)$ext"; index++
        }
        return candidate
    }

    private fun safeName(value: String): String = value.ifBlank { "video" }.replace(Regex("[\\/:*?\"<>|]"), "_").take(100)
    private fun notification(text: String, progress: Int) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(displayName).setContentText(text)
        .setOngoing(progress in 0..99).setProgress(100, progress, false).build()
    private fun finish(message: String) {
        handler.removeCallbacksAndMessages(null)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(message, 100))
        transformer = null
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }
    private fun markIntentFailed(intent: Intent?, message: String) {
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        val id = intent?.getStringExtra(EXTRA_JOB_ID).orEmpty()
        if (profileId.isNotBlank() && id.isNotBlank()) HlsDownloadRepository(applicationContext, profileId)
            .update(id, HlsDownloadStatus.FAILED, message)
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        transformer?.cancel()
        output?.delete()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "hls_downloads"
        private const val NOTIFICATION_ID = 7001
        const val EXTRA_URL = "url"
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_USER_AGENT = "user_agent"
        const val EXTRA_REFERER = "referer"
    }
}
