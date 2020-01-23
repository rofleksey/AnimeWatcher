package ru.rofleksey.animewatcher.api.util

import ru.rofleksey.animewatcher.api.model.Quality

class ApiUtil {
    companion object {
        fun pickQuality(list: List<Pair<Quality, String>>, pref: Quality): String {
            if (list.isEmpty()) {
                throw IllegalArgumentException("List is empty!")
            }
            list.sortedBy { it.first }
            val eq = list.find { it.first == pref }
            if (eq != null) {
                return eq.second
            }
            val index = list.indexOfLast { it.first < pref }
            if (index != -1) {
                return list[index].second
            }
            return list.last().second
        }

        fun sanitizeScheme(url: String): String {
            return if (url.startsWith("//")) {
                "https:$url"
            } else url
        }
    }
}