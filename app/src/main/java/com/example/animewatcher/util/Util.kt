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
        val TAG = "animewatcher"
        val FILENAME_REGEX = Regex("[^a-zA-Z0-9.-]")

        fun getEpisodeNumber(s: String): Int {
            if (s.isEmpty()) {
                throw NumberFormatException("String is empty")
            }
            val index = s.indexOfFirst { it != '0' }
            if (index == -1) {
                return 0
            }
            return s.substring(index until s.length).toInt()
        }

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