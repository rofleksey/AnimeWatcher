package com.example.animewatcher.storage

import android.content.SharedPreferences
import com.example.animewatcher.api.model.TitleInfo
import com.google.gson.Gson

class TitleStorage private constructor(val prefs: SharedPreferences) {
    companion object {
        fun load(prefs: SharedPreferences): TitleStorage {
            return TitleStorage(prefs).also { it.reload() }
        }
    }

    private val data: TitleStorageData = TitleStorageData(ArrayList<TitleStorageEntry>())
    private val gson = Gson()

    val entryList: ArrayList<TitleStorageEntry>
        get() = data.entryList

    val infoList: List<TitleInfo>
        get() = entryList.map { it.info }

    fun reload() {
        val json = prefs.getString("title_storage", null)
        if (json != null) {
            val otherData = Gson().fromJson<TitleStorageData>(json, TitleStorageData::class.java)
            data.entryList.clear()
            data.entryList.addAll(otherData.entryList)
        }
    }

    fun save() {
        val json = gson.toJson(data)
        prefs.edit().putString("title_storage", json).apply()
    }

    @Throws(NoSuchElementException::class)
    fun findByName(name: String): TitleStorageEntry {
        return entryList.find {
            it.info.title == name
        } ?: throw NoSuchElementException("Title doesn't exist")
    }

    fun update(func: (ArrayList<TitleStorageEntry>) -> Unit) {
        func(entryList)
        save()
    }

    private class TitleStorageData(val entryList: ArrayList<TitleStorageEntry>)
}