package ru.rofleksey.animewatcher.api.model

data class ProviderStats(
    val needsContext: Boolean,
    val hasCloudfare: Boolean,
    val episodesDesc: Boolean,
    val episodesPerPage: Int,
    val loadingString: String? = null
)