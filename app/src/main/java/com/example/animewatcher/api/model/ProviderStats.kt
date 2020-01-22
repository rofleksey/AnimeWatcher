package com.example.animewatcher.api.model

data class ProviderStats(
    val needsContext: Boolean,
    val hasCloudfare: Boolean,
    val episodesDesc: Boolean,
    val loadingString: String? = null
)