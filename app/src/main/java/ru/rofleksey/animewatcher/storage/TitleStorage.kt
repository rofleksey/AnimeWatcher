package ru.rofleksey.animewatcher.storage

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import ru.rofleksey.animewatcher.api.model.TitleInfo

class TitleStorage private constructor(val prefs: SharedPreferences) {
    companion object {
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

    val infoList: List<TitleInfo>
        get() = entryList.map { it.info }

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
        println("json - $json")
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