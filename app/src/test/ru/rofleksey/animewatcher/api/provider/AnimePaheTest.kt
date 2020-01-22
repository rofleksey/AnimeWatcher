package com.example.animewatcher.api.provider

import com.example.animewatcher.api.model.EpisodeInfo
import com.example.animewatcher.api.model.Quality
import com.example.animewatcher.api.model.TitleInfo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AnimePaheTest {
    private lateinit var pahe : AnimePahe
    private lateinit var hunter: TitleInfo
    private lateinit var e148: EpisodeInfo

    @BeforeAll
    fun init() {
        pahe = AnimePahe()
    }

    @Test
    @Order(1)
    fun search() {
        val list = runBlocking { pahe.search("hunter") }
        assertFalse(list.isEmpty())
        assertEquals("Hunter x Hunter (2011)", list[0].title)
        hunter = list[0]
    }

    @Test
    @Order(2)
    fun episodePage() {
        val list = runBlocking { pahe.getEpisodeList(hunter, 0) }
        assertFalse(list.isEmpty())
        assertEquals("148", list[0].name)
    }

    @Test
    @Order(3)
    fun allEpisodes() {
        val list = runBlocking { pahe.getAllEpisodes(hunter) }
        assertEquals(146, list.size)
        e148 = list.last()
    }

    @Test
    @Order(4)
    fun storageLink() {
        val map = runBlocking { pahe.getStorageLinks(hunter, e148) }
        assertFalse(map.isEmpty())
        assertTrue(map.containsKey(Quality.q720))
        assertEquals("https://kwik.cx/e/7nbfAA7aJyf1", map[Quality.q720])
    }

    @Test
    @Order(5)
    fun allTitles() {
        val list = runBlocking { pahe.getAllTitles() }
        if (list != null) {
            println(list)
            assertTrue(list.size >= 184)
            assertTrue(list.contains("Bleach"))
        } else {
            fail("didn't return anything!")
        }
    }
}