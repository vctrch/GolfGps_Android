package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.GolfCourseSummary
import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.LatLng

/** Public OpenStreetMap browse / edit URLs for community map updates. */
object OSMCourseLinks {
    const val HOME = "https://www.openstreetmap.org/"

    fun courseWay(osmId: Long): String = "https://www.openstreetmap.org/way/$osmId"

    fun editCourseWay(osmId: Long): String = "https://www.openstreetmap.org/edit?way=$osmId"

    fun editMap(
        at: LatLng,
        zoom: Int = 18,
    ): String {
        val lat = formatCoordinate(at.latitude)
        val lon = formatCoordinate(at.longitude)
        return "https://www.openstreetmap.org/edit?editor=id#map=$zoom/$lat/$lon"
    }

    fun newNote(at: LatLng): String {
        val lat = formatCoordinate(at.latitude)
        val lon = formatCoordinate(at.longitude)
        return "https://www.openstreetmap.org/note/new?lat=$lat&lon=$lon"
    }

    fun preferredEditCoordinate(
        hole: HoleTarget,
        course: GolfCourseSummary,
    ): LatLng {
        if (hole.hasReliableGreenPosition) return hole.green
        hole.tee?.let { return it }
        return LatLng(course.latitude, course.longitude)
    }

    private fun formatCoordinate(value: Double): String = "%.6f".format(value)
}
