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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenGolfMomentsListClientTest {
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

    @Test
    fun listMoments_sendsGetWithAuthHeadersAndQueryParams() =
        runTest {
            var captured: HttpRequestData? = null
            val contribute =
                client { request ->
                    captured = request
                    json("""{"moments":[],"next_cursor":null}""")
                }

            contribute.listMoments(
                query =
                    OpenGolfMomentsQuery(
                        player = "ogid_player1",
                        session = "round-42",
                        type = "score",
                        limit = 25,
                        cursor = "cur_abc",
                    ),
                appApiKey = "ogapi_test_key",
                accessToken = "og_access_token",
            )

            val request = requireNotNull(captured)
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/moments", request.url.encodedPath)
            assertEquals("ogapi_test_key", request.headers["X-API-Key"])
            assertEquals("og_access_token", request.headers["X-OpenGolf-Token"])
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
            assertEquals("cg_player1", request.url.parameters["player"])
            assertEquals("round-42", request.url.parameters["session"])
            assertEquals("score", request.url.parameters["type"])
            assertEquals("25", request.url.parameters["limit"])
            assertEquals("cur_abc", request.url.parameters["cursor"])
        }

    @Test
    fun listMoments_omitsEmptyPlayerSessionAndCursor() =
        runTest {
            var captured: HttpRequestData? = null
            val contribute =
                client { request ->
                    captured = request
                    json("""{"moments":[]}""")
                }

            contribute.listMoments(
                query =
                    OpenGolfMomentsQuery(
                        player = "",
                        session = "  ",
                        type = "pin",
                        cursor = "",
                    ),
                appApiKey = "k",
                accessToken = "token",
            )

            val params = requireNotNull(captured).url.parameters
            assertNull(params["player"])
            assertNull(params["session"])
            assertNull(params["cursor"])
            assertEquals("pin", params["type"])
        }

    @Test
    fun listMoments_blankToken_throwsAuthenticationFailed() =
        runTest {
            val contribute = client { json("""{"moments":[]}""") }
            try {
                contribute.listMoments(
                    query = OpenGolfMomentsQuery(player = "ogid_test"),
                    appApiKey = "ogapi_test",
                    accessToken = "",
                )
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.AUTHENTICATION_FAILED, e.error)
                assertEquals("Sign in with OpenGolf to use Moments.", e.message)
            }
        }

    @Test
    fun listMoments_blankApiKey_throwsContributionFailed() =
        runTest {
            val contribute = client { json("""{"moments":[]}""") }
            try {
                contribute.listMoments(
                    query = OpenGolfMomentsQuery(player = "ogid_test"),
                    appApiKey = "  ",
                    accessToken = "token",
                )
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.CONTRIBUTION_FAILED, e.error)
                assertEquals("Something went wrong. Please try again later.", e.message)
            }
        }

    @Test
    fun listMoments_decodesMomentsEnvelopeWithPayload() =
        runTest {
            val contribute =
                client {
                    json(
                        """
                        {
                          "moments": [
                            {
                              "id": "m_1",
                              "moment_type": "score",
                              "lat": 38.8,
                              "lng": -97.5,
                              "accuracy_m": 4.5,
                              "recorded_at": "2026-07-27T18:00:00Z",
                              "course_id": "course-1",
                              "hole": 7,
                              "player_id": "ogid_player1",
                              "session_id": "round-42",
                              "dedup_key": "round-42:7:score",
                              "consent_scope": "contribute",
                              "payload": { "strokes": 4, "note": "solid" }
                            }
                          ],
                          "next_cursor": "cur_next"
                        }
                        """.trimIndent(),
                    )
                }

            val result =
                contribute.listMoments(
                    query = OpenGolfMomentsQuery(player = "ogid_player1"),
                    appApiKey = "ogapi_test",
                    accessToken = "token",
                )

            assertEquals("cur_next", result.nextCursor)
            assertEquals(1, result.moments.size)
            val moment = result.moments.first()
            assertEquals("m_1", moment.serverId)
            assertEquals("score", moment.momentType)
            assertEquals(38.8, moment.latitude)
            assertEquals(-97.5, moment.longitude)
            assertEquals(4.5, moment.accuracyMeters)
            assertEquals("2026-07-27T18:00:00Z", moment.recordedAt)
            assertEquals("course-1", moment.courseId)
            assertEquals(7, moment.hole)
            assertEquals("ogid_player1", moment.playerId)
            assertEquals("round-42", moment.sessionId)
            assertEquals("round-42:7:score", moment.dedupKey)
            assertEquals(4, moment.payload?.get("strokes")?.jsonPrimitive?.intOrNull)
            assertEquals("solid", moment.payload?.get("note")?.jsonPrimitive?.content)
            assertEquals(4, moment.displayStrokes)
            assertEquals("solid", moment.displayNote)
            assertEquals("m_1", moment.id)
        }

    @Test
    fun listMoments_decodesBareJsonArray() =
        runTest {
            val contribute =
                client {
                    json(
                        """
                        [
                          {
                            "moment_type": "breadcrumb",
                            "lat": 1.0,
                            "lng": 2.0,
                            "player_id": "ogid_p1",
                            "session_id": "s1"
                          }
                        ]
                        """.trimIndent(),
                    )
                }

            val result =
                contribute.listMoments(
                    query = OpenGolfMomentsQuery(session = "s1"),
                    appApiKey = "ogapi_test",
                    accessToken = "token",
                )

            assertNull(result.nextCursor)
            assertEquals(1, result.moments.size)
            assertEquals("breadcrumb", result.moments[0].momentType)
            assertNull(result.moments[0].serverId)
        }

    @Test
    fun listMoments_decodesDataArrayAlias() =
        runTest {
            val contribute =
                client {
                    json("""{"data":[{"moment_type":"swing","hole":2}],"next_cursor":"n1"}""")
                }

            val result =
                contribute.listMoments(
                    query = OpenGolfMomentsQuery(),
                    appApiKey = "k",
                    accessToken = "token",
                )

            assertEquals("n1", result.nextCursor)
            assertEquals("swing", result.moments.single().momentType)
            assertEquals(2, result.moments.single().hole)
        }

    @Test
    fun listMoments_empty2xxBody_throwsContributionFailed() =
        runTest {
            val contribute = client { json("") }
            try {
                contribute.listMoments(
                    query = OpenGolfMomentsQuery(player = "ogid_p1"),
                    appApiKey = "ogapi_test",
                    accessToken = "token",
                )
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.CONTRIBUTION_FAILED, e.error)
                assertTrue(e.message!!.contains("empty", ignoreCase = true))
            }
        }

    @Test
    fun listMoments_http401WithErrorBody_throwsContributionFailed() =
        runTest {
            val contribute =
                client {
                    json("""{"error":"API key required"}""", status = HttpStatusCode.Unauthorized)
                }
            try {
                contribute.listMoments(
                    query = OpenGolfMomentsQuery(player = "ogid_p1"),
                    appApiKey = "ogapi_bad",
                    accessToken = "token",
                )
                fail("expected GolfDataException")
            } catch (e: GolfDataException) {
                assertEquals(GolfDataError.CONTRIBUTION_FAILED, e.error)
                assertEquals("API key required", e.message)
            }
        }

    @Test
    fun momentReader_forwardsToContributeClient() =
        runTest {
            var captured: HttpRequestData? = null
            val contribute =
                client { request ->
                    captured = request
                    json("""{"moments":[{"moment_type":"pin","hole":3}],"next_cursor":null}""")
                }

            val result =
                OpenGolfMomentReader(contribute).list(
                    player = "ogid_player1",
                    type = "pin",
                    limit = 10,
                    appApiKey = "ogapi_test",
                    accessToken = "token",
                )

            val request = requireNotNull(captured)
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/moments", request.url.encodedPath)
            assertEquals("cg_player1", request.url.parameters["player"])
            assertEquals("pin", request.url.parameters["type"])
            assertEquals("10", request.url.parameters["limit"])
            assertEquals(1, result.moments.size)
            assertEquals("pin", result.moments[0].momentType)
            assertEquals(3, result.moments[0].hole)
        }
}
