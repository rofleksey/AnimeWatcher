package ru.rofleksey.animewatcher.activity

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.Window.FEATURE_NO_TITLE
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.list.listItems
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.activity_player.*
import kotlinx.coroutines.*
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import ru.rofleksey.animewatcher.R
import ru.rofleksey.animewatcher.util.AnimeUtils
import java.io.File
import java.lang.Runnable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*
import kotlin.random.Random


class PlayerActivity : AppCompatActivity(),
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {
    companion object {
        const val TAG = "GifSaveActivity"
        const val ARG_FILE = "argFile"
        const val PLAY_BUTTON_MAX_FRAME = 25
        const val PLAY_BUTTON_ANIMATION_SPEED = 2.0f
        const val FAST_SEEK_TIME_FAR = 10000L
        const val FAST_SEEK_TIME_NEAR = 75L
        const val SEEK_MULT_FAR = 1f
        const val SEEK_MULT_NEAR = 0.01f
        const val SEEK_FUNC_MULT = 4f
        const val SEEK_FUNC_POWER = 1.6f
        const val SEEK_ANIMATION_TIME = 150L
        const val CONTROLS_ANIMATION_TIME = 200L
        const val LOADING_ANIMATION_TIME = 450L
        const val HIDE_CONTROLS_TIME = 3500L
        const val SEEK_BAR_UPDATE_INTERVAL = 100L
    }

    private lateinit var filePath: String
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var exoPlayer: SimpleExoPlayer

    private val handler = Handler()

    private var stopPosition: Long = 0
    private var seekStartPosition: Long = 0
    private var scrolling: Boolean = false
    private val seekDistanceCounter = SeekDistanceCounter(0f, 0f)
    private var shouldExecuteOnResume: Boolean = false

    private var gifStartPosition: Long = -1
    private var gifEndPosition: Long = -1
    private var controlsOpen: Boolean = false

    private var seekProcessing: Boolean = false
    private var draggingSeekBar: Boolean = false
    private var seekAnimation: YoYo.YoYoString? = null

    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    inner class SeekDistanceCounter(var dx: Float, var dy: Float) {
        fun add(x: Float, y: Float) {
            dx += sign(x) * SEEK_FUNC_MULT * abs(x).pow(SEEK_FUNC_POWER)
            dy += sign(y) * SEEK_FUNC_MULT * abs(y).pow(SEEK_FUNC_POWER)
        }

        fun reset() {
            dx = 0f
            dy = 0f
        }

        fun seekValue(): Float {
            if (abs(dy) > abs(dx)) {
                return 0f
            }
            return if (!exoPlayer.isPlaying) -dx * SEEK_MULT_NEAR else -dx * SEEK_MULT_FAR
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(FEATURE_NO_TITLE)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        supportActionBar?.hide()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_player)

        filePath = intent.getStringExtra(ARG_FILE) ?: ""
        gestureDetector = GestureDetectorCompat(this, this)

        surface_view.setOnTouchListener { _, event ->
            if (gestureDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_UP && scrolling) {
                Log.v(TAG, "scroll end")
                val seekValue = seekDistanceCounter.seekValue()
                seek_text.visibility = View.GONE
                val newPos = max(0f, (exoPlayer.currentPosition + seekValue)).toLong()
                if (newPos != exoPlayer.currentPosition) {
                    exoPlayer.seekTo(newPos)
                }
                scrolling = false
                seekDistanceCounter.reset()
            }
            false
        }
        surface_view.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                showControls(true)
            }
        }
        button_start.setOnClickListener {
            AnimeUtils.vibrate(this, 20)
            AnimeUtils.toast(this, "Start set")
            setIntervals(exoPlayer.currentPosition, gifEndPosition)
        }
        button_start.setOnLongClickListener {
            AnimeUtils.vibrate(this, 20)
            AnimeUtils.toast(this, "Start reset")
            setIntervals(-1, gifEndPosition)
            true
        }
        button_end.setOnClickListener {
            AnimeUtils.vibrate(this, 20)
            AnimeUtils.toast(this, "End set")
            setIntervals(gifStartPosition, exoPlayer.currentPosition)
        }
        button_end.setOnLongClickListener {
            AnimeUtils.vibrate(this, 20)
            AnimeUtils.toast(this, "End reset")
            setIntervals(gifStartPosition, -1)
            true
        }
        button_shot.setOnClickListener {
            showControls(false)
            saveFrame()
        }
        button_encode.setOnClickListener {
            showControls(false)
            AnimeUtils.vibrate(this, 20)
            if (gifStartPosition < 0) {
                AnimeUtils.vibrate(this, 15)
                AnimeUtils.toast(this, "start is not set")
                return@setOnClickListener
            }
            if (gifStartPosition < 0) {
                AnimeUtils.vibrate(this, 15)
                AnimeUtils.toast(this, "end is not set")
                return@setOnClickListener
            }
            if (gifStartPosition >= gifEndPosition) {
                AnimeUtils.vibrate(this, 15)
                AnimeUtils.toast(this, "start is not < end")
                return@setOnClickListener
            }
            saveGif()
        }
        button_play.setMinAndMaxFrame(0, PLAY_BUTTON_MAX_FRAME)
        button_play.setOnClickListener {
            togglePlay()
            showControls(true)
        }
        if (savedInstanceState != null) {
            stopPosition = savedInstanceState.getLong("stopPosition")
            gifStartPosition = savedInstanceState.getLong("gifStartPosition")
            gifEndPosition = savedInstanceState.getLong("gifEndPosition")
            setIntervals(gifStartPosition, gifEndPosition)
            Log.v(TAG, "lifecycle: restored vars from savedInstanceState")
        }
        seek_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (draggingSeekBar) {
                    val dragTime = progress.toLong()
                    if (dragTime != exoPlayer.currentPosition) {
                        exoPlayer.seekTo(dragTime)
                    }
                    showControls(true)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                draggingSeekBar = true
                showControls(true)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                draggingSeekBar = false
                showControls(true)
            }

        })
    }

    private fun initPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBackBuffer(15 * 1000, true)
            .setBufferDurationsMs(
                60 * 1000,
                5 * 60 * 1000,
                5000,
                2000
            )
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS)
            .createDefaultLoadControl()

        exoPlayer = SimpleExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
        exoPlayer.setVideoSurfaceView(surface_view)
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onSeekStarted(eventTime: AnalyticsListener.EventTime) {
                Log.v(TAG, "onSeekStarted")
                if (!seekProcessing && !exoPlayer.isPlaying) {
                    handler.post {
                        seekAnimation?.stop()
                        seekAnimation = YoYo
                            .with(Techniques.FadeIn)
                            .onStart {
                                seek_loading.visibility = View.VISIBLE
                            }
                            .duration(SEEK_ANIMATION_TIME)
                            .playOn(seek_loading)
                    }
                }
                seekProcessing = true
            }

            override fun onPlayerStateChanged(
                eventTime: AnalyticsListener.EventTime,
                playWhenReady: Boolean,
                playbackState: Int
            ) {
                if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
                    updateProgress()
                }
                if (playbackState == Player.STATE_READY) {
                    updateAspectRatio()
                    if (seekProcessing) {
                        seekProcessing = false
                        if (seek_loading.visibility == View.VISIBLE) {
                            handler.post {
                                seekAnimation?.stop()
                                seekAnimation = YoYo
                                    .with(Techniques.FadeOut)
                                    .onEnd {
                                        seek_loading.visibility = View.GONE
                                    }
                                    .duration(SEEK_ANIMATION_TIME)
                                    .playOn(seek_loading)
                            }
                        }
                    }
                }
            }
        })

        val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(
            this,
            Util.getUserAgent(this, "ru.rofleksey.animewatcher")
        )
        exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        exoPlayer.prepare(
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(Uri.parse(filePath))
        )
        updateAspectRatio()
        exoPlayer.playWhenReady = true
    }

    private fun updateAspectRatio() {
        val format = exoPlayer.videoFormat
        if (format != null) {
            aspect_ration_frame_layout.setAspectRatio(format.width.toFloat() / format.height)
        }
    }

    override fun onStart() {
        Log.v(TAG, "lifecycle: onStart")
        super.onStart()
        initPlayer()
        if (stopPosition != 0L) {
            exoPlayer.seekTo(stopPosition)
        }
    }

    override fun onResume() {
        Log.v(TAG, "lifecycle: onResume")
        super.onResume()
        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        showControls(true)
        shouldExecuteOnResume = true
        exoPlayer.playWhenReady = true
        button_play.speed = -PLAY_BUTTON_ANIMATION_SPEED
        button_play.playAnimation()
    }

    override fun onPause() {
        Log.v(TAG, "lifecycle: onPause")
        super.onPause()
        exoPlayer.stop()
        seekProcessing = false
        draggingSeekBar = false

        seek_loading.visibility = View.GONE
        seek_text.visibility = View.GONE
        seekDistanceCounter.reset()
        stopPosition = exoPlayer.currentPosition
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("stopPosition", stopPosition)
        outState.putLong("gifStartPosition", gifStartPosition)
        outState.putLong("gifEndPosition", gifEndPosition)
        Log.v(TAG, "lifecycle: stored vars in onSaveInstanceState")
    }

    override fun onStop() {
        Log.v(TAG, "lifecycle: onStop")
        super.onStop()
        exoPlayer.release()
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(closeControlsRunnable)
    }

    override fun onDestroy() {
        Log.v(TAG, "lifecycle: onDestroy")
        super.onDestroy()
        exoPlayer.release()
        job?.cancel()
    }

    override fun onShowPress(e: MotionEvent?) {
        Log.v(TAG, "onShowPress")
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        Log.v(TAG, "onSingleTapUp")
        return false
    }

    override fun onDown(e: MotionEvent?): Boolean {
        Log.v(TAG, "onDown")
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        Log.v(TAG, "onFling")
        return false
    }

    private fun runJob(func: suspend (app: FFmpeg) -> Unit) {
        job?.cancel()
        val animation = YoYo
            .with(Techniques.FadeIn)
            .onStart {
                loading.visibility = View.VISIBLE
            }
            .duration(LOADING_ANIMATION_TIME)
            .playOn(loading)
        job = coroutineScope.launch {
            try {
                val app = FFmpeg.getInstance(this@PlayerActivity)
                if (!app.isSupported) {
                    throw Exception("FFmpeg is not supported")
                }
                func(app)
            } catch (e: Exception) {
                e.printStackTrace()
                AnimeUtils.toast(this@PlayerActivity, "error: ${e.message}")
            } finally {
                animation.stop()
                YoYo
                    .with(Techniques.FadeOut)
                    .onEnd {
                        loading.visibility = View.GONE
                    }
                    .duration(LOADING_ANIMATION_TIME)
                    .playOn(loading)
            }
        }
    }

    private fun saveFrame() {
        runJob { app ->
            val inputPath = File(filePath).canonicalPath
            val curPos = exoPlayer.currentPosition
            val outputFileName = "${File(filePath).name}_${Random.nextLong()}.jpg"
            val file = File(
                "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/AnimeWatcher",
                outputFileName
            )
            file.parentFile?.mkdirs()
            val result = suspendCancellableCoroutine<String> { cont ->
                val task = app.execute(
                    arrayOf(
                        "-ss",
                        (curPos / 1000f).toString(),
                        "-i",
                        inputPath,
                        "-q:v",
                        "1",
                        "-vframes",
                        "1",
                        file.toString()
                    ), object :
                        ExecuteBinaryResponseHandler() {
                        override fun onFailure(message: String) {
                            cont.resumeWithException(Exception(message))
                        }

                        override fun onSuccess(message: String) {
                            cont.resume(message)
                        }
                    })

                cont.invokeOnCancellation {
                    task.sendQuitSignal()
                }
            }
            Log.v(TAG, "result = $result")
            AnimeUtils.vibrate(this@PlayerActivity, 20)
            AnimeUtils.toast(this@PlayerActivity, "Result saved to $outputFileName")
        }
    }

    @SuppressLint("DefaultLocale")
    private fun saveGif() {
        runJob { app ->
            val startSeconds = gifStartPosition / 1000f
            val duration = gifEndPosition / 1000f - startSeconds
            val inputPath = File(filePath).canonicalPath
            val items = listOf("gif", "mp4", "mp3")
            val ext = suspendCancellableCoroutine<CharSequence> { cont ->
                var chosen = false
                MaterialDialog(this).show {
                    listItems(items = items) { dialog, index, text ->
                        chosen = true
                        cont.resume(text)
                    }
                    onDismiss {
                        if (!chosen) {
                            cont.resumeWithException(Exception("No format chosen!"))
                        }
                    }
                    cont.invokeOnCancellation {
                        this.dismiss()
                    }
                }
            }
            val outputFileName = "${File(filePath).name}_${Random.nextLong()}.${ext}"
            Log.v(TAG, "FFmpeg processing started!")
            val result = suspendCancellableCoroutine<String> { cont ->
                val task = app.execute(
                    when (ext) {
                        "gif" -> {
                            arrayOf(
                                "-ss",
                                startSeconds.toString(),
                                "-i",
                                inputPath,
                                "-t",
                                duration.toString(),
                                "-loop",
                                "0",
                                "-vf",
                                "fps=20,scale=640:-1",
                                File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                                    outputFileName
                                ).toString()
                            )
                        }
                        "mp4" -> {
                            if (AnimeUtils.getFileExt(inputPath).toLowerCase() == "mp4") {
                                arrayOf(
                                    "-ss",
                                    startSeconds.toString(),
                                    "-i",
                                    inputPath,
                                    "-t",
                                    duration.toString(),
                                    "-c",
                                    "copy",
                                    File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                                        outputFileName
                                    ).toString()
                                )
                            } else {
                                arrayOf(
                                    "-ss",
                                    startSeconds.toString(),
                                    "-i",
                                    inputPath,
                                    "-t",
                                    duration.toString(),
                                    File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                                        outputFileName
                                    ).toString()
                                )
                            }
                        }
                        "mp3" -> {
                            arrayOf(
                                "-ss",
                                startSeconds.toString(),
                                "-i",
                                inputPath,
                                "-t",
                                duration.toString(),
                                File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                                    outputFileName
                                ).toString()
                            )
                        }
                        else -> arrayOf("--wtf")
                    }, object :
                        ExecuteBinaryResponseHandler() {
                        override fun onFailure(message: String) {
                            cont.resumeWithException(Exception(message))
                        }

                        override fun onSuccess(message: String) {
                            cont.resume(message)
                        }
                    })
                cont.invokeOnCancellation {
                    task.sendQuitSignal()
                }
            }
            Log.v(TAG, "result = $result")
            AnimeUtils.vibrate(this@PlayerActivity, 20)
            AnimeUtils.toast(this@PlayerActivity, "Result saved to $outputFileName")
        }
    }

    private fun setIntervals(start: Long, end: Long) {
        gifStartPosition = start
        gifEndPosition = end
        //

    }

    private fun formatTimeDelta(s: Int): String {
        val seconds = abs(s)
        if (seconds < 60) {
            return "${seconds}s"
        }
        return "${seconds / 60}m ${seconds % 60}s"
    }

    private fun formatVideoTime(time: Long): String {
        val totalSeconds = max(0, time) / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        if (hours == 0L) {
            return "${minutes.toString().padStart(2, '0')}:" +
                    seconds.toString().padStart(2, '0')
        }
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(
            2,
            '0'
        )}:${seconds.toString().padStart(2, '0')}"
    }

    @SuppressLint("SetTextI18n")
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (e1 == null || e2 == null) {
            Log.v(TAG, "onScroll(null)")
            return false
        }
        if (!scrolling) {
            scrolling = true
            seekStartPosition = exoPlayer.currentPosition
            return true
        }
        seekDistanceCounter.add(distanceX, distanceY)
        if (abs(seekDistanceCounter.dx) > 0) {
            val seekVal = seekDistanceCounter.seekValue()
            seek_text.visibility = View.VISIBLE
            val prefix = if (seekVal < 0) "<<" else ">>"
            if (exoPlayer.isPlaying) {
                val seconds = ceil(seekVal / 1000.0).toInt()
                seek_text.text =
                    "$prefix ${formatTimeDelta(seconds)} (${formatVideoTime((exoPlayer.currentPosition + seekDistanceCounter.seekValue()).toLong())})"
            } else {
                val frames = floor(seekVal / getFrameInterval()).toInt()
                seek_text.text = "$prefix $frames frames"
            }
        } else {
            seek_text.visibility = View.GONE
        }
        return true
    }

    override fun onLongPress(e: MotionEvent?) {
        Log.v(TAG, "onLongPress")
    }

    private var frameIntervalVar: Long = 0
    private fun getFrameInterval(): Long {
        if (frameIntervalVar == 0L) {
            val exoFps = exoPlayer.videoFormat?.frameRate
            frameIntervalVar = if (exoFps == null || exoFps <= 0) {
                FAST_SEEK_TIME_NEAR
            } else {
                ceil(1000 / exoFps).toLong()
            }
        }
        return frameIntervalVar
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        val seekTime = if (exoPlayer.isPlaying) FAST_SEEK_TIME_FAR else getFrameInterval()
        when {
            e.x < surface_view.width / 4 -> {
                val newPos = max(0, exoPlayer.currentPosition - seekTime)
                if (newPos != exoPlayer.currentPosition) {
                    exoPlayer.seekTo(newPos)
                }
            }
            e.x > 3 * surface_view.width / 4 -> {
                exoPlayer.seekTo(exoPlayer.currentPosition + seekTime)
            }
            else -> togglePlay()
        }
        return true
    }

    private val updateProgressRunnable = Runnable {
        updateProgress()
    }

    private fun updateProgress() {
        handler.removeCallbacks(updateProgressRunnable)
        val cur = formatVideoTime(exoPlayer.currentPosition)
        val remaining = formatVideoTime(exoPlayer.duration - exoPlayer.currentPosition)
        text_current.text = cur
        text_remaining.text = remaining
        if (!draggingSeekBar) {
            seek_bar.max = exoPlayer.duration.toInt()
            seek_bar.progress = exoPlayer.currentPosition.toInt()
        }
        handler.postDelayed(updateProgressRunnable, SEEK_BAR_UPDATE_INTERVAL)
    }

    private fun togglePlay() {
        if (exoPlayer.isPlaying) {
            exoPlayer.setSeekParameters(SeekParameters.EXACT)
            stopPosition = exoPlayer.currentPosition
            Log.v(TAG, "stopPosition = $stopPosition")
            exoPlayer.playWhenReady = false
            button_play.speed = PLAY_BUTTON_ANIMATION_SPEED
            button_play.playAnimation()
            showControls(true)
        } else {
            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            exoPlayer.playWhenReady = true
            button_play.speed = -PLAY_BUTTON_ANIMATION_SPEED
            button_play.playAnimation()
        }
    }

    override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
        Log.v(TAG, "onDoubleTapEvent")
        return true
    }

    private val closeControlsRunnable = Runnable {
        showControls(false)
    }

    private fun showControls(show: Boolean) {
        if (show) {
            if (!controlsOpen) {
                YoYo
                    .with(Techniques.FadeIn)
                    .onStart {
                        controls.visibility = View.VISIBLE
                    }
                    .duration(CONTROLS_ANIMATION_TIME)
                    .playOn(controls)
            }
            controlsOpen = true
            // delay hiding
            handler.removeCallbacks(closeControlsRunnable)
            handler.postDelayed(closeControlsRunnable, HIDE_CONTROLS_TIME)
            window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        } else if (controlsOpen && !show) {
            YoYo
                .with(Techniques.FadeOut)
                .duration(CONTROLS_ANIMATION_TIME)
                .onEnd {
                    controls.visibility = View.GONE
                }
                .playOn(controls)
            handler.removeCallbacks(closeControlsRunnable)
            controlsOpen = false
            window.decorView.apply {
                systemUiVisibility =
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        Log.v(TAG, "onSingleTapConfirmed")
        showControls(!controlsOpen)
        return true
    }
}
