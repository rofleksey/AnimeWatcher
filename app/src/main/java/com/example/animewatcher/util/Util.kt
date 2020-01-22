package com.example.animewatcher.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.lang.NumberFormatException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Util {
    companion object {
        const val TAG = "animewatcher"
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.88 Safari/537.36"
        private val FILENAME_REGEX = Regex("[^a-zA-Z0-9.-]")

        fun openInVlc(context: Context, str: String) {
            val vlc = Intent(Intent.ACTION_VIEW)
            vlc.setDataAndType(str.toUri(), "video/*")
            context.startActivity(vlc)
        }

        fun toast(context: Context, str: String) {
            Toast.makeText(context, str, Toast.LENGTH_LONG).show()
        }

        fun sanitizeForFileName(s: String): String {
            return s.replace(FILENAME_REGEX, "_")
        }
    }
}