package com.example.animewatcher.api.storage

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageLocatorTest {
    @Test
    fun kwik() {
        val storage = StorageLocator.locate("https://kwik.cx/e/7nbfAA7aJyf1")
        assertTrue(storage != null)
        assertTrue(storage is KwikStorage)
    }

    @Test
    fun invalid() {
        val storage = StorageLocator.locate("https://example.com")
        assertTrue(storage == null)
    }
}