package com.noop.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins Gemini's pure model-list parser (Mac parity): strip `models/` prefix, keep chat-capable
 * gemini-* only. No network.
 */
class GeminiModelsParseTest {

    @Test
    fun stripsModelsPrefixAndKeepsChatModels() {
        val body = JSONObject(
            """
            {"models":[
              {"name":"models/gemini-2.5-pro"},
              {"name":"models/gemini-2.5-flash"},
              {"name":"models/gemini-embedding-001"},
              {"name":"models/aqa"},
              {"name":"models/text-embedding-004"},
              {"name":"models/imagen-3.0-generate-002"}
            ]}
            """.trimIndent(),
        )
        assertEquals(
            listOf("gemini-2.5-pro", "gemini-2.5-flash"),
            AiCoach.parseGeminiModels(body),
        )
    }

    @Test
    fun toleratesNameWithoutModelsPrefix() {
        val body = JSONObject("""{"models":[{"name":"gemini-2.0-flash"}]}""")
        assertEquals(listOf("gemini-2.0-flash"), AiCoach.parseGeminiModels(body))
    }

    @Test
    fun dropsEmptyAndMalformedRows() {
        val body = JSONObject(
            """
            {"models":[
              {"name":""},
              {"id":"gemini-2.5-flash"},
              {"name":"models/gemini-2.5-flash-lite"}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("gemini-2.5-flash-lite"), AiCoach.parseGeminiModels(body))
    }

    @Test
    fun wrongEnvelopeKeyYieldsEmpty() {
        val body = JSONObject("""{"data":[{"id":"gemini-2.5-flash"}]}""")
        assertTrue(AiCoach.parseGeminiModels(body).isEmpty())
    }
}
