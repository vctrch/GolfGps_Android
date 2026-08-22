package com.vctrch.golfgps.data.opengolf

import com.vctrch.golfgps.domain.GolfDataError
import com.vctrch.golfgps.domain.GolfDataException
import com.vctrch.golfgps.testing.TestFixtures
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenGolfContributeClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val config = TestFixtures.openGolfConfig()
    private val termsStore =
        mockk<OpenGolfTermsStore> {
            coEvery { acceptedVersionSnapshot() } returns null
        }

    private fun client(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): OpenGolfContributeClient {
        val http =
            HttpClient(MockEngine(handler)) {
                expectSuccess = false
            }
        return OpenGolfContributeClient(json, termsStore, config, http)
    }

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private fun sampleSubmission(
        type: OpenGolfMomentType = OpenGolfMomentType.TEE,
        note: String? = "front left",
        sessionId: String? = null,
        strokes: Int? = null,
        accuracyMeters: Double? = 4.0,
    ) = OpenGolfMomentSubmission(
        momentType = type,
        latitude = 36.568,
        longitude = -121.949,
        accuracyMeters = accuracyMeters,
        courseId = "course-1",
        hole = 7,
        playerId = "ogid_abcdef",
        note = note,
        dedupKey = "course-1-7-tee-1",
        sessionId = sessionId,
        strokes = strokes,
    )

    @Test
    fun successMessage_matchesMomentType() {
        val contribute = client { json("""{"ok":true}""") }
        assertEquals("Tee saved for hole 3.", contribute.successMessage(OpenGolfMomentType.TEE, 3))
        assertEquals("Green saved for hole 3.", contribute.successMessage(OpenGolfMomentType.GREEN, 3))
        assertEquals("Pin saved for hole 3.", contribute.successMessage(OpenGolfMomentType.PIN, 3))
        assertEquals("Note saved for hole 3.", contribute.successMessage(OpenGolfMomentType.MESSAGE, 3))
        assertEquals("Score saved for hole 3.", contribute.successMessage(OpenGolfMomentType.SCORE, 3))
        assertEquals("Breadcrumb saved for hole 3.", contribute.successMessage(OpenGolfMomentType.BREADCRUMB, 3))
    }

    @Test
    fun submitMoment_postsMappedBodyAndHeaders() =
        runTest {
            var captured: HttpRequestData? = null
            val contribute =
                client { request ->
                    captured = request
                    json("""{"ok":true,"ingested":1,"ids":["m1"]}""")
                }

            val result =
                contribute.submitMoment(
                    submission = sampleSubmission(),
                    appApiKey = "test-key",
                    accessToken = "access-token",
                )

            assertEquals(true, result.ok)
            assertEquals(1, result.ingested)
            val request = requireNotNull(captured)
            assertEquals("/api/v1/moments", request.url.encodedPath)
            assertEquals("test-key", request.headers["X-API-Key"])
            assertEquals("access-token", request.headers["X-OpenGolf-Token"])
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
            val raw = request.bodyText()
            assertTrue(raw.contains("\"moment_type\":\"tee\""))
            assertTrue(raw.contains("\"player_id\":\"cg_abcdef\""))
            assertTrue(raw.contains("\"course_id\":\"course-1\""))
            assertTrue(raw.contains("\"hole\":7"))
            assertTrue(raw.contains("\"note\":\"front left\""))
            assertTrue(raw.contains("\"accuracy_m\":4"))
            assertTrue(raw.contains("\"consent_scope\":\"contribute\""))
            assertFalse(raw.contains("session_id"))
        }

    @Test
    fun submitMoment_includesSessionIdWhenSet() =
        runTest {
            var rawBody = ""
            val contribute =
                client { request ->
                    rawBody = request.bodyText()
                    json("""{"ok":true}""")
                }

            contribute.submitMoment(
                submission = sampleSubmission(sessionId = "sess-1"),
                appApiKey = "k",
                accessToken = "token",
            )

            assertTrue(rawBody.contains("\"session_id\":\"sess-1\""))
        }

    @Test
    fun submitMoment_omitsSessionIdWhenNull() =
        runTest {
            var rawBody = ""
            val contribute =
                client { request ->
                    rawBody = request.bodyText()
                    json("""{"ok":true}""")
                }

            contribute.submitMoment(
                submission = sampleSubmission(sessionId = null),
                appApiKey = "k",
                accessToken = "token",
            )

            assertFalse(rawBody.contains("session_id"))
        }

    @Test
    fun submitMoment_scoreSendsStrokesPayloadWithoutNote() =
        runTest {
            var rawBody = ""
            val contribute =
                client { request ->
                    rawBody = request.bodyText()
                    json("""{"ok":true}""")
                }

            contribute.submitMoment(
                submission =
                    sampleSubmission(
                        type = OpenGolfMomentType.SCORE,
                        note = "great putt",
                        strokes = 4,
                    ),
                appApiKey = "k",
                accessToken = "token",
            )

            val payload = json.parseToJsonElement(rawBody).jsonObject["payload"]!!.jsonObject
            assertEquals(4, payload["strokes"]!!.jsonPrimitive.content.toInt())
            assertEquals(7, payload["hole"]!!.jsonPrimitive.content.toInt())
            assertFalse(payload.containsKey("note"))
            assertFalse(payload.containsKey("text"))
            assertFalse(payload.containsKey("category"))
        }

    @Test
    fun submitMoment_messageSendsBodyPayload() =
        runTest {
            var rawBody = ""
            val contribute =
                client { request ->
                    rawBody = request.bodyText()
                    json("""{"ok":true}""")
                }

            contribute.submitMoment(
                submission =
                    sampleSubmission(
                        type = OpenGolfMomentType.MESSAGE,
                        note = "watch left bunker",
                    ),
                appApiKey = "k",
                accessToken = "token",
            )

            val payload = json.parseToJsonElement(rawBody).jsonObject["payload"]!!.jsonObject
            assertEquals("watch left bunker", payload["body"]!!.jsonPrimitive.content)
            assertFalse(payload.containsKey("note"))
            assertFalse(payload.containsKey("text"))
        }

    @Test
    fun submitMoment_blankAccessToken_throwsAuthenticationFailed() =
        runTest {
            val contribute = client { json("""{"ok":true}""") }
            try {
                contribute.submitMoment(sampleSubmission(), "k", null)
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.AUTHENTICATION_FAILED, e.error)
                assertTrue(e.message!!.contains("Sign in with OpenGolf", ignoreCase = false))
            }
        }

    @Test
    fun submitMoment_blankApiKey_throwsContributionFailed() =
        runTest {
            val contribute = client { json("""{"ok":true}""") }
            try {
                contribute.submitMoment(sampleSubmission(), "  ", "token")
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.CONTRIBUTION_FAILED, e.error)
                assertTrue(e.message!!.contains("try again later", ignoreCase = true))
            }
        }

    @Test
    fun submitMoment_emptyResponse_throwsContributionFailed() =
        runTest {
            val contribute = client { json("") }
            try {
                contribute.submitMoment(sampleSubmission(), "k", "token")
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.CONTRIBUTION_FAILED, e.error)
                assertTrue(e.message!!.contains("empty", ignoreCase = true))
            }
        }

    @Test
    fun submitMoment_okFalse_throwsContributionFailed() =
        runTest {
            val contribute = client { json("""{"ok":false}""") }
            try {
                contribute.submitMoment(sampleSubmission(), "k", "token")
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.CONTRIBUTION_FAILED, e.error)
                assertTrue(e.message!!.contains("did not accept", ignoreCase = true))
                assertTrue(e.message!!.contains("Moment"))
                assertFalse(e.message!!.contains("map update", ignoreCase = true))
            }
        }

    @Test
    fun submitMoment_termsChallenge_throwsTermsAcceptanceRequired() =
        runTest {
            val contribute =
                client {
                    json(
                        """
                        {"error":"terms_acceptance_required","terms_version":"2024-01",
                         "terms":"https://example.com/terms"}
                        """.trimIndent(),
                        status = HttpStatusCode.Forbidden,
                    )
                }

            try {
                contribute.submitMoment(sampleSubmission(), "k", "token")
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.TERMS_ACCEPTANCE_REQUIRED, e.error)
                assertEquals("2024-01", e.termsVersion)
                assertEquals("https://example.com/terms", e.termsUrl)
            }
        }

    @Test
    fun submitMoment_sendsAcceptedTermsHeader() =
        runTest {
            coEvery { termsStore.acceptedVersionSnapshot() } returns "2024-01"
            var sawTermsHeader: String? = null
            val contribute =
                client { request ->
                    sawTermsHeader = request.headers["X-Accept-Terms"]
                    json("""{"ok":true}""")
                }

            contribute.submitMoment(sampleSubmission(), "k", "token")

            assertEquals("2024-01", sawTermsHeader)
        }

    @Test
    fun submitCorrection_returnsDecodedResult() =
        runTest {
            val contribute =
                client { request ->
                    assertEquals("/api/v1/corrections", request.url.encodedPath)
                    json("""{"correction_id":"c1","status":"queued"}""")
                }

            val result =
                contribute.submitCorrection(
                    submission =
                        OpenGolfCorrectionSubmission(
                            courseId = "course-1",
                            field = OpenGolfCorrectionField.CITY,
                            proposedValue = "Monterey",
                            note = null,
                        ),
                    appApiKey = "k",
                    accessToken = "t",
                )

            assertEquals("c1", result.correctionId)
            assertEquals("queued", result.status)
        }

    @Test
    fun submitCorrection_errorField_throwsContributionFailed() =
        runTest {
            val contribute = client { json("""{"error":"invalid field"}""") }
            try {
                contribute.submitCorrection(
                    OpenGolfCorrectionSubmission(
                        courseId = "course-1",
                        field = OpenGolfCorrectionField.PHONE,
                        proposedValue = "555",
                        note = null,
                    ),
                    "k",
                    null,
                )
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.CONTRIBUTION_FAILED, e.error)
                assertEquals("invalid field", e.message)
            }
        }
}
