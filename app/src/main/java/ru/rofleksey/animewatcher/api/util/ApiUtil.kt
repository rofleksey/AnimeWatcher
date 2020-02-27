package ru.rofleksey.animewatcher.api.util

import ru.rofleksey.animewatcher.api.model.Quality
import java.io.IOException

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
            val match = regex.find(str) ?: throw IOException("can't match regex!")
            return match.groupValues[1]
        }
    }
}