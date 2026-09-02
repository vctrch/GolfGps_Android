package com.vctrch.golfgps.feature.map

import com.vctrch.golfgps.domain.GreenMappingConfidence
import com.vctrch.golfgps.domain.TeeMappingConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoleMapMarkerPaletteTest {
    @Test
    fun fillsAreFullyOpaque() {
        GreenMappingConfidence.entries.forEach { confidence ->
            assertTrue(
                "green fill for $confidence",
                HoleMapMarkerPalette.isFullyOpaque(HoleMapMarkerPalette.greenFill(confidence)),
            )
        }
        TeeMappingConfidence.entries.forEach { confidence ->
            assertTrue(
                "tee fill for $confidence",
                HoleMapMarkerPalette.isFullyOpaque(HoleMapMarkerPalette.teeFill(confidence)),
            )
        }
        assertTrue(HoleMapMarkerPalette.isFullyOpaque(HoleMapMarkerPalette.PLAYER_FILL))
        assertTrue(HoleMapMarkerPalette.isFullyOpaque(HoleMapMarkerPalette.STROKE))
        assertTrue(HoleMapMarkerPalette.isFullyOpaque(HoleMapMarkerPalette.HALO))
        assertTrue(HoleMapMarkerPalette.isFullyOpaque(HoleMapMarkerPalette.GLYPH))
    }

    @Test
    fun greenTitle_usesMappedLabel() {
        assertEquals("Green", HoleMapMarkerPalette.greenTitle(GreenMappingConfidence.MAPPED))
        assertEquals(
            GreenMappingConfidence.ESTIMATED.shortLabel,
            HoleMapMarkerPalette.greenTitle(GreenMappingConfidence.ESTIMATED),
        )
    }
}
