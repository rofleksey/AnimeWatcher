package com.example.animewatcher.api.provider

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
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
        val list = pahe.search("hunter").get(5, TimeUnit.SECONDS)
        assertFalse(list.isEmpty())
        assertEquals("Hunter x Hunter (2011)", list[0].title)
    }

    @Test
    fun episodes() {
        val list = pahe.getEpisodeList(163, 0).get(5, TimeUnit.SECONDS)
        assertFalse(list.isEmpty())
        assertEquals("148", list[0].name)
    }
}