package org.jellyfin.mobile.webapp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SsoNavigationTest {
    private val server = "https://media.example:443/jellyfin"
    private val routes = SsoNavigation(server)
    private val start = "$server/sso/OID/start/Keycloak"
    private val provider = "https://login.example/auth"
    private val callback = "https%3A%2F%2Fmedia.example%2Fjellyfin%2Fsso%2FOID%2Fredirect%2FKeycloak"

    @Test
    fun `recognizes plugin entry and direct authorization redirects`() {
        assertTrue(routes.shouldStart(start, server))
        assertTrue(routes.shouldStart(provider, start))
        assertTrue(routes.shouldStart("$provider?redirect_uri=$callback", server))
        assertFalse(routes.shouldStart(provider, server))
        val otherServer = callback.replace("media.example", "other.example")
        assertFalse(routes.shouldStart("$provider?redirect_uri=$otherServer", server))
    }

    @Test
    fun `resumes only at the selected installation's web index`() {
        assertTrue(routes.isWebIndex("https://media.example/jellyfin/web/index.html"))
        assertFalse(routes.isWebIndex("https://media.example:444/jellyfin/web/index.html"))
        assertFalse(routes.isWebIndex("https://media.example.other.example/jellyfin/web/index.html"))
        assertFalse(routes.isWebIndex("https://media.example/web/index.html"))
        assertFalse(routes.isWebIndex(start))
    }
}
