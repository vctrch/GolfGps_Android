package com.vctrch.golfgps.di

import android.content.Context
import androidx.room.Room
import com.vctrch.golfgps.BuildConfig
import com.vctrch.golfgps.data.local.*
import com.vctrch.golfgps.data.remote.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient {
        return HttpClient(OkHttp) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
            }
            // Overpass queries run with a server-side `[timeout:25]`, and free instances are often
            // slow, so the client must wait longer than the OkHttp engine's ~10s default or every
            // OSM enrichment silently times out.
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 35_000
                socketTimeoutMillis = 35_000
            }
        }
    }

    @Provides
    @Singleton
    fun provideOpenGolfApi(client: HttpClient): OpenGolfApi {
        return KtorOpenGolfApi(client, BuildConfig.OPENGOLF_BASE_URL)
    }

    @Provides
    @Singleton
    fun provideOsmGolfSource(client: HttpClient): OsmGolfSource {
        return OverpassGolfSource(client)
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): GolfGpsDatabase {
        return Room.databaseBuilder(context, GolfGpsDatabase::class.java, "GolfGpsCache")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideCourseDataCache(
        database: GolfGpsDatabase,
        json: Json,
    ): CourseDataCache {
        return CourseDataCache(database.cachedCourseDao(), json)
    }

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        @ApplicationContext context: Context,
    ): UserPreferencesRepository {
        return UserPreferencesRepository.create(context)
    }
}
