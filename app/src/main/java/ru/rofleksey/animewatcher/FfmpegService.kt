package ru.rofleksey.animewatcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import ru.rofleksey.animewatcher.util.AnimeUtils
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class FfmpegService : Service() {
    companion object {
        private const val TAG = "FfmpegService"
        const val ARG_TITLE = "title"
        const val ARG_ARGUMENTS = "args"
        const val ARG_CONTENT = "content"
        private const val FOREGROUND_ID = 1998
        private const val TITLE = "Processing video in the background..."
        private const val CHANNEL_ID = "AnimeWatcher"
        private const val CHANNEL_NAME = "AnimeWatcher"
        private const val MAX_UPTIME_MINUTES = 180L
        private const val WAKELOCK_TAG = "AnimeWatcher::FfmpegService"
        private val TIME_REGEX = Regex("time=([^\\s]*?)\\s")
    }

    private var job: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var title: String = TITLE
    private lateinit var channelId: String
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationManager.createNotificationChannel(chan)
        return channelId
    }

    override fun onCreate() {
        super.onCreate()
        wakeLock = try {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                    acquire(MAX_UPTIME_MINUTES * 60 * 1000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel(CHANNEL_ID, CHANNEL_NAME)
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }
    }

    private fun getNotification(title: String, content: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            channelId
        ) else Notification.Builder(this)
        return builder
            .setOngoing(true)
            .setProgress(0, 100, true)
            .setSmallIcon(R.drawable.zero2)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setContentText(content)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        title = intent?.getStringExtra(ARG_TITLE) ?: TITLE
        val arguments =
            intent?.getStringArrayExtra(ARG_ARGUMENTS) ?: throw Exception("no arguments provided")
        val notification = getNotification(title, "Initializing...")
        startForeground(FOREGROUND_ID, notification)
        job?.cancel()
        job = coroutineScope.launch {
            try {
                val app = FFmpeg.getInstance(this@FfmpegService)
                if (!app.isSupported) {
                    throw Exception("FFmpeg is not supported")
                }
                val result = suspendCancellableCoroutine<String> { cont ->
                    val task = app.execute(
                        arguments, object :
                            ExecuteBinaryResponseHandler() {
                            override fun onFailure(message: String) {
                                cont.resumeWithException(Exception(message))
                            }

                            override fun onProgress(message: String?) {
                                val msg = message ?: "initializing..."
                                val content = when (val match = TIME_REGEX.find(msg)) {
                                    null -> msg
                                    else -> "${match.groupValues[1]} processed so far"
                                }
                                notificationManager.notify(
                                    FOREGROUND_ID,
                                    getNotification(title, content)
                                )
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
                AnimeUtils.vibrate(this@FfmpegService, 20)
                AnimeUtils.toast(this@FfmpegService, "Done!")
            } catch (e: Exception) {
                e.printStackTrace()
                AnimeUtils.toast(this@FfmpegService, "error: ${e.message}")
            } finally {
                stopSelf()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        job?.cancel()
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
