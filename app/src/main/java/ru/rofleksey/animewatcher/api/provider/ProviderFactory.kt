package ru.rofleksey.animewatcher.api.provider

import android.content.Context
import android.content.SharedPreferences
import ru.rofleksey.animewatcher.api.AnimeProvider

class ProviderFactory {
    companion object {
        const val ANIMEPAHE = "animepahe"
        const val ANIMEDUB = "animedub"
        const val GOGOANIME = "gogoanime"
        const val DEFAULT = GOGOANIME

        @Throws(NoSuchElementException::class)
        fun get(name: String): AnimeProvider {
            return when (name) {
                ANIMEPAHE -> AnimePahe()
                ANIMEDUB -> AnimeDub()
                GOGOANIME -> GogoAnime()
                else -> throw NoSuchElementException("Invalid anime provider")
            }
        }

        suspend fun init(
            context: Context, prefs: SharedPreferences,
            updateStatus: (title: String) -> Unit
        ) {
            AnimePahe().init(context, prefs, updateStatus)
        }
    }
}