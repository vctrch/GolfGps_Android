package com.vctrch.golfgps.feature.auto

import com.vctrch.golfgps.domain.HoleTargetSource
import com.vctrch.golfgps.feature.auto.ActiveRoundSession.Companion.greenYardageLabel
import com.vctrch.golfgps.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRoundSessionTest {
    @Test
    fun yardageDetail_formatsLiveAndPendingStates() {
        assertEquals(
            "142 yds to pin",
            ActiveRoundSession.yardageDetail(142, holeMapped = true),
        )
        assertEquals(
            "142 yds to pin (est.)",
            ActiveRoundSession.yardageDetail(142, holeMapped = true, estimatedGreen = true),
        )
        assertEquals(
            "380 yds tee to pin",
            ActiveRoundSession.yardageDetail(null, holeMapped = true, teeToPinYards = 380),
        )
        assertEquals(
            "Waiting for GPS",
            ActiveRoundSession.yardageDetail(null, holeMapped = true),
        )
        assertEquals(
            "Green position pending",
            ActiveRoundSession.yardageDetail(null, holeMapped = false),
        )
    }

    @Test
    fun publish_exposesCurrentHoleAndYardage() {
        val session = ActiveRoundSession()
        val loaded = TestFixtures.loadedCourse()
        val hole =
            TestFixtures.holeTarget(
                number = 1,
                source = HoleTargetSource.OPEN_STREET_MAP,
                tee = TestFixtures.sampleTee,
                green = TestFixtures.offset(northYards = 150.0),
            )
        val course = loaded.copy(holes = listOf(hole, loaded.holes[1]))
        val onHole = TestFixtures.offset(northYards = 50.0)

        session.publish(course, selectedHoleNumber = 1, userLocation = onHole)

        val snapshot = session.snapshot.value
        assertTrue(snapshot.isRoundReady)
        assertEquals(course.summary.name, snapshot.courseName)
        assertEquals(1, snapshot.selectedHoleNumber)
        assertEquals(onHole, snapshot.userLocation)
        assertTrue((snapshot.yardsToGreen ?: -1) >= 0)
        assertEquals(hole.holeLengthYards(), snapshot.teeToPinYards)
    }

    @Test
    fun publish_omitsGreenYardageWhenUnreliable() {
        val session = ActiveRoundSession()
        val fallback =
            TestFixtures.holeTarget(
                number = 1,
                source = HoleTargetSource.SCORECARD_FALLBACK,
                tee = TestFixtures.sampleTee,
                green = TestFixtures.offset(northYards = 150.0),
            )
        val course = TestFixtures.loadedCourse().copy(holes = listOf(fallback))
        val onHole = TestFixtures.offset(northYards = 50.0)

        session.publish(course, selectedHoleNumber = 1, userLocation = onHole)

        assertEquals(null, session.snapshot.value.yardsToGreen)
        assertEquals(fallback.holeLengthYards(), session.snapshot.value.teeToPinYards)
    }

    @Test
    fun publish_exposesTeeToPinWhenGpsIsMissing() {
        val session = ActiveRoundSession()
        val hole =
            TestFixtures.holeTarget(
                number = 1,
                source = HoleTargetSource.OPEN_STREET_MAP,
                tee = TestFixtures.sampleTee,
                green = TestFixtures.offset(northYards = 150.0),
            )
        val course = TestFixtures.loadedCourse().copy(holes = listOf(hole))

        session.publish(course, selectedHoleNumber = 1, userLocation = null)

        assertEquals(null, session.snapshot.value.yardsToGreen)
        assertEquals(hole.holeLengthYards(), session.snapshot.value.teeToPinYards)
        assertEquals(
            "${hole.holeLengthYards()} yds tee to pin",
            session.snapshot.value.greenYardageLabel(),
        )
    }

    @Test
    fun holeActions_invokeBoundCallbacks() {
        val session = ActiveRoundSession()
        var previous = 0
        var next = 0
        session.bindHoleActions(
            onPrevious = { previous += 1 },
            onNext = { next += 1 },
        )

        session.previousHole()
        session.nextHole()

        assertEquals(1, previous)
        assertEquals(1, next)

        session.unbindHoleActions()
        session.previousHole()
        session.nextHole()
        assertEquals(1, previous)
        assertEquals(1, next)
    }

    @Test
    fun clearRound_keepsAutoConnectionFlag() {
        val session = ActiveRoundSession()
        session.setAutoConnected(true)
        session.publish(TestFixtures.loadedCourse(), 1, null)
        session.clearRound()

        assertTrue(session.snapshot.value.isAutoConnected)
        assertFalse(session.snapshot.value.isRoundReady)
    }
}
