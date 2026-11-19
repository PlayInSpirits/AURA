package com.eddyizm.tempus.hub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubPlaybackTest {
    @Test
    fun streamCookieIsOnlyForThePairedHubOriginAndStreamEndpoint() {
        val paired = "https://hub.example:8445"
        assertTrue(HubPlayback.isHubStream("${paired}/api/listen/stream?ref=dz%3A42", paired))
        for (other in listOf(
            "http://hub.example:8445/api/listen/stream",
            "https://hub.example/api/listen/stream",
            "https://hub.example.invalid:8445/api/listen/stream",
            "https://hub.example:8445/api/listen/stream-other",
            "https://hub.example:8445/rest/stream"
        )) {
            assertFalse(other, HubPlayback.isHubStream(other, paired))
        }
        assertFalse(HubPlayback.isHubStream("${paired}/api/listen/stream", null))
    }
}
