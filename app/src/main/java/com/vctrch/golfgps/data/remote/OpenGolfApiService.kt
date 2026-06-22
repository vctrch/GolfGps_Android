package com.vctrch.golfgps.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenGolfApiService {
    @GET("courses/search")
    suspend fun searchCourses(
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
    ): OpenGolfSearchResponse

    @GET("courses/{id}")
    suspend fun loadCourse(
        @Path("id") id: String,
    ): OpenGolfCourseDetail
}
