package com.eddyizm.tempus.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HubClientTest {
    @Test
    fun parseBase_addsHttpWhenNoScheme() {
        val url = HubClient.parseBase("  192.168.1.129:4533/ ")
        assertNotNull(url)
        assertEquals("http://192.168.1.129:4533", HubClient.baseOf(url!!))
    }

    @Test
    fun parseBase_rejectsEmptyAndGarbage() {
        assertNull(HubClient.parseBase(""))
        assertNull(HubClient.parseBase("   "))
        assertNull(HubClient.parseBase("http://"))
    }

    @Test
    fun baseOf_dropsPathQueryAndFragment() {
        val url = HubClient.parseBase("https://music.example.org/app/#/home?x=1")!!
        assertEquals("https://music.example.org", HubClient.baseOf(url))
    }

    @Test
    fun hubCandidates_navidromeOnLanLeadsToHubOn8080() {
        val url = HubClient.parseBase("http://192.168.1.129:4533")!!
        assertEquals(
            listOf("http://192.168.1.129:4533", "http://192.168.1.129:8080"),
            HubClient.hubCandidates(url),
        )
    }

    @Test
    fun hubCandidates_typedHubIsTriedOnce() {
        val url = HubClient.parseBase("192.168.1.129:8080")!!
        assertEquals(listOf("http://192.168.1.129:8080"), HubClient.hubCandidates(url))
    }

    @Test
    fun hubCandidates_vpsPorts() {
        val url = HubClient.parseBase("https://vps.example.org:8444")!!
        assertEquals(
            listOf("https://vps.example.org:8444", "https://vps.example.org:8445"),
            HubClient.hubCandidates(url),
        )
    }

    @Test
    fun hubCandidates_httpsDefaultPortIsNotDowngraded() {
        val url = HubClient.parseBase("https://music.example.org")!!
        assertEquals(listOf("https://music.example.org"), HubClient.hubCandidates(url))
    }

    @Test
    fun pair_badAddressFailsWithoutNetwork() {
        val result = HubClient.pair("http://", "user", "pass", null)
        assertFalse(result.ok)
        assertNotNull(result.error)
    }
}
