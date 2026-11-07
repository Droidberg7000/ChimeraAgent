package com.chimera.agent

import org.junit.Assert.*
import org.junit.Test

class LocalAgentRosterTest {
    @Test fun parsesNamesAndModels() {
        val agents = LocalAgentRoster.parse("Architect=qwen3:4b\nAuditor=llama3.2:latest")
        assertEquals(2, agents.size)
        assertEquals("Auditor", agents[1].name)
        assertEquals("llama3.2:latest", agents[1].model)
    }
    @Test fun repeatedModelIsAllowedForDifferentRoles() {
        val agents = LocalAgentRoster.parse("Architect=qwen3:4b\nAuditor=qwen3:4b")
        assertEquals(agents[0].model, agents[1].model)
    }
    @Test fun rejectsBadRosters() {
        for (text in listOf("", "MissingEquals", "A=qwen3:4b\na=llama3.2", "A=test:cloud",
            "A=host/model", "=qwen3:4b", "A=", "A=model with spaces")) {
            var rejected = false
            try { LocalAgentRoster.parse(text) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(text, rejected)
        }
    }
    @Test fun rejectsTooManyAgents() {
        val text = (1..6).joinToString("\n") { "Agent$it=qwen3:4b" }
        var rejected = false
        try { LocalAgentRoster.parse(text) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
