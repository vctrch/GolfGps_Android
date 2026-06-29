package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.GolfDataException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class KtorOpenGolfApiTest {
    private val baseUrl = "https://api.opengolfapi.org/"

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorOpenGolfApi {
        val client =
            HttpClient(MockEngine(handler)) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return KtorOpenGolfApi(client, baseUrl)
    }

    private fun MockRequestHandleScope.json(body: String) =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    @Test
    fun searchCourses_mapsResults() =
        runTest {
            val body =
                """
                {"courses":[
                  {"id":"abc","course_name":"Pebble Beach","latitude":36.0,"longitude":-121.0}
                ]}
                """.trimIndent()
            val api =
                api { request ->
                    assertEquals("/v1/courses/search", request.url.encodedPath)
                    assertEquals("pebble", request.url.parameters["q"])
                    json(body)
                }

            val results = api.searchCourses("pebble")

            assertEquals(1, results.size)
            assertEquals("Pebble Beach", results.first().name)
        }

    @Test(expected = GolfDataException::class)
    fun searchCourses_httpFailure_throwsInvalidResponse() =
        runTest {
            val api = api { respond("", HttpStatusCode.InternalServerError) }

            api.searchCourses("fail")
        }

    @Test
    fun loadCourse_returnsSummaryAndScorecard() =
        runTest {
            val body =
                """
                {"id":"abc","course_name":"North","lat":36.0,"lng":-121.0,
                 "holes_data":[{"number":1,"par":4}]}
                """.trimIndent()
            val api =
                api { request ->
                    assertEquals("/api/v1/courses/abc", request.url.encodedPath)
                    json(body)
                }

            val (summary, scorecard) = api.loadCourse("abc")

            assertEquals("abc", summary.id)
            assertEquals(1, scorecard.size)
            assertEquals(4, scorecard.first().par)
        }

    @Test(expected = GolfDataException::class)
    fun loadCourse_mismatchedId_throwsInvalidResponse() =
        runTest {
            val api = api { json("""{"id":"different","lat":36.0,"lng":-121.0}""") }

            api.loadCourse("abc")
        }
}
