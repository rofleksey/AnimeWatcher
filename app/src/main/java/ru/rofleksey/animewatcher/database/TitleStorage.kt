package ru.rofleksey.animewatcher.database

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder

class TitleStorage private constructor(val prefs: SharedPreferences) {
    companion object {
        private const val TAG = "TitleStorage"
        fun load(prefs: SharedPreferences): TitleStorage {
            return TitleStorage(prefs).also { it.reload() }
        }

        private fun getGson(): Gson {
            return GsonBuilder().enableComplexMapKeySerialization()
                .setPrettyPrinting().create()
        }
    }

    private val data: TitleStorageData = TitleStorageData(ArrayList())
    private val gson = getGson()


    val entryList: ArrayList<TitleStorageEntry>
        get() = data.entryList

    fun reload() {
        val json = prefs.getString("title_storage", null)
        if (json != null) {
            val otherData = getGson().fromJson<TitleStorageData>(json, TitleStorageData::class.java)
            data.entryList.clear()
            data.entryList.addAll(otherData.entryList)
        }
    }

    fun save() {
        val json = gson.toJson(data)
        //Log.v(TAG, "json - $json")
        prefs.edit().putString("title_storage", json).apply()
    }

    fun debug(): String {
        return gson.toJson(data)
    }

    fun hasTitle(name: String, provider: String): Boolean {
        return entryList.any {
            it.info.title == name && it.provider == provider
        }
    }

    @Throws(NoSuchElementException::class)
    fun findByName(name: String, provider: String): TitleStorageEntry {
        return entryList.find {
            it.info.title == name && it.provider == provider
        } ?: throw NoSuchElementException("Title doesn't exist")
    }

    fun update(func: (ArrayList<TitleStorageEntry>) -> Unit) {
        func(entryList)
        save()
    }

    private class TitleStorageData(val entryList: ArrayList<TitleStorageEntry>)
}