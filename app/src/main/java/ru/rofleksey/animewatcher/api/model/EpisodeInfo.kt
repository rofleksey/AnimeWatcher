package ru.rofleksey.animewatcher.api.model

class EpisodeInfo(
    val name: String,
    val image: String?,
    val fields: MutableMap<String, String> = HashMap()
) {
    operator fun get(name: String): String {
        return fields[name]
            ?: throw NoSuchElementException("EpisodeInfo doesn't contain field '$name'")
    }

    operator fun set(name: String, value: String) {
        fields[name] = value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EpisodeInfo
        if (name != other.name) return false
        if (image != other.image) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (image?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "EpisodeInfo(name='$name', image=$image)"
    }


}