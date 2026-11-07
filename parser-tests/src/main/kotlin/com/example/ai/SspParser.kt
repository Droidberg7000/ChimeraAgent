package com.example.ai

import org.json.JSONObject
import org.json.JSONTokener

data class SspPayload(val text: String, val consensusScore: Double?)

/** Accept one object, or one complete fenced JSON object; never scrape braces from prose. */
object SspParser {
    fun parse(raw: String): SspPayload? = try {
        require(raw.length <= 65536)
        var body = raw.trim()
        if (body.startsWith("```")) {
            val lines = body.lines()
            require(lines.size >= 3 && lines.first().lowercase() in setOf("```", "```json"))
            require(lines.last() == "```")
            body = lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        require(body.startsWith("{") && body.endsWith("}"))
        val tokener = JSONTokener(body)
        val json = tokener.nextValue() as? JSONObject ?: error("Expected object")
        require(tokener.nextClean() == 0.toChar())
        require(json.opt("protocol_version") == "SSP-V1")
        val text = json.get("response_payload") as? String ?: error("Expected payload string")
        require(text.isNotBlank())
        val score = if (json.has("consensus_score") && !json.isNull("consensus_score")) {
            val number = json.get("consensus_score") as? Number ?: error("Expected numeric score")
            number.toDouble().also { require(it.isFinite() && it in 0.0..1.0) }
        } else null
        SspPayload(text, score)
    } catch (_: Exception) { null }
}
