package com.example.ai

import org.junit.Assert.*
import org.junit.Test

class SspParserTest {
    @Test fun parsesPlainObject() {
        val result = SspParser.parse("""{"protocol_version":"SSP-V1","response_payload":"ok","consensus_score":0.5}""")
        assertEquals("ok", result?.text)
        assertEquals(0.5, result?.consensusScore ?: -1.0, 0.0)
    }
    @Test fun parsesFence() {
        val raw = "```json\n" + """{"protocol_version":"SSP-V1","response_payload":"ok"}""" + "\n```"
        assertEquals("ok", SspParser.parse(raw)?.text)
    }
    @Test fun rejectsProse() {
        assertNull(SspParser.parse("Prefix: " + """{"protocol_version":"SSP-V1","response_payload":"ok"}"""))
    }
    @Test fun rejectsTrailingObject() {
        assertNull(SspParser.parse("""{"protocol_version":"SSP-V1","response_payload":"ok"} {}"""))
    }
    @Test fun rejectsBadScoreAndPayload() {
        assertNull(SspParser.parse("""{"protocol_version":"SSP-V1","response_payload":"ok","consensus_score":1.1}"""))
        assertNull(SspParser.parse("""{"protocol_version":"SSP-V1","response_payload":42}"""))
    }
    @Test fun preservesBracesInPayload() {
        val result = SspParser.parse("""{"protocol_version":"SSP-V1","response_payload":"Use {a} literally"}""")
        assertEquals("Use {a} literally", result?.text)
    }
}
