package com.vctrch.golfgps.data.opengolf

import com.vctrch.golfgps.di.OpenGolfAuthHttpClient
import com.vctrch.golfgps.domain.GolfDataException
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenGolfAuthStore
    @Inject
    constructor(
        private val secureStore: OpenGolfSecureStore,
        private val json: Json,
        private val config: OpenGolfConfig,
        @OpenGolfAuthHttpClient private val authClient: HttpClient,
    ) {
        enum class Phase { SIGNED_OUT, AWAITING_CODE, SIGNED_IN }

        enum class AuthIntent { CREATE_ACCOUNT, SIGN_IN }

        data class State(
            val phase: Phase = Phase.SIGNED_OUT,
            val intent: AuthIntent = AuthIntent.SIGN_IN,
            val email: String = "",
            val playerId: String = "",
            val accessToken: String? = null,
            val isBusy: Boolean = false,
            val statusMessage: String? = null,
            val errorMessage: String? = null,
        ) {
            val isSignedIn: Boolean get() = phase == Phase.SIGNED_IN && !accessToken.isNullOrBlank()
        }

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        private var pkce: OpenGolfPkce.Challenge? = null

        init {
            restore()
        }

        fun restore() {
            val token = secureStore.accessToken
            val email = secureStore.email
            val playerId = secureStore.playerId
            if (!token.isNullOrBlank() && !email.isNullOrBlank() && !playerId.isNullOrBlank()) {
                _state.value =
                    State(
                        phase = Phase.SIGNED_IN,
                        email = email,
                        playerId = playerId,
                        accessToken = token,
                    )
            }
        }

        suspend fun requestSignInCode(
            rawEmail: String,
            intent: AuthIntent = AuthIntent.SIGN_IN,
        ) {
            _state.update { it.copy(errorMessage = null, statusMessage = null, intent = intent, isBusy = true) }
            val normalized = OpenGolfIdentity.normalizeEmail(rawEmail)
            if (!normalized.contains('@') || !normalized.contains('.')) {
                _state.update {
                    it.copy(isBusy = false, errorMessage = "Enter a valid email address.")
                }
                return
            }
            try {
                val challenge = OpenGolfPkce.generate()
                pkce = challenge
                postJson(
                    path = "oauth/start",
                    body =
                        buildJsonObject {
                            put("email", normalized)
                            put("client_id", config.clientId)
                        },
                )
                _state.update {
                    it.copy(
                        isBusy = false,
                        email = normalized,
                        playerId = OpenGolfIdentity.deriveOpenGolfId(normalized),
                        phase = Phase.AWAITING_CODE,
                        statusMessage =
                            when (intent) {
                                AuthIntent.CREATE_ACCOUNT ->
                                    "We emailed a code to $normalized. Enter it to create your OpenGolf account."
                                AuthIntent.SIGN_IN ->
                                    "We emailed a sign-in code to $normalized."
                            },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isBusy = false, errorMessage = e.message ?: "Sign-in failed.")
                }
            }
        }

        suspend fun verifyCode(code: String) {
            _state.update { it.copy(errorMessage = null, statusMessage = null, isBusy = true) }
            val otp = code.trim()
            if (otp.length < 4) {
                _state.update {
                    it.copy(isBusy = false, errorMessage = "Enter the code from your email.")
                }
                return
            }
            val challenge = pkce
            if (challenge == null) {
                _state.update {
                    it.copy(
                        isBusy = false,
                        phase = Phase.SIGNED_OUT,
                        errorMessage = "Request a new code first.",
                    )
                }
                return
            }
            try {
                val email = _state.value.email
                val codeResponse =
                    postOAuthCode(
                        body =
                            buildJsonObject {
                                put("email", email)
                                put("otp", otp)
                                put("client_id", config.clientId)
                                put("redirect_uri", config.redirectUri)
                                put("scope", "identity")
                                put("code_challenge", challenge.challenge)
                                put("code_challenge_method", "S256")
                            },
                    )
                val authCode =
                    extractAuthCode(codeResponse)
                        ?: throw GolfDataException.authenticationFailed("Could not read the OpenGolf auth code.")
                val tokenResponse =
                    postJson(
                        path = "oauth/token",
                        body =
                            buildJsonObject {
                                put("grant_type", "authorization_code")
                                put("code", authCode)
                                put("redirect_uri", config.redirectUri)
                                put("client_id", config.clientId)
                                put("code_verifier", challenge.verifier)
                            },
                    )
                val accessToken =
                    tokenResponse.string("access_token")
                        ?: throw GolfDataException.authenticationFailed("OpenGolf did not return an access token.")
                var playerId = _state.value.playerId
                tokenResponse.string("id_token")?.let { idToken ->
                    OpenGolfIdentity.subjectFromIdToken(idToken)?.let { playerId = it }
                }

                secureStore.accessToken = accessToken
                secureStore.email = email
                secureStore.playerId = playerId
                pkce = null

                var status =
                    when (_state.value.intent) {
                        AuthIntent.CREATE_ACCOUNT ->
                            "OpenGolf account ready. You can mark tee, green, and pin on the map."
                        AuthIntent.SIGN_IN ->
                            "Signed in to OpenGolf. You can submit map updates."
                    }
                if (!config.hasApiKey) {
                    status += " Note: set OPENGOLF_API_KEY in local.properties to enable submissions."
                }
                _state.update {
                    it.copy(
                        isBusy = false,
                        phase = Phase.SIGNED_IN,
                        accessToken = accessToken,
                        playerId = playerId,
                        statusMessage = status,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isBusy = false, errorMessage = e.message ?: "Sign-in failed.")
                }
            }
        }

        fun signOut() {
            secureStore.clear()
            pkce = null
            _state.value = State()
        }

        fun loadSignedInStateForTesting(
            email: String,
            playerId: String,
            accessToken: String,
        ) {
            _state.value =
                State(
                    phase = Phase.SIGNED_IN,
                    email = email,
                    playerId = playerId,
                    accessToken = accessToken,
                )
        }

        private suspend fun postJson(
            path: String,
            body: JsonObject,
        ): JsonObject {
            val base = config.apiBaseUrl
            val response: HttpResponse =
                authClient.post("$base/$path") {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                val message =
                    runCatching { json.decodeFromString<OpenGolfApiErrorBody>(text).error }.getOrNull()
                throw GolfDataException.authenticationFailed(
                    message ?: "Sign-in failed (${response.status.value}).",
                )
            }
            if (text.isBlank()) return buildJsonObject { }
            return runCatching { json.decodeFromString<JsonObject>(text) }.getOrElse {
                buildJsonObject { put("raw", text) }
            }
        }

        private suspend fun postOAuthCode(body: JsonObject): JsonObject {
            val base = config.apiBaseUrl
            val response: HttpResponse =
                authClient.post("$base/oauth/code") {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            val location = response.headers[HttpHeaders.Location]
            if (!location.isNullOrBlank()) {
                return buildJsonObject {
                    put("redirect", location)
                    put("location", location)
                }
            }
            val text = response.bodyAsText()
            if (response.status.value in 300..399) {
                throw GolfDataException.authenticationFailed("Sign-in redirect missing Location header.")
            }
            if (!response.status.isSuccess()) {
                val message =
                    runCatching { json.decodeFromString<OpenGolfApiErrorBody>(text).error }.getOrNull()
                throw GolfDataException.authenticationFailed(
                    message ?: "Sign-in failed (${response.status.value}).",
                )
            }
            if (text.isBlank()) return buildJsonObject { }
            return runCatching { json.decodeFromString<JsonObject>(text) }.getOrElse {
                buildJsonObject { put("raw", text) }
            }
        }

        companion object {
            fun extractAuthCode(response: JsonObject): String? {
                response.string("code")?.takeIf { it.isNotBlank() }?.let { return it }
                listOf("redirect", "location", "raw").forEach { key ->
                    response.string(key)?.let { value ->
                        codeFromUrl(value)?.let { return it }
                    }
                }
                return null
            }

            private fun codeFromUrl(value: String): String? {
                return runCatching {
                    URI(value).query
                        ?.split('&')
                        ?.mapNotNull { part ->
                            val idx = part.indexOf('=')
                            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
                        }
                        ?.firstOrNull { it.first == "code" }
                        ?.second
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }

            private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        }
    }
