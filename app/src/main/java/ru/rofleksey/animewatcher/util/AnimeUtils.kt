package ru.rofleksey.animewatcher.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import ru.rofleksey.animewatcher.R
import ru.rofleksey.animewatcher.api.model.Quality


class AnimeUtils {
    companion object {
        const val TAG = "animewatcher"
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.88 Safari/537.36"
        private val FILENAME_REGEX = Regex("[^a-zA-Z0-9.-]")

        fun openInChrome(context: Context, url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setPackage("com.android.chrome")
                intent.data = Uri.parse(url)
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                openVideo(context, url)
            }
        }

        fun open(context: Context, url: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            context.startActivity(intent)
        }

        fun openVideo(context: Context, url: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(url), "video/*")
            context.startActivity(intent)
        }

        fun toast(context: Context, str: String) {
            Toast.makeText(context, str, Toast.LENGTH_LONG).show()
        }

        fun vibrate(context: Context, duration: Long) {
            try {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (!v.hasVibrator()) {
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(
                        VibrationEffect.createOneShot(
                            duration,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else { //deprecated in API 26
                    v.vibrate(duration)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun sanitizeForFileName(s: String): String {
            return s.replace(FILENAME_REGEX, "_")
        }

        fun spotlightLayout(activity: Activity, text: String): ViewGroup {
            val layout = activity.layoutInflater.inflate(R.layout.spotlight_layout, null)
            val textView: TextView = layout.findViewById(R.id.spotlight_text)
            textView.text = text
            return layout as ViewGroup
        }

        fun qualityToStr(quality: Quality): String {
            val number = quality.num
            return if (number == 0) {
                "???p"
            } else {
                "${number}p"
            }
        }

        fun getFileFromIntentData(context: Context, uri: Uri?): String? {
            if (uri == null) {
                return null
            }
            var filePath: String? = null
            if ("content" == uri.scheme) {
                val cursor: Cursor? = context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Images.ImageColumns.DATA),
                    null,
                    null,
                    null
                )
                if (cursor != null) {
                    cursor.moveToFirst()
                    filePath = cursor.getString(0)
                    cursor.close()
                }
            } else {
                filePath = uri.path
            }
            return filePath
        }

        fun getFileExt(path: String): String {
            val lastDot = path.lastIndexOf(".")
            if (lastDot > 0) {
                return path.substring(lastDot + 1)
            }
            return ""
        }
    }
}