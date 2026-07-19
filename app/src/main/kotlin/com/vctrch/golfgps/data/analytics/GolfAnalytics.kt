package com.vctrch.golfgps.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.vctrch.golfgps.domain.GolfCourseSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

interface GolfAnalytics {
    fun setCollectionEnabled(enabled: Boolean)

    fun logSearch(
        query: String,
        resultCount: Int,
    )

    fun logCourseSelected(course: GolfCourseSummary)

    fun logRoundStarted(
        course: GolfCourseSummary,
        holeCount: Int,
    )

    fun logRoundEnded(courseId: String?)

    fun logHoleSelected(holeNumber: Int)

    fun logOsmEnrichment(
        courseId: String,
        mappedHoleCount: Int,
        forced: Boolean,
    )

    fun logReloadHoleGps(courseId: String)
}

@Singleton
class FirebaseGolfAnalytics
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : GolfAnalytics {
        private val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
        private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
        private val collectionEnabled = AtomicBoolean(true)

        override fun setCollectionEnabled(enabled: Boolean) {
            collectionEnabled.set(enabled)
            analytics.setAnalyticsCollectionEnabled(enabled)
            crashlytics.isCrashlyticsCollectionEnabled = enabled
        }

        override fun logSearch(
            query: String,
            resultCount: Int,
        ) {
            if (!collectionEnabled.get()) return
            analytics.logEvent(
                "course_search",
                Bundle().apply {
                    putString("query_length", query.length.toString())
                    putInt("result_count", resultCount)
                },
            )
        }

        override fun logCourseSelected(course: GolfCourseSummary) {
            if (!collectionEnabled.get()) return
            analytics.logEvent(
                "course_selected",
                Bundle().apply {
                    putString("course_id", course.id)
                    putString("course_name", course.name.take(COURSE_NAME_ANALYTICS_MAX))
                    course.state?.let { putString("state", it) }
                },
            )
        }

        override fun logRoundStarted(
            course: GolfCourseSummary,
            holeCount: Int,
        ) {
            if (!collectionEnabled.get()) return
            analytics.logEvent(
                "round_started",
                Bundle().apply {
                    putString("course_id", course.id)
                    putInt("hole_count", holeCount)
                },
            )
        }

        override fun logRoundEnded(courseId: String?) {
            if (!collectionEnabled.get()) return
            analytics.logEvent(
                "round_ended",
                Bundle().apply {
                    courseId?.let { putString("course_id", it) }
                },
            )
        }

        override fun logHoleSelected(holeNumber: Int) {
            if (!collectionEnabled.get()) return
            analytics.logEvent(
                "hole_selected",
                Bundle().apply { putInt("hole_number", holeNumber) },
            )
        }

        override fun logOsmEnrichment(
            courseId: String,
            mappedHoleCount: Int,
            forced: Boolean,
        ) {
            if (!collectionEnabled.get()) return
            analytics.logEvent(
                "osm_enrichment",
                Bundle().apply {
                    putString("course_id", courseId)
                    putInt("mapped_hole_count", mappedHoleCount)
                    putBoolean("forced", forced)
                },
            )
        }

        override fun logReloadHoleGps(courseId: String) {
            if (!collectionEnabled.get()) return
            analytics.logEvent(
                "reload_hole_gps",
                Bundle().apply { putString("course_id", courseId) },
            )
        }

        private companion object {
            const val COURSE_NAME_ANALYTICS_MAX = 100
        }
    }
