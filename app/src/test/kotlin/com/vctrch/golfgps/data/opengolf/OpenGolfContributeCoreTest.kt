package com.vctrch.golfgps.data.opengolf

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGolfIdentityTest {
    @Test
    fun normalizeEmail_trimsAndLowercases() {
        assertEquals("golfer@example.com", OpenGolfIdentity.normalizeEmail("  Golfer@Example.COM "))
    }

    @Test
    fun deriveOpenGolfId_isStablePrefix() {
        val id = OpenGolfIdentity.deriveOpenGolfId("golfer@example.com")
        assertTrue(id.startsWith("ogid_"))
        assertEquals(21, id.length)
        assertEquals(id, OpenGolfIdentity.deriveOpenGolfId("Golfer@Example.com"))
    }

    @Test
    fun contributionPlayerId_mapsOgid() {
        assertEquals("cg_abcdef", OpenGolfIdentity.contributionPlayerId("ogid_abcdef"))
        assertEquals("custom", OpenGolfIdentity.contributionPlayerId("custom"))
    }

    @Test
    fun subjectFromIdToken_readsSubClaim() {
        // {"sub":"ogid_abc","iat":1} base64url payload
        val payload = "eyJzdWIiOiJvZ2lkX2FiYyIsImlhdCI6MX0"
        val token = "hdr.$payload.sig"
        assertEquals("ogid_abc", OpenGolfIdentity.subjectFromIdToken(token))
    }
}

class OpenGolfPkceTest {
    @Test
    fun generate_producesUrlSafeVerifierAndChallenge() {
        val challenge = OpenGolfPkce.generate()
        assertTrue(challenge.verifier.isNotBlank())
        assertTrue(challenge.challenge.isNotBlank())
        assertTrue(!challenge.verifier.contains('+'))
        assertTrue(!challenge.challenge.contains('/'))
    }
}

class OpenGolfAuthCodeExtractionTest {
    @Test
    fun extractAuthCode_fromRedirectQuery() {
        val response =
            buildJsonObject {
                put("redirect", "https://api.opengolfapi.org/oauth/callback?code=abc123&state=x")
            }
        assertEquals("abc123", OpenGolfAuthStore.extractAuthCode(response))
    }

    @Test
    fun extractAuthCode_nullWhenMissing() {
        val response = buildJsonObject { put("raw", "no-code-here") }
        assertNull(OpenGolfAuthStore.extractAuthCode(response))
    }
}

class OpenGolfApiErrorBodyTest {
    @Test
    fun termsChallenge_parsesRequiredError() {
        val body =
            OpenGolfApiErrorBody(
                error = "terms_acceptance_required",
                terms = "https://example.com/terms",
                termsVersion = "2024-01",
            )
        val challenge = body.termsChallenge()
        assertEquals("2024-01", challenge?.version)
        assertEquals("https://example.com/terms", challenge?.termsUrl)
    }
}
