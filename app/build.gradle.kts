plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.vctrch.golfgps"
    compileSdk = 37

    fun localProp(key: String): String {
        val file = rootProject.file("local.properties")
        if (!file.exists()) return ""
        val prefix = "$key="
        return file.readLines()
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.trim()
            .orEmpty()
    }

    val mapsApiKey = localProp("MAPS_API_KEY")
    val openGolfApiKey = localProp("OPENGOLF_API_KEY")
    val openGolfClientId = localProp("OPENGOLF_CLIENT_ID").ifEmpty { "com.vctrch.golfgps" }
    val openGolfRedirectUri =
        localProp("OPENGOLF_REDIRECT_URI").ifEmpty { "https://api.opengolfapi.org/oauth/callback" }

    defaultConfig {
        applicationId = "com.vctrch.golfgps"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OPENGOLF_BASE_URL", "\"https://api.opengolfapi.org/\"")
        buildConfigField("String", "MAPS_API_KEY", "\"${mapsApiKey.ifEmpty { "YOUR_MAPS_API_KEY" }}\"")
        buildConfigField("String", "OPENGOLF_API_KEY", "\"${openGolfApiKey.replace("\"", "\\\"")}\"")
        buildConfigField("String", "OPENGOLF_CLIENT_ID", "\"${openGolfClientId.replace("\"", "\\\"")}\"")
        buildConfigField(
            "String",
            "OPENGOLF_REDIRECT_URI",
            "\"${openGolfRedirectUri.replace("\"", "\\\"")}\"",
        )
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey.ifEmpty { "YOUR_MAPS_API_KEY" }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProp("RELEASE_STORE_FILE")
            if (storeFilePath.isNotEmpty()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProp("RELEASE_STORE_PASSWORD")
                keyAlias = localProp("RELEASE_KEY_ALIAS")
                keyPassword = localProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // OpenStreetMap tiles work on any emulator without a Google Maps API key.
            buildConfigField("boolean", "USE_OSM_MAP", "true")
        }
        release {
            buildConfigField("boolean", "USE_OSM_MAP", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    additionalEditorconfig.set(
        mapOf(
            "ktlint_standard_no-wildcard-imports" to "disabled",
        ),
    )
    filter {
        exclude("**/build/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/detekt.yml")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.osmdroid)
    implementation(libs.billing.ktx)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.car.app.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
}
