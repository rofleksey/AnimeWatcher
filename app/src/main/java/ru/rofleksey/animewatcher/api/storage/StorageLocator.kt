package ru.rofleksey.animewatcher.api.storage

import android.util.Log
import ru.rofleksey.animewatcher.api.storage.english.*
import ru.rofleksey.animewatcher.api.storage.russian.AnimeDubStorage
import ru.rofleksey.animewatcher.api.storage.russian.HaloAniStorage
import ru.rofleksey.animewatcher.api.storage.russian.MailRuStorage
import ru.rofleksey.animewatcher.api.storage.russian.SibnetStorage
import java.net.URI

class StorageLocator {
    companion object {
        private const val TAG = "StorageLocator"
        fun locate(url: String): Storage? {
            return try {
                val host = getHost(url) ?: return null
                Log.v(TAG, "locating host of $url")
                when (host) {
                    "kwik.cx" -> KwikStorage.instance
                    //"mp4upload.com" -> Mp4UploadStorage.instance
                    "vidstreaming.io" -> VidStreamingStorage.instance
                    "gcloud.live", "feurl.com", "fembed.com" -> XStreamCdnStorage.instance
                    "my.mail.ru", "videoapi.my.mail.ru" -> MailRuStorage.instance
                    "video.sibnet.ru" -> SibnetStorage.instance
                    "animedub.ru" -> AnimeDubStorage.instance
                    "animo-pace-stream.io" -> AnimoPaceStream.instance
                    "haloani.ru" -> HaloAniStorage.instance
                    "bitchute.com" -> BitchuteStorage.instance
                    else -> null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
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