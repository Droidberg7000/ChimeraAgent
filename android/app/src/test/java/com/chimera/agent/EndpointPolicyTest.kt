package com.chimera.agent

import org.junit.Assert.*
import org.junit.Test

class EndpointPolicyTest {
    @Test fun acceptsLocalAddresses() {
        val client = OllamaClient()
        try {
            for (url in listOf("http://127.0.0.1:11434/", "http://10.0.2.2:11434/", "https://192.168.1.2/")) {
                assertNotNull(client.validateEndpoint(url))
            }
        } finally { client.close() }
    }
    @Test fun rejectsPublicOrCredentialedEndpoints() {
        val client = OllamaClient()
        try {
            for (url in listOf("https://example.com/", "http://8.8.8.8/", "http://user:pass@127.0.0.1/", "http://127.0.0.1/api/chat")) {
                var rejected = false
                try { client.validateEndpoint(url) } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue(url, rejected)
            }
        } finally { client.close() }
    }
}
