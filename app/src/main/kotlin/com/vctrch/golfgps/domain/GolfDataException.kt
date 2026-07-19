package com.vctrch.golfgps.domain

enum class GolfDataError {
    INVALID_RESPONSE,
    COURSE_NOT_FOUND,
    NO_HOLE_DATA,
    AUTHENTICATION_FAILED,
    CONTRIBUTION_FAILED,
    TERMS_ACCEPTANCE_REQUIRED,
}

class GolfDataException(
    val error: GolfDataError,
    override val message: String = error.name,
    val termsVersion: String? = null,
    val termsUrl: String? = null,
) : Exception(message) {
    companion object {
        fun authenticationFailed(message: String) = GolfDataException(GolfDataError.AUTHENTICATION_FAILED, message)

        fun contributionFailed(message: String) = GolfDataException(GolfDataError.CONTRIBUTION_FAILED, message)

        fun termsAcceptanceRequired(
            version: String,
            termsUrl: String,
        ) = GolfDataException(
            error = GolfDataError.TERMS_ACCEPTANCE_REQUIRED,
            message = "OpenGolf requires accepting terms ($version) before contributing.",
            termsVersion = version,
            termsUrl = termsUrl,
        )
    }
}
