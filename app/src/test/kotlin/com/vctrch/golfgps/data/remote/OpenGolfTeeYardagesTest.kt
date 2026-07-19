package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.ScorecardHole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenGolfTeeYardagesTest {
    @Test
    fun preferredTeeKey_prefersWhiteWhenAvailable() {
        val tees =
            listOf(
                OpenGolfTeeSet(teeKey = "blue-male", teeName = "Blue", yardage = 6_500),
                OpenGolfTeeSet(teeKey = "white-male", teeName = "White", yardage = 6_100),
                OpenGolfTeeSet(teeKey = "red-male", teeName = "Red", yardage = 5_400),
            )

        assertEquals("white-male", OpenGolfTeeYardages.preferredTeeKey(tees))
    }

    @Test
    fun preferredTeeKey_returnsNullForEmptyList() {
        assertNull(OpenGolfTeeYardages.preferredTeeKey(emptyList()))
    }

    @Test
    fun yardageLookupKey_stripsGenderSuffix() {
        assertEquals("white", OpenGolfTeeYardages.yardageLookupKey("white-male"))
        assertEquals("blue", OpenGolfTeeYardages.yardageLookupKey("blue"))
    }

    @Test
    fun yardageByHole_mapsFromPreferredWhiteTeeKey() {
        val detail =
            OpenGolfCourseDetail(
                id = "salina",
                tees = listOf(OpenGolfTeeSet(teeKey = "white-male", teeName = "White", yardage = 6_115)),
                holesData =
                    listOf(
                        OpenGolfHole(
                            number = 1,
                            par = 4,
                            handicapIndex = 7,
                            yardages = mapOf("white" to 402, "blue" to 418),
                        ),
                        OpenGolfHole(
                            number = 8,
                            par = 3,
                            handicapIndex = 18,
                            yardages = mapOf("white" to 152, "blue" to 168),
                        ),
                    ),
            )

        val yardages = OpenGolfTeeYardages.yardageByHole(detail)

        assertEquals(402, yardages[1])
        assertEquals(152, yardages[8])
    }

    @Test
    fun mergeYardages_copiesPublishedYardagesOntoScorecard() {
        val scorecard =
            listOf(
                ScorecardHole(number = 1, par = 4, handicap = null),
                ScorecardHole(number = 2, par = 3, handicap = null),
            )

        val merged = OpenGolfTeeYardages.mergeYardages(scorecard, mapOf(1 to 402, 2 to 165))

        assertEquals(402, merged[0].yardage)
        assertEquals(165, merged[1].yardage)
    }

    @Test
    fun toScorecard_mergesPreferredTeeYardages() {
        val detail =
            OpenGolfCourseDetail(
                id = "abc",
                tees = listOf(OpenGolfTeeSet(teeKey = "white-male", teeName = "White", yardage = 6_000)),
                holesData =
                    listOf(
                        OpenGolfHole(number = 1, par = 4, yardages = mapOf("white" to 380)),
                        OpenGolfHole(number = 2, par = 5, yardages = mapOf("white" to 510)),
                    ),
            )

        val scorecard = detail.toScorecard()

        assertEquals(380, scorecard[0].yardage)
        assertEquals(510, scorecard[1].yardage)
    }
}
