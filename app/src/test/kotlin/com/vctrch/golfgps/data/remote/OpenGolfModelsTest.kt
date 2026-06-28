package com.vctrch.golfgps.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenGolfModelsTest {
    @Test
    fun toSummary_prefersCourseName() {
        val item =
            OpenGolfCourseListItem(
                id = "abc",
                clubName = "Club",
                courseName = "North Course",
                city = "Austin",
                state = "TX",
                latitude = 30.0,
                longitude = -97.0,
            )

        val summary = item.toSummary()

        assertEquals("abc", summary.id)
        assertEquals("North Course", summary.name)
        assertEquals("Austin", summary.city)
        assertEquals("TX", summary.state)
    }

    @Test
    fun toSummary_fallsBackToClubName() {
        val item =
            OpenGolfCourseListItem(
                id = "abc",
                clubName = "Pebble Beach",
                courseName = null,
                latitude = 36.0,
                longitude = -121.0,
            )

        assertEquals("Pebble Beach", item.toSummary().name)
    }

    @Test
    fun toSummary_usesGenericNameWhenMissing() {
        val item =
            OpenGolfCourseListItem(
                id = "abc",
                latitude = 36.0,
                longitude = -121.0,
            )

        assertEquals("Golf Course", item.toSummary().name)
    }

    @Test
    fun toScorecard_skipsRowsWithoutHoleNumber() {
        val detail =
            OpenGolfCourseDetail(
                id = "abc",
                lat = 36.0,
                lng = -121.0,
                holesData =
                    listOf(
                        OpenGolfHole(number = 2, par = 5),
                        OpenGolfHole(number = null, par = 4),
                        OpenGolfHole(number = 1, par = 4),
                    ),
            )

        val scorecard = detail.toScorecard()

        assertEquals(listOf(1, 2), scorecard.map { it.number })
        assertEquals(4, scorecard[0].par)
        assertEquals(5, scorecard[1].par)
    }
}
