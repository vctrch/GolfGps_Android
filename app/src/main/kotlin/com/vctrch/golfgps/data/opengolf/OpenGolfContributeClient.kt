package com.vctrch.golfgps.data.opengolf

import com.vctrch.golfgps.di.OpenGolfWriteHttpClient
import com.vctrch.golfgps.domain.GolfDataException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
            requireMomentCredentials(appApiKey, accessToken)
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
                    submission.sessionId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("session_id", it) }
                    submission.accuracyMeters?.let { put("accuracy_m", it) }
                    putJsonObject("payload") {
                        put("hole", submission.hole)
                        when (submission.momentType) {
                            OpenGolfMomentType.SCORE -> {
                                put("strokes", scoreStrokesOrThrow(submission))
                            }
                            OpenGolfMomentType.MESSAGE -> {
                                submission.note?.trim()?.takeIf { it.isNotEmpty() }?.let { put("body", it) }
                            }
                            else -> {
                                submission.note?.trim()?.takeIf { it.isNotEmpty() }?.let { put("note", it) }
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
        ): String = "${type.label} saved for hole $holeNumber."

        private fun requireMomentCredentials(
            appApiKey: String,
            accessToken: String?,
        ) {
            if (appApiKey.isBlank()) {
                throw GolfDataException.contributionFailed("Something went wrong. Please try again later.")
            }
            if (accessToken.isNullOrBlank()) {
                throw GolfDataException.authenticationFailed("Sign in with OpenGolf to use Moments.")
            }
        }

        private fun scoreStrokesOrThrow(submission: OpenGolfMomentSubmission): Int {
            submission.strokes?.takeIf { it in 1..30 }?.let { return it }
            return submission.note?.trim()?.toIntOrNull()?.takeIf { it in 1..30 }
                ?: throw GolfDataException.contributionFailed("Score needs strokes (1–30).")
        }

        /**
         * Read the signed-in golfer's own Moments (`GET /api/v1/moments`).
         * Same split as POST: server `appApiKey` + user `accessToken`.
         */
        suspend fun listMoments(
            query: OpenGolfMomentsQuery,
            appApiKey: String,
            accessToken: String,
        ): OpenGolfMomentsListResponse {
            requireMomentsCredentials(appApiKey, accessToken)
            val outgoing =
                query.copy(
                    player = query.player?.let { OpenGolfIdentity.contributionPlayerId(it) },
                )
            val data = get("api/v1/moments", outgoing.queryItems(), appApiKey, accessToken)
            if (data.isBlank()) {
                throw GolfDataException.contributionFailed("OpenGolf returned an empty response.")
            }
            return runCatching { OpenGolfMomentsListResponse.decode(json, data) }.getOrElse {
                throw GolfDataException.contributionFailed("Could not read OpenGolf's moments response.")
            }
        }

        private fun requireMomentsCredentials(
            appApiKey: String,
            accessToken: String,
        ) {
            if (appApiKey.trim().isEmpty()) {
                throw GolfDataException.contributionFailed("Something went wrong. Please try again later.")
            }
            if (accessToken.trim().isEmpty()) {
                throw GolfDataException.authenticationFailed("Sign in with OpenGolf to use Moments.")
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
                throw GolfDataException.contributionFailed("OpenGolf did not accept this Moment.")
            }
            return decoded
        }

        private suspend fun post(
            path: String,
            body: JsonObject,
            appApiKey: String,
            accessToken: String?,
        ): String {
            val acceptedTerms = termsStore.acceptedVersionSnapshot()
            val response =
                client.post("${config.apiBaseUrl}/$path") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Accept, "application/json")
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

        private suspend fun get(
            path: String,
            queryParams: List<Pair<String, String>>,
            appApiKey: String,
            accessToken: String,
        ): String {
            val acceptedTerms = termsStore.acceptedVersionSnapshot()
            val response =
                client.get("${config.apiBaseUrl}/$path") {
                    header(HttpHeaders.Accept, "application/json")
                    header("X-API-Key", appApiKey)
                    if (accessToken.isNotBlank()) {
                        header("X-OpenGolf-Token", accessToken)
                    }
                    if (!acceptedTerms.isNullOrBlank()) {
                        header("X-Accept-Terms", acceptedTerms)
                    }
                    queryParams.forEach { (name, value) -> parameter(name, value) }
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
