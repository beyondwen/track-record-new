package com.wenhao.record.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapboxAccessTokenGateTest {
    @Test
    fun `prefers runtime token over bundled token`() {
        assertEquals(
            "pk.runtime-token",
            resolveMapboxAccessToken(
                runtimeToken = "  pk.runtime-token  ",
                bundledToken = "pk.bundled-token",
            )
        )
    }

    @Test
    fun `falls back to bundled token when runtime token is blank`() {
        assertEquals(
            "pk.bundled-token",
            resolveMapboxAccessToken(
                runtimeToken = "   ",
                bundledToken = "pk.bundled-token",
            )
        )
    }

    @Test
    fun `returns false when token is blank`() {
        assertFalse(isMapboxAccessTokenConfigured(""))
        assertFalse(isMapboxAccessTokenConfigured("   "))
    }

    @Test
    fun `returns false when token is placeholder`() {
        assertFalse(isMapboxAccessTokenConfigured("YOUR_MAPBOX_ACCESS_TOKEN"))
        assertFalse(isMapboxAccessTokenConfigured(" your_mapbox_access_token "))
    }

    @Test
    fun `returns true when token looks configured`() {
        assertTrue(isMapboxAccessTokenConfigured("pk.test-token"))
    }
}
