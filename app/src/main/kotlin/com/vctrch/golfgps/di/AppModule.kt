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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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
    fun provideOkHttpClient(): OkHttpClient {
        val logging =
            HttpLoggingInterceptor().apply {
                level =
                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
            }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        json: Json,
        okHttpClient: OkHttpClient,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.OPENGOLF_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenGolfApiService(retrofit: Retrofit): OpenGolfApiService {
        return retrofit.create(OpenGolfApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenGolfApi(service: OpenGolfApiService): OpenGolfApi {
        return OpenGolfApiClient(service)
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): GolfGpsDatabase {
        return Room.databaseBuilder(context, GolfGpsDatabase::class.java, "GolfGpsCache")
            .fallbackToDestructiveMigration()
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
