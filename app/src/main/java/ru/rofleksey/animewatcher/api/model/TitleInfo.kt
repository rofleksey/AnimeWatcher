package ru.rofleksey.animewatcher.api.model

class TitleInfo(val title: String, val details: String, val image: String?, val fields: MutableMap<String, String> = HashMap()) : Comparable<TitleInfo> {
    override fun compareTo(other: TitleInfo): Int {
        return title.compareTo(other.title)
    }

    operator fun get(name: String): String {
        return fields[name] ?: throw NoSuchElementException("TitleInfo doesn't contain field '$name'")
    }

    operator fun set(name: String, value: String) {
        fields[name] = value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TitleInfo

        if (title != other.title) return false
        if (image != other.image) return false
        if (details != other.details) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (image?.hashCode() ?: 0)
        result = 31 * result + details.hashCode()
        return result
    }

    override fun toString(): String {
        return "TitleInfo(title='$title', details='$details', image=$image, fields=$fields)"
    }


}