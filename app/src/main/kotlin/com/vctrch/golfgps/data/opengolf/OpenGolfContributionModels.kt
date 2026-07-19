package com.vctrch.golfgps.data.opengolf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class OpenGolfMomentType(val rawValue: String, val label: String) {
    TEE("tee", "Tee"),
    GREEN("green", "Green"),
    PIN("pin", "Pin"),
    BETA("beta", "Tip"),
}

enum class OpenGolfCorrectionField(val rawValue: String, val label: String) {
    COURSE_NAME("course_name", "Course name"),
    CITY("city", "City"),
    POSTAL_CODE("postal_code", "Postal code"),
    PHONE("phone", "Phone"),
    WEBSITE("website", "Website"),
    ADDRESS("address", "Address"),
    ARCHITECT("architect", "Architect"),
    YEAR_BUILT("year_built", "Year built"),
    COURSE_TYPE("course_type", "Course type"),
}

data class OpenGolfMomentSubmission(
    val momentType: OpenGolfMomentType,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val courseId: String,
    val hole: Int,
    val playerId: String,
    val note: String?,
    val dedupKey: String,
)

data class OpenGolfCorrectionSubmission(
    val courseId: String,
    val field: OpenGolfCorrectionField,
    val proposedValue: String,
    val note: String?,
)

@Serializable
data class OpenGolfIngestResult(
    val ok: Boolean? = null,
    val ingested: Int? = null,
    val ids: List<String>? = null,
)

@Serializable
data class OpenGolfCorrectionResult(
    @SerialName("correction_id") val correctionId: String? = null,
    val status: String? = null,
    val error: String? = null,
)

@Serializable
data class OpenGolfApiErrorBody(
    val error: String? = null,
    val terms: String? = null,
    @SerialName("terms_version") val termsVersion: String? = null,
    val accept: String? = null,
)

data class OpenGolfTermsChallenge(
    val version: String,
    val termsUrl: String,
)

fun OpenGolfApiErrorBody.termsChallenge(): OpenGolfTermsChallenge? {
    if (error != "terms_acceptance_required") return null
    val version = termsVersion?.trim().orEmpty()
    if (version.isEmpty()) return null
    val url = terms?.takeIf { it.isNotBlank() } ?: "https://api.opengolfapi.org/terms.txt"
    return OpenGolfTermsChallenge(version = version, termsUrl = url)
}
