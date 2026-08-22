package com.vctrch.golfgps.data.opengolf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGolfContributionFeaturesTest {
    @Test
    fun featureGates_matchIosDefaults() {
        assertTrue(OpenGolfContributionFeatures.playerMomentsEnabled)
        assertFalse(OpenGolfContributionFeatures.courseCorrectionsEnabled)
    }
}
