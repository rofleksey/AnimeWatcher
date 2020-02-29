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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.activity_gif_save.*
import kotlinx.coroutines.*
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import ru.rofleksey.animewatcher.R
import ru.rofleksey.animewatcher.util.AnimeUtils
import java.io.File
import java.lang.Runnable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.*
import kotlin.random.Random


class GifSaveActivity : AppCompatActivity(),
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {
    companion object {
        const val TAG = "GifSaveActivity"
        const val ARG_FILE = "argFile"
        const val FAST_SEEK_TIME_FAR = 10000L
        const val FAST_SEEK_TIME_NEAR = 75L
        const val SEEK_MULT_FAR = 1f
        const val SEEK_MULT_NEAR = 0.01f
        const val SEEK_FUNC_MULT = 5f
        const val SEEK_FUNC_POWER = 2f
        const val SEEK_ANIMATION_TIME = 150L
        const val CONTROLS_ANIMATION_TIME = 200L
        const val LOADING_ANIMATION_TIME = 450L
        const val HIDE_CONTROLS_TIME = 3500L
    }

    private lateinit var filePath: String
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var exoPlayer: SimpleExoPlayer

    private val handler = Handler()

    private var stopPosition: Long = 0
    private var scrollStartPosition: Long = 0
    private var scrolling: Boolean = false
    private val scrollDistance = ScrollDistance(0f, 0f)
    private var shouldExecuteOnResume: Boolean = false

    private var gifStartPosition: Long = -1
    private var gifEndPosition: Long = -1
    private var controlsOpen: Boolean = false

    private var seekProcessing: Boolean = false
    private var seekAnimation: YoYo.YoYoString? = null

    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    inner class ScrollDistance(var dx: Float, var dy: Float) {
        fun add(x: Float, y: Float) {
            Log.v(TAG, "dx, dy = $x, $y")
            dx += sign(x) * SEEK_FUNC_MULT * abs(x).pow(SEEK_FUNC_POWER)
            dy += sign(y) * SEEK_FUNC_MULT * abs(y).pow(SEEK_FUNC_POWER)
        }

        fun reset() {
            dx = 0f
            dy = 0f
        }

        fun seekValue(): Float {
            return if (!exoPlayer.isPlaying) -dx * SEEK_MULT_NEAR else -dx * SEEK_MULT_FAR
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        supportActionBar?.hide()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_gif_save)

        filePath = intent.getStringExtra(ARG_FILE) ?: ""
        gestureDetector = GestureDetectorCompat(this, this)

        surface_view.setOnTouchListener { _, event ->
            if (gestureDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_UP && scrolling) {
                Log.v(TAG, "scroll end")
                val seekValue = scrollDistance.seekValue()
                seek_text.visibility = View.GONE
                val newPos = max(0f, (exoPlayer.currentPosition + seekValue)).toLong()
                if (newPos != exoPlayer.currentPosition) {
                    exoPlayer.seekTo(newPos)
                }
                scrolling = false
                scrollDistance.reset()
            }
            false
        }
        button_start.setOnClickListener {
            AnimeUtils.vibrate(this, 20)
            AnimeUtils.toast(this, "Start set")
            setIntervals(exoPlayer.currentPosition, gifEndPosition)
        }
        button_end.setOnClickListener {
            AnimeUtils.vibrate(this, 20)
            AnimeUtils.toast(this, "End set")
            setIntervals(gifStartPosition, exoPlayer.currentPosition)
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
        if (savedInstanceState != null) {
            stopPosition = savedInstanceState.getLong("stopPosition")
            Log.v(TAG, "lifecycle: restored stopPosition = $stopPosition")
        }
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
                if (playbackState == Player.STATE_READY) {
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
                    //exoPlayer.playWhenReady = true
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
        exoPlayer.playWhenReady = true
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
        if (shouldExecuteOnResume) {
            exoPlayer.playWhenReady = true
        }
        shouldExecuteOnResume = true
    }

    override fun onPause() {
        Log.v(TAG, "lifecycle: onPause")
        super.onPause()
        exoPlayer.stop()
        stopPosition = exoPlayer.currentPosition
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("stopPosition", stopPosition)
        Log.v(TAG, "lifecycle: stored stopPosition = $stopPosition")
    }

    override fun onStop() {
        Log.v(TAG, "lifecycle: onStop")
        super.onStop()
        exoPlayer.release()
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
                val app = FFmpeg.getInstance(this@GifSaveActivity)
                if (!app.isSupported) {
                    throw Exception("FFmpeg is not supported")
                }
                func(app)
            } catch (e: Exception) {
                e.printStackTrace()
                AnimeUtils.toast(this@GifSaveActivity, "error: ${e.message}")
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
            AnimeUtils.vibrate(this@GifSaveActivity, 20)
            AnimeUtils.toast(this@GifSaveActivity, "Result saved to $outputFileName")
        }
    }

    private fun saveGif() {
        runJob { app ->
            val startSeconds = gifStartPosition / 1000f
            val duration = gifEndPosition / 1000f - startSeconds
            val gifFileName = "${File(filePath).name}_${Random.nextLong()}.gif"
            val inputPath = File(filePath).canonicalPath
            Log.v(TAG, "FFmpeg processing started!")
            val result = suspendCoroutine<String> { cont ->
                app.execute(
                    arrayOf(
                        "-ss",
                        startSeconds.toString(),
                        "-t",
                        duration.toString(),
                        "-i",
                        inputPath,
                        "-loop",
                        "0",
                        "-vf",
                        "fps=30,scale=960:-1",
                        File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                            gifFileName
                        ).toString()
                    ), object :
                        ExecuteBinaryResponseHandler() {
                        override fun onFailure(message: String) {
                            cont.resumeWithException(Exception(message))
                        }

                        override fun onSuccess(message: String) {
                            cont.resume(message)
                        }
                    })
            }
            Log.v(TAG, "result = $result")
            AnimeUtils.vibrate(this@GifSaveActivity, 20)
            AnimeUtils.toast(this@GifSaveActivity, "Result saved to $gifFileName")
        }
    }

    private fun setIntervals(start: Long, end: Long) {
        gifStartPosition = start
        gifEndPosition = end
        button_start.setBackgroundColor(
            ContextCompat.getColor(
                this, when {
                    start < 0L -> {
                        R.color.colorBlack
                    }
                    start >= 0L -> {
                        R.color.accent
                    }
                    else -> R.color.colorBlack
                }
            )
        )
        button_end.setBackgroundColor(
            ContextCompat.getColor(
                this, when {
                    end < 0L -> {
                        R.color.colorBlack
                    }
                    end >= 0L -> {
                        R.color.accent
                    }
                    else -> R.color.colorBlack
                }
            )
        )
        button_encode.setBackgroundColor(
            ContextCompat.getColor(
                this, when {
                    gifEndPosition >= 0 && gifStartPosition in 0 until gifEndPosition -> {
                        R.color.accent
                    }
                    else -> {
                        R.color.colorBlack
                    }
                }
            )
        )
    }

    private fun formatTimeDelta(s: Int): String {
        val seconds = abs(s)
        if (seconds < 60) {
            return "${seconds}s"
        }
        return "${seconds / 60}m ${seconds % 60}s"
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
            scrollStartPosition = exoPlayer.currentPosition
            return true
        }
        scrollDistance.add(distanceX, distanceY)
        if (abs(scrollDistance.dx) > 0) {
            val seekVal = scrollDistance.seekValue()
            seek_text.visibility = View.VISIBLE
            val prefix = if (seekVal < 0) "<<" else ">>"
            if (exoPlayer.isPlaying) {
                val seconds = ceil(seekVal / 1000.0).toInt()
                seek_text.text = "$prefix ${formatTimeDelta(seconds)}"
            } else {
                seek_text.text = "$prefix ${ceil(seekVal)}ms"
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
            else -> {
                if (exoPlayer.isPlaying) {
                    exoPlayer.setSeekParameters(SeekParameters.EXACT)
                    stopPosition = exoPlayer.currentPosition
                    Log.v(TAG, "stopPosition = $stopPosition")
                    exoPlayer.playWhenReady = false
                    showControls(true)
                } else {
                    exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    exoPlayer.playWhenReady = true
                }
            }
        }
        return true
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
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        Log.v(TAG, "onSingleTapConfirmed")
        showControls(!controlsOpen)
        return true
    }
}
