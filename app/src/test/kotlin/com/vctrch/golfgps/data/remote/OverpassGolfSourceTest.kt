package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassGolfSourceTest {
    private fun source(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String,
    ): OverpassGolfSource {
        val client =
            HttpClient(
                MockEngine {
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(HttpTimeout)
            }
        return OverpassGolfSource(client, endpoints = listOf("https://overpass.test/api/interpreter"))
    }

    @Test
    fun loadHoleTargets_parsesCourseAreaGeometry() =
        runTest {
            val source =
                source(
                    body =
                        """
                        {"elements":[
                          {"type":"way","id":1,"tags":{"golf":"hole","ref":"1","par":"4"},
                           "geometry":[{"lat":36.0,"lon":-121.0},{"lat":36.0013,"lon":-121.0}]}
                        ]}
                        """.trimIndent(),
                )

            val holes =
                source.loadHoleTargets(
                    courseCenter = LatLng(36.0, -121.0),
                    osmCourseId = 123L,
                    courseName = "Test Course",
                    scorecard = listOf(ScorecardHole(1, 4, null)),
                )

            assertEquals(1, holes.size)
            assertEquals(1, holes.first().number)
            assertEquals(4, holes.first().par)
            assertEquals(HoleTargetSource.OPEN_STREET_MAP, holes.first().source)
        }

    @Test
    fun loadHoleTargets_returnsEmptyOnHttpFailure() =
        runTest {
            val source = source(status = HttpStatusCode.BadGateway, body = "")

            val holes =
                source.loadHoleTargets(
                    courseCenter = LatLng(36.0, -121.0),
                    osmCourseId = 123L,
                    courseName = "Test Course",
                    scorecard = listOf(ScorecardHole(1, 4, null)),
                )

            assertTrue(holes.isEmpty())
        }
}
