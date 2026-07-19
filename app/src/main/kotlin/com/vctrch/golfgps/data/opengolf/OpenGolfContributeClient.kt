package com.vctrch.golfgps.data.opengolf

import com.vctrch.golfgps.di.OpenGolfWriteHttpClient
import com.vctrch.golfgps.domain.GolfDataException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenGolfContributeClient
    @Inject
    constructor(
        private val json: Json,
        private val termsStore: OpenGolfTermsStore,
        private val config: OpenGolfConfig,
        @OpenGolfWriteHttpClient private val client: HttpClient,
    ) {
        suspend fun submitMoment(
            submission: OpenGolfMomentSubmission,
            appApiKey: String,
            accessToken: String?,
        ): OpenGolfIngestResult {
            val body =
                buildJsonObject {
                    put("moment_type", submission.momentType.rawValue)
                    put("lat", submission.latitude)
                    put("lng", submission.longitude)
                    put("course_id", submission.courseId)
                    put("hole", submission.hole)
                    put("player_id", OpenGolfIdentity.contributionPlayerId(submission.playerId))
                    put("dedup_key", submission.dedupKey)
                    put("recorded_at", Instant.now().toString())
                    put("consent_scope", "contribute")
                    submission.accuracyMeters?.let { put("accuracy_m", it) }
                    putJsonObject("payload") {
                        put("hole", submission.hole)
                        val note = submission.note?.trim().orEmpty()
                        if (note.isNotEmpty()) {
                            when (submission.momentType) {
                                OpenGolfMomentType.BETA -> {
                                    put("text", note)
                                    put("category", "general")
                                }
                                else -> put("note", note)
                            }
                        }
                    }
                }
            return decodeMomentResult(post("api/v1/moments", body, appApiKey, accessToken))
        }

        suspend fun submitCorrection(
            submission: OpenGolfCorrectionSubmission,
            appApiKey: String,
            accessToken: String?,
        ): OpenGolfCorrectionResult {
            val body =
                buildJsonObject {
                    put("course_id", submission.courseId)
                    put("field", submission.field.rawValue)
                    put("proposed_value", submission.proposedValue)
                    submission.note?.trim()?.takeIf { it.isNotEmpty() }?.let { put("note", it) }
                }
            val data = post("api/v1/corrections", body, appApiKey, accessToken)
            val decoded =
                runCatching { json.decodeFromString<OpenGolfCorrectionResult>(data) }.getOrNull()
            if (decoded?.error?.isNotBlank() == true) {
                throw GolfDataException.contributionFailed(decoded.error)
            }
            return decoded ?: OpenGolfCorrectionResult(status = "submitted")
        }

        fun successMessage(
            type: OpenGolfMomentType,
            holeNumber: Int,
        ): String {
            return when (type) {
                OpenGolfMomentType.TEE -> "Tee location submitted for hole $holeNumber."
                OpenGolfMomentType.GREEN -> "Green location submitted for hole $holeNumber."
                OpenGolfMomentType.PIN -> "Pin location submitted for hole $holeNumber."
                OpenGolfMomentType.BETA -> "Local tip submitted for hole $holeNumber."
            }
        }

        private fun decodeMomentResult(data: String): OpenGolfIngestResult {
            val decoded =
                data.takeIf { it.isNotBlank() }
                    ?.let { runCatching { json.decodeFromString<OpenGolfIngestResult>(it) }.getOrNull() }
            if (decoded == null) {
                throw GolfDataException.contributionFailed(
                    if (data.isBlank()) {
                        "OpenGolf returned an empty response."
                    } else {
                        "Could not read OpenGolf's response."
                    },
                )
            }
            if (decoded.ok == false) {
                throw GolfDataException.contributionFailed("OpenGolf did not accept this map update.")
            }
            return decoded
        }

        private suspend fun post(
            path: String,
            body: kotlinx.serialization.json.JsonObject,
            appApiKey: String,
            accessToken: String?,
        ): String {
            val acceptedTerms = termsStore.acceptedVersionSnapshot()
            val response =
                client.post("${config.apiBaseUrl}/$path") {
                    contentType(ContentType.Application.Json)
                    header("X-API-Key", appApiKey)
                    if (!accessToken.isNullOrBlank()) {
                        header("X-OpenGolf-Token", accessToken)
                    }
                    if (!acceptedTerms.isNullOrBlank()) {
                        header("X-Accept-Terms", acceptedTerms)
                    }
                    setBody(body.toString())
                }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                val apiError = runCatching { json.decodeFromString<OpenGolfApiErrorBody>(text) }.getOrNull()
                apiError?.termsChallenge()?.let { challenge ->
                    throw GolfDataException.termsAcceptanceRequired(challenge.version, challenge.termsUrl)
                }
                throw GolfDataException.contributionFailed(
                    apiError?.error?.takeIf { it.isNotBlank() }
                        ?: "Request failed (${response.status.value})",
                )
            }
            return text
        }
    }
