package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.GolfDataException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class OpenGolfApiClientTest {
    private val service = mockk<OpenGolfApiService>()
    private val client = OpenGolfApiClient(service)

    @Test
    fun searchCourses_mapsResults() =
        runTest {
            coEvery { service.searchCourses("pebble") } returns
                OpenGolfSearchResponse(
                    courses =
                        listOf(
                            OpenGolfCourseListItem(
                                id = "abc",
                                courseName = "Pebble Beach",
                                latitude = 36.0,
                                longitude = -121.0,
                            ),
                        ),
                )

            val results = client.searchCourses("pebble")

            assertEquals(1, results.size)
            assertEquals("Pebble Beach", results.first().name)
        }

    @Test(expected = GolfDataException::class)
    fun searchCourses_httpFailure_throwsInvalidResponse() =
        runTest {
            coEvery { service.searchCourses("fail") } throws httpException()

            client.searchCourses("fail")
        }

    @Test
    fun loadCourse_returnsSummaryAndScorecard() =
        runTest {
            coEvery { service.loadCourse("abc") } returns
                OpenGolfCourseDetail(
                    id = "abc",
                    courseName = "North",
                    latitude = 36.0,
                    longitude = -121.0,
                    scorecard = listOf(OpenGolfScorecardRow(holeNumber = 1, par = 4)),
                )

            val (summary, scorecard) = client.loadCourse("abc")

            assertEquals("abc", summary.id)
            assertEquals(1, scorecard.size)
            assertEquals(4, scorecard.first().par)
        }

    @Test(expected = GolfDataException::class)
    fun loadCourse_mismatchedId_throwsInvalidResponse() =
        runTest {
            coEvery { service.loadCourse("abc") } returns
                OpenGolfCourseDetail(
                    id = "different",
                    latitude = 36.0,
                    longitude = -121.0,
                )

            client.loadCourse("abc")
        }

    private fun httpException(): HttpException {
        @Suppress("DEPRECATION")
        return HttpException(Response.error<String>(500, okhttp3.ResponseBody.create(null, "")))
    }
}
