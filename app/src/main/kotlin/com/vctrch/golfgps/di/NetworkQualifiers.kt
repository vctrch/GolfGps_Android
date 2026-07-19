package com.vctrch.golfgps.di

import javax.inject.Qualifier

/** Shared Ktor client for OpenGolf catalog reads and Overpass/OSM. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CatalogHttpClient

/**
 * OpenGolf contribute writes (`expectSuccess = false`; raw JSON bodies).
 * Separate from [CatalogHttpClient] so write error handling stays explicit.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenGolfWriteHttpClient

/**
 * OpenGolf OAuth (`expectSuccess = false`, redirects disabled for Location capture).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenGolfAuthHttpClient
