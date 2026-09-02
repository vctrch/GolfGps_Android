package com.vctrch.golfgps.data.opengolf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenGolfMomentModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun queryItems_omitsNullBlankAndWhitespace() {
        val query =
            OpenGolfMomentsQuery(
                player = "p1",
                session = "",
                type = null,
                limit = 5,
                cursor = "  ",
            )
        val items = query.queryItems()
        assertEquals(listOf("player", "limit"), items.map { it.first })
        assertEquals("p1", items.first { it.first == "player" }.second)
        assertEquals("5", items.first { it.first == "limit" }.second)
    }

    @Test
    fun queryItems_omitsEmptyPlayerSessionAndCursor() {
        val items =
            OpenGolfMomentsQuery(
                player = "   ",
                session = "",
                type = "score",
                cursor = null,
            ).queryItems()
        assertEquals(listOf("type"), items.map { it.first })
        assertEquals("score", items.single().second)
    }

    @Test
    fun displayNote_prefersNoteThenBodyThenMessage() {
        val pin =
            OpenGolfMomentRecord.decode(
                json,
                json.parseToJsonElement(
                    """{"moment_type":"pin","payload":{"hole":3,"note":"left pin"}}""",
                ),
            )
        assertEquals("left pin", pin.displayNote)

        val message =
            OpenGolfMomentRecord.decode(
                json,
                json.parseToJsonElement(
                    """{"moment_type":"message","payload":{"hole":3,"body":"slow greens"}}""",
                ),
            )
        assertEquals("slow greens", message.displayNote)

        val fallback =
            OpenGolfMomentRecord.decode(
                json,
                json.parseToJsonElement(
                    """{"moment_type":"swing","payload":{"message":"keep it in the fairway"}}""",
                ),
            )
        assertEquals("keep it in the fairway", fallback.displayNote)
    }

    @Test
    fun displayStrokes_acceptsOnlyOneToThirty() {
        val score =
            OpenGolfMomentRecord.decode(
                json,
                json.parseToJsonElement(
                    """{"moment_type":"score","payload":{"hole":3,"strokes":5,"note":"bogey"}}""",
                ),
            )
        assertEquals(5, score.displayStrokes)
        assertEquals("bogey", score.displayNote)

        val tooLow =
            OpenGolfMomentRecord(
                momentType = "score",
                payload = buildJsonObject { put("strokes", 0) },
            )
        assertNull(tooLow.displayStrokes)

        val tooHigh =
            OpenGolfMomentRecord(
                momentType = "score",
                payload = buildJsonObject { put("strokes", 31) },
            )
        assertNull(tooHigh.displayStrokes)
    }

    @Test
    fun id_prefersServerIdThenDedupKeyThenComposite() {
        assertEquals(
            "m_1",
            OpenGolfMomentRecord(serverId = "m_1", momentType = "tee", dedupKey = "d1").id,
        )
        assertEquals(
            "round-42:7:score",
            OpenGolfMomentRecord(momentType = "score", hole = 7, dedupKey = "round-42:7:score").id,
        )
        val fallback =
            OpenGolfMomentRecord(
                momentType = "tee",
                hole = 1,
                recordedAt = "2026-01-01T00:00:00Z",
            )
        assertEquals("tee-1-2026-01-01T00:00:00Z", fallback.id)
    }

    @Test
    fun decode_foldsTopLevelNoteIntoPayloadWhenMissing() {
        val record =
            OpenGolfMomentRecord.decode(
                json,
                json.parseToJsonElement(
                    """{"moment_type":"swing","note":"fat contact","payload":{"hole":4}}""",
                ),
            )
        assertEquals("fat contact", record.displayNote)
        assertEquals(JsonPrimitive("fat contact"), record.payload?.get("note"))
    }

    @Test
    fun decode_foldsTopLevelNoteWhenPayloadNoteIsBlank() {
        val record =
            OpenGolfMomentRecord.decode(
                json,
                json.parseToJsonElement(
                    """{"moment_type":"swing","note":"fat contact","payload":{"hole":4,"note":"  "}}""",
                ),
            )
        assertEquals("fat contact", record.displayNote)
        assertEquals(JsonPrimitive("fat contact"), record.payload?.get("note"))
    }

    @Test
    fun decode_keepsPayloadBodyOverTopLevelNote() {
        val record =
            OpenGolfMomentRecord.decode(
                json,
                json.parseToJsonElement(
                    """{"moment_type":"message","note":"ignored","payload":{"body":"slow greens"}}""",
                ),
            )
        assertEquals("slow greens", record.displayNote)
        assertEquals(JsonPrimitive("slow greens"), record.payload?.get("body"))
        assertEquals(null, record.payload?.get("note"))
    }

    @Test
    fun decode_doesNotRemapPlayerId() {
        val record =
            OpenGolfMomentRecord.decode(
                json,
                json.parseToJsonElement(
                    """{"moment_type":"pin","player_id":"ogid_player1"}""",
                ),
            )
        assertEquals("ogid_player1", record.playerId)
    }

    @Test
    fun decode_keepsUnknownMomentType() {
        val record =
            OpenGolfMomentRecord.decode(
                json,
                json.parseToJsonElement("""{"moment_type":"swing"}"""),
            )
        assertEquals("swing", record.momentType)
    }

    @Test
    fun listResponse_decodesDataKeyAlias() {
        val decoded =
            OpenGolfMomentsListResponse.decode(
                json,
                """{"data":[{"moment_type":"swing"}],"next_cursor":"n1"}""",
            )
        assertEquals(1, decoded.moments.size)
        assertEquals("swing", decoded.moments[0].momentType)
        assertEquals("n1", decoded.nextCursor)
    }

    @Test
    fun listResponse_decodesBareArray() {
        val decoded =
            OpenGolfMomentsListResponse.decode(
                json,
                """[{"moment_type":"breadcrumb","player_id":"ogid_p1"}]""",
            )
        assertEquals(1, decoded.moments.size)
        assertEquals("breadcrumb", decoded.moments[0].momentType)
        assertNull(decoded.nextCursor)
    }
}
