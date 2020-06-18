package ru.rofleksey.animewatcher.util

import android.os.Handler

class Debounce(val interval: Long) {
    private val mHandler = Handler()

    fun attempt(func: Runnable) {
        stop()
        mHandler.postDelayed(func, interval)
    }

    fun stop() {
        mHandler.removeCallbacksAndMessages(null)
    }
}