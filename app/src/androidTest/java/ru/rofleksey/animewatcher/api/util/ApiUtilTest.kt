package ru.rofleksey.animewatcher.api.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiUtilTest {
    @Test
    fun sha1() {
        val from =
            "hunt15834914912b3cb006307580baa899e85b11489822ca27abe08814673e022f2566818ead5c178.71.77.125sa"
        val to = "527CA439111727E876A09BD0C579ADBD8F5DC151"
        assertEquals(to, ApiUtil.sha1(from).toUpperCase())
    }

    @Test
    fun sha1_kickass() {
        val title = "jojo"
        val mt = "1583499271"
        val sig = "455751bb9b9074ffc053fb58a63264d924176856e8450b029c506ea1835470bb"
        val clip = "178.71.77.125"
        val stringToHash = "${title}${mt}${sig}${clip}sa"
        val signature = ApiUtil.sha1(stringToHash).toUpperCase()
        val expected = "ABBE1182109CFB033945A20E59819A5AA1241775"
        assertEquals(expected, signature)
    }
}