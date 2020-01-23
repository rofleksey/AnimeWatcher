package ru.rofleksey.animewatcher.api.storage

import android.util.Log
import ru.rofleksey.animewatcher.api.Storage
import java.net.URI

class StorageLocator {
    companion object {
        private const val TAG = "StorageLocator"
        fun locate(url: String): Storage? {
            val host = getHost(url) ?: return null
            Log.v(TAG, "locating host: $host")
            return when (host) {
                "kwik.cx" -> KwikStorage.instance
                "mp4upload.com" -> Mp4UploadStorage.instance
                "vidstreaming.io" -> VidStreamingStorage.instance
                "gcloud.live", "feurl.com", "fembed.com" -> XStreamCdnStorage.instance
                else -> null
            }
        }

        private fun getHost(url: String): String? {
            val uri = URI(url)
            val hostname = uri.host
            if (hostname != null) {
                return if (hostname.startsWith("www.")) hostname.substring(4) else hostname
            }
            return null
        }
    }
}