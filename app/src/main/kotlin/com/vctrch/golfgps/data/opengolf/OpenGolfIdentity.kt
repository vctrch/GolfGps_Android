package com.vctrch.golfgps.data.opengolf

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object OpenGolfPkce {
    data class Challenge(
        val verifier: String,
        val challenge: String,
    )

    fun generate(): Challenge {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val verifier = base64UrlEncode(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return Challenge(verifier = verifier, challenge = base64UrlEncode(digest))
    }

    private fun base64UrlEncode(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)
}

object OpenGolfIdentity {
    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun deriveOpenGolfId(email: String): String {
        val normalized = normalizeEmail(email)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "ogid_" + hex.take(16)
    }

    /** Pseudonym write id — claimed ogid_ requires X-Play-Grant we don't mint. */
    fun contributionPlayerId(openGolfId: String): String {
        return if (openGolfId.startsWith("ogid_")) {
            "cg_" + openGolfId.removePrefix("ogid_")
        } else {
            openGolfId
        }
    }

    fun subjectFromIdToken(idToken: String): String? {
        val parts = idToken.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val json = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.getOrNull(1)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
