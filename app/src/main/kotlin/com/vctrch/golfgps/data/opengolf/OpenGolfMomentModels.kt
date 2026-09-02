package com.vctrch.golfgps.data.opengolf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Query filters for `GET /api/v1/moments` (player, session, type, limit, cursor). */
data class OpenGolfMomentsQuery(
    val player: String? = null,
    val session: String? = null,
    val type: String? = null,
    val limit: Int? = null,
    val cursor: String? = null,
) {
    fun queryItems(): List<Pair<String, String>> =
        buildList {
            trimmed(player)?.let { add("player" to it) }
            trimmed(session)?.let { add("session" to it) }
            trimmed(type)?.let { add("type" to it) }
            if (limit != null) add("limit" to limit.toString())
            trimmed(cursor)?.let { add("cursor" to it) }
        }

    private fun trimmed(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}

/** One Moments row from `GET /api/v1/moments`. `momentType` is a string so unknown types still decode. */
data class OpenGolfMomentRecord(
    val serverId: String? = null,
    val momentType: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
    val recordedAt: String? = null,
    val courseId: String? = null,
    val hole: Int? = null,
    val playerId: String? = null,
    val sessionId: String? = null,
    val dedupKey: String? = null,
    val consentScope: String? = null,
    val payload: JsonObject? = null,
) {
    val displayNote: String?
        get() {
            val payload = payload ?: return null
            for (key in listOf("note", "body", "message")) {
                val text = payload.stringValue(key)?.trim()?.takeIf { it.isNotEmpty() }
                if (text != null) return text
            }
            return null
        }

    val displayStrokes: Int?
        get() {
            val strokes = payload?.intValue("strokes") ?: return null
            return strokes.takeIf { it in 1..30 }
        }

    val id: String
        get() {
            if (!serverId.isNullOrEmpty()) return serverId
            if (!dedupKey.isNullOrEmpty()) return dedupKey
            return "$momentType-$hole-$recordedAt"
        }

    companion object {
        fun decode(
            json: Json,
            element: JsonElement,
        ): OpenGolfMomentRecord = json.decodeFromJsonElement<OpenGolfMomentRecordWire>(element).toRecord()
    }
}

data class OpenGolfMomentsListResponse(
    val moments: List<OpenGolfMomentRecord>,
    val nextCursor: String? = null,
) {
    companion object {
        fun decode(
            json: Json,
            text: String,
        ): OpenGolfMomentsListResponse {
            val element = json.parseToJsonElement(text)
            if (element is JsonArray) {
                return OpenGolfMomentsListResponse(
                    moments = element.map { OpenGolfMomentRecord.decode(json, it) },
                    nextCursor = null,
                )
            }
            val obj = element as? JsonObject
            val momentsElement =
                obj?.get("moments") as? JsonArray
                    ?: obj?.get("data") as? JsonArray
            val moments =
                momentsElement?.map { OpenGolfMomentRecord.decode(json, it) }
                    ?: emptyList()
            val nextCursor =
                (obj?.get("next_cursor") as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotEmpty() }
            return OpenGolfMomentsListResponse(moments = moments, nextCursor = nextCursor)
        }
    }
}

@Serializable
private data class OpenGolfMomentRecordWire(
    @SerialName("id") val serverId: String? = null,
    @SerialName("moment_type") val momentType: String,
    @SerialName("lat") val latitude: Double? = null,
    @SerialName("lng") val longitude: Double? = null,
    @SerialName("accuracy_m") val accuracyMeters: Double? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("course_id") val courseId: String? = null,
    val hole: Int? = null,
    @SerialName("player_id") val playerId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("dedup_key") val dedupKey: String? = null,
    @SerialName("consent_scope") val consentScope: String? = null,
    val payload: JsonObject? = null,
    val note: String? = null,
)

private fun OpenGolfMomentRecordWire.toRecord(): OpenGolfMomentRecord {
    val decodedPayload = (payload ?: JsonObject(emptyMap())).toMutableMap()
    val topLevelNote = note?.trim().orEmpty()
    if (topLevelNote.isNotEmpty() && decodedPayload.lacksDisplayNote()) {
        decodedPayload["note"] = JsonPrimitive(topLevelNote)
    }
    val foldedPayload = decodedPayload.takeIf { it.isNotEmpty() }?.let { JsonObject(it) }
    return OpenGolfMomentRecord(
        serverId = serverId,
        momentType = momentType,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        recordedAt = recordedAt,
        courseId = courseId,
        hole = hole,
        playerId = playerId,
        sessionId = sessionId,
        dedupKey = dedupKey,
        consentScope = consentScope,
        payload = foldedPayload,
    )
}

private fun Map<String, JsonElement>.lacksDisplayNote(): Boolean =
    this["note"] == null && this["body"] == null && this["message"] == null

private fun JsonObject.stringValue(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (!primitive.isString) return null
    return primitive.content
}

private fun JsonObject.intValue(key: String): Int? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    return primitive.intOrNull ?: primitive.doubleOrNull?.toInt()
}
