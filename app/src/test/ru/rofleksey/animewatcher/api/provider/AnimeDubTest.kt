package com.example.animewatcher.api.provider

import com.example.animewatcher.api.model.EpisodeInfo
import com.example.animewatcher.api.model.Quality
import com.example.animewatcher.api.model.TitleInfo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AnimeDubTest {
    private lateinit var animedub : AnimeDub

    @BeforeAll
    fun init() {
        animedub = AnimeDub()
    }

    @Test
    @Order(1)
    fun search() {
        val list = runBlocking { animedub.search("hunter x hunter") }
        list.forEach {
            println(it)
        }
//        assertFalse(list.isEmpty())
//        assertEquals("Hunter x Hunter (2011)", list[0].title)
    }
}