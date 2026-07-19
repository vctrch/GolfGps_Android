package com.vctrch.golfgps.di

import android.content.Context
import androidx.room.Room
import com.vctrch.golfgps.BuildConfig
import com.vctrch.golfgps.data.analytics.FirebaseGolfAnalytics
import com.vctrch.golfgps.data.analytics.GolfAnalytics
import com.vctrch.golfgps.data.local.*
import com.vctrch.golfgps.data.opengolf.OpenGolfConfig
import com.vctrch.golfgps.data.opengolf.OpenGolfSecureStore
import com.vctrch.golfgps.data.remote.*
import dagger.Binds
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
abstract class AnalyticsModule {
    @Binds
    @Singleton
    abstract fun bindGolfAnalytics(impl: FirebaseGolfAnalytics): GolfAnalytics
}

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
    fun provideOpenGolfConfig(): OpenGolfConfig =
        OpenGolfConfig(
            apiKey = BuildConfig.OPENGOLF_API_KEY,
            baseUrl = BuildConfig.OPENGOLF_BASE_URL,
            clientId = BuildConfig.OPENGOLF_CLIENT_ID,
            redirectUri = BuildConfig.OPENGOLF_REDIRECT_URI,
        )

    @Provides
    @Singleton
    @CatalogHttpClient
    fun provideCatalogHttpClient(json: Json): HttpClient {
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
    @OpenGolfWriteHttpClient
    fun provideOpenGolfWriteHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    @Provides
    @Singleton
    @OpenGolfAuthHttpClient
    fun provideOpenGolfAuthHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    @Provides
    @Singleton
    fun provideOpenGolfApi(
        @CatalogHttpClient client: HttpClient,
        config: OpenGolfConfig,
    ): OpenGolfApi {
        return KtorOpenGolfApi(client, config.baseUrl)
    }

    @Provides
    @Singleton
    fun provideOsmGolfSource(
        @CatalogHttpClient client: HttpClient,
    ): OsmGolfSource {
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

    @Provides
    @Singleton
    fun provideOpenGolfSecureStore(
        @ApplicationContext context: Context,
    ): OpenGolfSecureStore {
        return OpenGolfSecureStore.create(context)
    }
}
