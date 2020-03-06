package ru.rofleksey.animewatcher.api.util

import ru.rofleksey.animewatcher.api.model.Quality
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class ApiUtil {
    companion object {
        fun sanitizeScheme(url: String): String {
            return if (url.startsWith("//")) {
                "https:$url"
            } else url
        }

        fun strToQuality(str: String): Quality {
            return when (str.replace("p", "").trim()) {
                "360" -> Quality.q360
                "480" -> Quality.q480
                "720" -> Quality.q720
                "1080" -> Quality.q1080
                else -> Quality.UNKNOWN
            }
        }

        fun getRegex(str: String, regex: Regex): String {
            val match = regex.find(str) ?: throw IOException("can't match regex $regex")
            return match.groupValues[1]
        }

        fun getRegexAll(str: String, regex: Regex): Sequence<String> {
            return regex.findAll(str).map {
                it.groupValues[1]
            }
        }

        @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class)
        fun sha1(text: String): String {
            val digest: MessageDigest = MessageDigest.getInstance("SHA-1")
            val result = digest.digest(text.toByteArray(Charset.forName("UTF-8")))
            val sb = StringBuilder()
            for (b in result) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }
    }
}