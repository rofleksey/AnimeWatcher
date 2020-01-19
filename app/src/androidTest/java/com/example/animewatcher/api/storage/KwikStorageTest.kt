package com.example.animewatcher.api.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class KwikStorageTest {

    @Test
    fun extractStream() {
        val link = runBlocking { KwikStorage.instance.extractStream("https://kwik.cx/e/7nbfAA7aJyf1") }
        assertTrue(link.startsWith("https"))
        assertTrue(link.contains("stream"))
        //https://eu1.files.nextstream.org/stream/0002/deb877e560780fb3bd4fe73353ea40b366f0ae4999819933830792b1a4b359ff/uwu.m3u8?token=z1jxq5Ve9r958JKR-lDlHA&expires=1579404934
    }

    @Test
    fun extractDownload() {
        val link = runBlocking { KwikStorage.instance.extractDownload("https://kwik.cx/e/7nbfAA7aJyf1") }
        assertTrue(link.startsWith("https"))
        assertTrue(link.contains("mp4"))
        println(link)
        //https://eu1.files.nextstream.org/get/AED2ONNgls_E9UvjM_Oc2g/1579405601/mp4/0002/deb877e560780fb3bd4fe73353ea40b366f0ae4999819933830792b1a4b359ff/AnimePahe_Hunter_x_Hunter_2011_-_148_BD_720p_TenB.mp4
    }
}