package ru.rofleksey.animewatcher.api.provider

import android.content.Context

class ProviderFactory {
    companion object {
        const val ANIMEPAHE = "animepahe"
        const val ANIMEDUB = "animedub"
        const val GOGOANIME = "gogoanime"
        const val KICKASSANIME = "kickassanime"
        const val KISSANIME = "kissanime"
        const val YOUTUBE = "youtube playlists"
        const val DEFAULT = ANIMEPAHE

        @Throws(NoSuchElementException::class)
        fun get(context: Context, name: String): AnimeProvider {
            return when (name) {
                ANIMEPAHE -> AnimePahe(context)
                ANIMEDUB -> AnimeDub(context)
                GOGOANIME -> GogoAnime(context)
                KICKASSANIME -> KickassAnime(context)
                KISSANIME -> KissAnime(context)
                YOUTUBE -> YoutubeProvider(context)
                else -> throw NoSuchElementException("Invalid anime provider")
            }
        }
    }
}