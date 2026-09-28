package com.example.privatebrowser

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import java.util.concurrent.Executors

@UnstableApi
class PlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var catalog: MediaCatalog
    private var mediaId = ""
    private var zoom = 1f
    private var ctrlDown = false
    private var frameStartX = 0f
    private var frameSteps = 0
    private val previewWorker = Executors.newSingleThreadExecutor()
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var updater: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        mediaId = intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty()
        catalog = MediaCatalog(applicationContext, profileId)
        val headers = buildMap {
            intent.getStringExtra(EXTRA_USER_AGENT)?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
            intent.getStringExtra(EXTRA_REFERER)?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
            android.webkit.CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        }
        val dataSource = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        val selector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters().setMaxVideoSize(1920, 1080).setExceedVideoConstraintsIfNecessary(true).build()
        }
        player = ExoPlayer.Builder(this)
            .setTrackSelector(selector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .build()
        playerView = PlayerView(this).apply { this.player = this@PlayerActivity.player; useController = true }
        setContentView(buildUi(url))
        player.setMediaItem(MediaItem.Builder().setUri(url)
            .setMimeType(if (url.substringBefore('?').endsWith(".m3u8", true)) MimeTypes.APPLICATION_M3U8 else null).build())
        player.prepare()
        player.seekTo(catalog.position(mediaId))
        player.playWhenReady = true
        startProgressUpdates()
    }

    private fun buildUi(url: String): android.view.View {
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 16)
            setBackgroundColor(0x99000000.toInt())
        }
        val preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = android.view.View.GONE
        }
        loadPoster(preview)
        val time = TextView(this).apply { setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER }
        val seek = SeekBar(this).apply { max = 1000 }
        val speedLabel = TextView(this).apply { setTextColor(android.graphics.Color.WHITE); text = "再生速度 1.00×" }
        val speed = SeekBar(this).apply {
            max = 175
            progress = 75
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    val rate = (25 + value) / 100f
                    speedLabel.text = "再生速度 %.2f×".format(rate)
                    if (fromUser) player.playbackParameters = PlaybackParameters(rate)
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (!fromUser || player.duration <= 0) return
                val target = player.duration * value / 1000L
                time.text = formatTime(target)
                preview.visibility = android.view.View.VISIBLE
                loadPreview(url, target, preview)
            }
            override fun onStartTrackingTouch(bar: SeekBar) { preview.visibility = android.view.View.VISIBLE }
            override fun onStopTrackingTouch(bar: SeekBar) {
                if (player.duration > 0) player.seekTo(player.duration * bar.progress / 1000L)
                preview.postDelayed({ preview.visibility = android.view.View.GONE }, 300)
            }
        })
        controls.addView(preview, LinearLayout.LayoutParams(-1, 180))
        controls.addView(time)
        controls.addView(seek)
        controls.addView(speedLabel)
        controls.addView(speed)
        root.addView(controls, FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        progressSeek = seek
        progressText = time
        return root
    }

    private lateinit var progressSeek: SeekBar
    private lateinit var progressText: TextView
    private val scaleDetector by lazy { ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 4f)
            playerView.scaleX = zoom; playerView.scaleY = zoom
            return true
        }
    }) }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (ctrlDown) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { frameStartX = event.x; frameSteps = 0; return true }
                MotionEvent.ACTION_MOVE -> {
                    val steps = ((event.x - frameStartX) / 36f).toInt()
                    val delta = steps - frameSteps
                    if (delta != 0) {
                        val frameRate = player.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
                        val frameDurationMs = (1000f / frameRate).toLong().coerceAtLeast(1L)
                        val upper = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        player.seekTo((player.currentPosition + delta * frameDurationMs).coerceIn(0, upper))
                        frameSteps = steps
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        ctrlDown = event.isCtrlPressed
        return super.dispatchKeyEvent(event)
    }

    private fun loadPreview(url: String, positionMs: Long, target: ImageView) {
        val headers = HashMap<String, String>()
        intent.getStringExtra(EXTRA_USER_AGENT)?.let { headers["User-Agent"] = it }
        intent.getStringExtra(EXTRA_REFERER)?.let { headers["Referer"] = it }
        previewWorker.execute {
            val bitmap: Bitmap? = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(url, headers)
                    retriever.getFrameAtTime(positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally { retriever.release() }
            }.getOrNull()
            if (bitmap != null) target.post { target.setImageBitmap(bitmap) }
        }
    }

    private fun loadPoster(target: ImageView) {
        val poster = intent.getStringExtra(EXTRA_POSTER).orEmpty()
        if (!poster.startsWith("https://") && !poster.startsWith("http://")) return
        previewWorker.execute {
            val bitmap = runCatching {
                val connection = java.net.URL(poster).openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    intent.getStringExtra(EXTRA_USER_AGENT)?.let { setRequestProperty("User-Agent", it) }
                    intent.getStringExtra(EXTRA_REFERER)?.let { setRequestProperty("Referer", it) }
                }
                connection.getInputStream().use { input -> android.graphics.BitmapFactory.decodeStream(input) }
            }.getOrNull()
            if (bitmap != null) target.post { target.setImageBitmap(bitmap) }
        }
    }

    private fun startProgressUpdates() {
        updater = object : Runnable {
            override fun run() {
                if (player.duration > 0 && !progressSeek.isPressed) progressSeek.progress = (player.currentPosition * 1000 / player.duration).toInt()
                if (!progressSeek.isPressed) progressText.text = "${formatTime(player.currentPosition)} / ${formatTime(player.duration.coerceAtLeast(0))}"
                ui.postDelayed(this, 500)
            }
        }.also(ui::post)
    }

    override fun onStop() {
        catalog.savePosition(mediaId, player.currentPosition)
        player.pause() // Background playback is deliberately off by default.
        super.onStop()
    }

    override fun onDestroy() {
        updater?.let(ui::removeCallbacks)
        previewWorker.shutdownNow()
        playerView.player = null
        player.release()
        super.onDestroy()
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    companion object {
        const val EXTRA_URL = "media_url"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_MEDIA_ID = "media_id"
        const val EXTRA_USER_AGENT = "user_agent"
        const val EXTRA_REFERER = "referer"
        const val EXTRA_POSTER = "poster"
    }
}
