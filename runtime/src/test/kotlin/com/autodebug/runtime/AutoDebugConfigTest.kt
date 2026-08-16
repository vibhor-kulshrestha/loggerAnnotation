package com.autodebug.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AutoDebugConfigTest {
    @BeforeEach
    fun reset() {
        AutoDebugConfig.enabled = true
        AutoDebug.sink = AutoDebugSink { _, _ -> }
    }

    @Test
    fun `enabled defaults to true`() {
        assertTrue(AutoDebugConfig.enabled)
    }

    @Test
    fun `log is no-op when disabled`() {
        val messages = mutableListOf<String>()
        AutoDebug.sink = AutoDebugSink { _, msg -> messages += msg }
        AutoDebugConfig.enabled = false
        AutoDebug.log("Tag", "hello")
        assertEquals(0, messages.size)
    }

    @Test
    fun `log forwards to sink when enabled`() {
        val messages = mutableListOf<Pair<String, String>>()
        AutoDebug.sink = AutoDebugSink { tag, msg -> messages += tag to msg }
        AutoDebug.log("Demo", "⇢ enter")
        assertEquals(listOf("Demo" to "⇢ enter"), messages)
    }
}
