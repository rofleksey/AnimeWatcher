package com.example.animewatcher.storage

import android.content.SharedPreferences
import com.example.animewatcher.api.model.TitleInfo
import com.google.gson.Gson

class TitleStorage private constructor(@Transient val prefs: SharedPreferences) {
    companion object {
        fun load(prefs: SharedPreferences): TitleStorage {
            val json = prefs.getString("titles", null)
            if (json != null) {
                return Gson().fromJson<TitleStorage>(json, TitleStorage::class.java)
            }
            return TitleStorage(prefs)
        }
    }

    private val _entryList = ArrayList<TitleStorageEntry>()
    val entryList: ArrayList<TitleStorageEntry>
        get() = ArrayList(_entryList)
    val infoList: List<TitleInfo>
        get() = _entryList.map { it.info }

    @Transient
    val gson = Gson()

    fun save() {
        val json = gson.toJson(this)
        println(json)
        prefs.edit().putString("titles", json).apply()
    }

    @Throws(NoSuchElementException::class)
    fun findByName(name: String): TitleStorageEntry {
        return entryList.find {
            it.info.title == name
        } ?: throw NoSuchElementException("Title doesn't exist")
    }

    fun update(func: (ArrayList<TitleStorageEntry>) -> Unit) {
        func(_entryList)
        save()
    }
}