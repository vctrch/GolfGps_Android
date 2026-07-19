package com.vctrch.golfgps.data.remote

/** Public OpenGolf directory URLs for scorecard edits and course submissions. */
object OpenGolfCourseLinks {
    const val SEARCH = "https://courses.opengolfapi.org/search"
    const val SUBMIT_COURSE = "https://courses.opengolfapi.org/submit"

    fun coursePage(course: com.vctrch.golfgps.domain.GolfCourseSummary): String {
        val state = normalizedStateCode(course.state)
        return if (state != null) {
            "https://courses.opengolfapi.org/courses/$state/${course.id}"
        } else {
            "$SEARCH?q=${java.net.URLEncoder.encode(course.name, Charsets.UTF_8.name())}"
        }
    }

    private fun normalizedStateCode(state: String?): String? {
        val trimmed = state?.trim()?.lowercase() ?: return null
        if (trimmed.length != 2 || !trimmed.all { it.isLetter() }) return null
        return trimmed
    }
}
