package ru.rofleksey.animewatcher.activity

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_secret.*
import ru.rofleksey.animewatcher.R
import java.util.*


class SecretActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SecretActivity"
    }

    private lateinit var sound: MediaPlayer
    private lateinit var timer: Timer
    private lateinit var task: TimerTask
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secret)
        webview.settings.javaScriptEnabled = true
        webview.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.w(TAG, "console: ${msg.message()}")
                return true
            }
        }
        webview.loadUrl("file:///android_res/raw/secret.html")
        sound = MediaPlayer.create(
            this,
            R.raw.king_crimson
        )
        sound.isLooping = true

        timer = Timer()
    }


    override fun onResume() {
        super.onResume()
        sound.start()
        task = object : TimerTask() {
            override fun run() {
                handler.post {
                    try {
                        webview.evaluateJavascript("setTime(${sound.currentPosition});", null)
                    } catch (e: Exception) {
                        Log.e(TAG, e.message ?: "evaluate js error")
                    }
                }
            }
        }
        timer.schedule(task, 50, 25)
    }

    override fun onPause() {
        super.onPause()
        sound.pause()
        task.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
        timer.cancel()
        timer.purge()
        handler.removeCallbacksAndMessages(null)
    }
}
