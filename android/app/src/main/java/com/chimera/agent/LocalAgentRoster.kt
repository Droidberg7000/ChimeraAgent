package com.chimera.agent

data class LocalAgentSpec(val name: String, val model: String)

object LocalAgentRoster {
    private val modelTag = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,149}")

    fun validate(agents: List<LocalAgentSpec>): List<LocalAgentSpec> {
        require(agents.size in 1..5) { "Select one to five agents" }
        val snapshot = agents.map { LocalAgentSpec(it.name.trim(), it.model.trim()) }
        require(snapshot.all { agent -> agent.name.length in 1..40 &&
            agent.name.none { it.isISOControl() } }) { "Agent names must contain 1-40 printable characters" }
        require(snapshot.map { it.name.lowercase() }.distinct().size == snapshot.size) {
            "Agent names must be unique"
        }
        require(snapshot.all { modelTag.matches(it.model) && !it.model.contains("cloud", true) }) {
            "Use downloaded local model tags; public routing and cloud tags are disabled"
        }
        return snapshot
    }

    fun parse(text: String): List<LocalAgentSpec> {
        require(text.length <= 2048) { "Roster is too long" }
        val agents = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val parts = line.split('=', limit = 2)
            require(parts.size == 2) { "Each line must be Agent name=model:tag" }
            LocalAgentSpec(parts[0], parts[1])
        }.toList()
        return validate(agents)
    }
}
