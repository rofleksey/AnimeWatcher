package com.example.animewatcher.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Util {
    companion object {
        fun <T> id(x: T): T = x
        val TAG = "animewatcher"
    }
}