package com.example.animewatcher.api.provider

import com.example.animewatcher.api.model.Quality
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnimePaheTest {
    private lateinit var pahe : AnimePahe

    @BeforeAll
    fun init() {
        pahe = AnimePahe()
    }

    @Test
    fun search() {
        val list = runBlocking { pahe.search("hunter") }
        assertFalse(list.isEmpty())
        assertEquals("Hunter x Hunter (2011)", list[0].title)
    }

    @Test
    fun searchExact() {
        val title = runBlocking { pahe.searchExact("Hunter x Hunter (2011)") }
        assertFalse(title == null)
        assertEquals("Hunter x Hunter (2011)", title?.title)
    }

    @Test
    fun searchExactInvalid() {
        val title = runBlocking { pahe.searchExact("Hunter x Plumber") }
        assertTrue(title == null)
    }

    @Test
    fun episodePage() {
        val list = runBlocking { pahe.getEpisodeList("Hunter x Hunter (2011)", 0) }
        assertFalse(list.isEmpty())
        assertEquals("148", list[0].name)
    }

    @Test
    fun allEpisodes() {
        val list = runBlocking { pahe.getAllEpisodes("Hunter x Hunter (2011)") }
        assertEquals(146, list.size)
    }

    @Test
    fun storageLink() {
        val map = runBlocking { pahe.getStorageLink("Hunter x Hunter (2011)", 148) }
        assertFalse(map.isEmpty())
        assertTrue(map.containsKey(Quality.q720))
        assertEquals("https://kwik.cx/e/7nbfAA7aJyf1", map[Quality.q720])
    }
}