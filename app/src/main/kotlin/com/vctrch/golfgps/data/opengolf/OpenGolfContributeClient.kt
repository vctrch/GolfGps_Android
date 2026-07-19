package com.vctrch.golfgps.data.opengolf

import com.vctrch.golfgps.BuildConfig
import com.vctrch.golfgps.domain.GolfDataException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
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
    ) {
        private val client: HttpClient =
            HttpClient(OkHttp) {
                expectSuccess = false
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    requestTimeoutMillis = 30_000
                    socketTimeoutMillis = 30_000
                }
            }
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
            val data = post("api/v1/moments", body, appApiKey, accessToken)
            if (data.isBlank()) {
                throw GolfDataException.contributionFailed("OpenGolf returned an empty response.")
            }
            val decoded =
                runCatching { json.decodeFromString<OpenGolfIngestResult>(data) }.getOrElse {
                    throw GolfDataException.contributionFailed("Could not read OpenGolf's response.")
                }
            if (decoded.ok == false) {
                throw GolfDataException.contributionFailed("OpenGolf did not accept this map update.")
            }
            return decoded
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

        suspend fun submitMomentRequest(
            type: OpenGolfMomentType,
            latitude: Double,
            longitude: Double,
            accuracyMeters: Double?,
            courseId: String,
            holeNumber: Int,
            playerId: String,
            note: String?,
            appApiKey: String,
            accessToken: String?,
        ) {
            val submission =
                OpenGolfMomentSubmission(
                    momentType = type,
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = accuracyMeters,
                    courseId = courseId,
                    hole = holeNumber,
                    playerId = playerId,
                    note = note,
                    dedupKey = "$courseId-$holeNumber-${type.rawValue}-${Instant.now().epochSecond}",
                )
            submitMoment(submission, appApiKey, accessToken)
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

        private suspend fun post(
            path: String,
            body: kotlinx.serialization.json.JsonObject,
            appApiKey: String,
            accessToken: String?,
        ): String {
            val base = BuildConfig.OPENGOLF_BASE_URL.trimEnd('/')
            val acceptedTerms = termsStore.acceptedVersionSnapshot()
            val response =
                client.post("$base/$path") {
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
