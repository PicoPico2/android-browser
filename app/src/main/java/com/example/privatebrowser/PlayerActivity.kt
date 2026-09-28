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
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@UnstableApi
class PlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var catalog: MediaCatalog
    private var mediaId = ""
    private var zoom = 1f
    private var ctrlDown = false
    private var frameStartX = 0f
    private var frameBasePosition = 0L
    private var pendingFramePosition = 0L
    private var panLastX = 0f
    private var panLastY = 0f
    private var translationX = 0f
    private var translationY = 0f
    private lateinit var controlsView: LinearLayout
    private val previewWorker = Executors.newSingleThreadExecutor()
    private val previewGeneration = AtomicInteger()
    private val previewCache = android.util.LruCache<Long, Bitmap>(48)
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var updater: Runnable? = null
    private var previewRequest: Runnable? = null
    private var recoveryAttempts = 0

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
        val upstream = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSource = PlayerCache.dataSource(this, upstream)
        val selector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters().setMaxVideoSize(1920, 1080).setExceedVideoConstraintsIfNecessary(true).build()
        }
        player = ExoPlayer.Builder(this)
            .setTrackSelector(selector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 300_000, 1_000, 2_000)
                .setBackBuffer(120_000, true)
                .build())
            .build()
        playerView = PlayerView(this).apply {
            this.player = this@PlayerActivity.player
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        setContentView(buildUi(url))
        player.setMediaItem(MediaItem.Builder().setUri(url)
            .setMimeType(if (url.substringBefore('?').endsWith(".m3u8", true)) MimeTypes.APPLICATION_M3U8 else null).build())
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) recoveryAttempts = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                if (recoveryAttempts >= 2 || isFinishing || isDestroyed) return
                recoveryAttempts++
                val resumeAt = player.currentPosition.coerceAtLeast(0L)
                ui.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        player.prepare()
                        player.seekTo(resumeAt)
                        player.playWhenReady = true
                    }
                }, 500L * recoveryAttempts)
            }
        })
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
        controlsView = controls
        ViewCompat.setOnApplyWindowInsetsListener(controls) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(24, 8, 24, 16 + bottom)
            insets
        }
        val preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = android.view.View.GONE
        }
        loadPoster(preview)
        val time = TextView(this).apply { setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER }
        val transport = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(Button(context).apply { text = "−10秒"; setOnClickListener { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) } })
            addView(Button(context).apply { text = "再生／一時停止"; setOnClickListener { if (player.isPlaying) player.pause() else player.play() } })
            addView(Button(context).apply { text = "+10秒"; setOnClickListener {
                val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                player.seekTo((player.currentPosition + 10_000L).coerceAtMost(end))
            } })
        }
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
                schedulePreview(url, target, preview)
            }
            override fun onStartTrackingTouch(bar: SeekBar) { preview.visibility = android.view.View.VISIBLE }
            override fun onStopTrackingTouch(bar: SeekBar) {
                if (player.duration > 0) player.seekTo(player.duration * bar.progress / 1000L)
                preview.postDelayed({ preview.visibility = android.view.View.GONE }, 300)
            }
        })
        controls.addView(preview, LinearLayout.LayoutParams(-1, 180))
        controls.addView(transport)
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
            if (zoom <= 1.01f) {
                translationX = 0f; translationY = 0f
                playerView.translationX = 0f; playerView.translationY = 0f
            } else applyPanBounds()
            return true
        }
    }) }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (ctrlDown) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    frameStartX = event.x
                    frameBasePosition = player.currentPosition
                    pendingFramePosition = frameBasePosition
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val steps = ((event.x - frameStartX) / 36f).toInt()
                    val frameRate = player.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
                    val frameDurationMs = (1000f / frameRate).toLong().coerceAtLeast(1L)
                    val upper = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    pendingFramePosition = (frameBasePosition + steps * frameDurationMs).coerceIn(0, upper)
                    return true
                }
                MotionEvent.ACTION_UP -> { player.seekTo(pendingFramePosition); return true }
                MotionEvent.ACTION_CANCEL -> return true
            }
        }
        if (zoom > 1.01f && event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.pointerCount > 1) {
            val remaining = if (event.actionIndex == 0) 1 else 0
            panLastX = event.getX(remaining)
            panLastY = event.getY(remaining)
            return true
        }
        val overVideo = !::controlsView.isInitialized || event.y < controlsView.top
        if (zoom > 1.01f && overVideo && event.pointerCount == 1 && !scaleDetector.isInProgress) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { panLastX = event.x; panLastY = event.y; return true }
                MotionEvent.ACTION_MOVE -> {
                    translationX += event.x - panLastX
                    translationY += event.y - panLastY
                    panLastX = event.x; panLastY = event.y
                    applyPanBounds()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        ctrlDown = event.isCtrlPressed
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun applyPanBounds() {
        val maxX = playerView.width * (zoom - 1f) / 2f
        val maxY = playerView.height * (zoom - 1f) / 2f
        translationX = translationX.coerceIn(-maxX, maxX)
        translationY = translationY.coerceIn(-maxY, maxY)
        playerView.translationX = translationX
        playerView.translationY = translationY
    }

    private fun schedulePreview(url: String, positionMs: Long, target: ImageView) {
        previewRequest?.let(ui::removeCallbacks)
        val quantized = positionMs / 5_000L * 5_000L
        previewCache.get(quantized)?.let { target.setImageBitmap(it); return }
        val request = Runnable { loadPreview(url, quantized, target) }
        previewRequest = request
        ui.postDelayed(request, 160)
    }

    private fun loadPreview(url: String, positionMs: Long, target: ImageView) {
        val generation = previewGeneration.incrementAndGet()
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
            if (bitmap != null && !isLikelyGreen(bitmap)) {
                previewCache.put(positionMs, bitmap)
                if (generation == previewGeneration.get()) target.post { target.setImageBitmap(bitmap) }
            }
        }
    }

    private fun isLikelyGreen(bitmap: Bitmap): Boolean {
        if (bitmap.width == 0 || bitmap.height == 0) return true
        var green = 0
        var checked = 0
        for (x in 0 until bitmap.width step (bitmap.width / 8).coerceAtLeast(1)) {
            for (y in 0 until bitmap.height step (bitmap.height / 8).coerceAtLeast(1)) {
                val color = bitmap.getPixel(x, y)
                if (android.graphics.Color.green(color) > android.graphics.Color.red(color) * 2 &&
                    android.graphics.Color.green(color) > android.graphics.Color.blue(color) * 2) green++
                checked++
            }
        }
        return checked > 0 && green * 4 > checked * 3
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
                if (player.duration > 0 && !progressSeek.isPressed) progressSeek.secondaryProgress = (player.bufferedPosition * 1000 / player.duration).toInt()
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
        previewRequest?.let(ui::removeCallbacks)
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
