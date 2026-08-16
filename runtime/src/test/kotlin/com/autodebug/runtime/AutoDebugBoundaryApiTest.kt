package com.autodebug.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AutoDebugBoundaryApiTest {
    private val lines = mutableListOf<Pair<String, String>>()

    @BeforeEach
    fun reset() {
        lines.clear()
        AutoDebugConfig.enabled = true
        AutoDebug.sink = AutoDebugSink { tag, msg -> lines += tag to msg }
    }

    @Test
    fun `logEnter formats and forwards`() {
        AutoDebug.logEnter("Demo", "greet", "name=Ada")
        assertEquals(listOf("Demo" to "⇢ greet(name=Ada)"), lines)
    }

    @Test
    fun `logExit formats result`() {
        AutoDebug.logExit("Demo", "greet", "Hello, Ada", 5)
        assertEquals(listOf("Demo" to "⇠ greet = Hello, Ada [5ms]"), lines)
    }

    @Test
    fun `logThrow formats throwable`() {
        AutoDebug.logThrow("Demo", "fail", IllegalArgumentException("x"), 2)
        assertEquals(listOf("Demo" to "⇠ fail threw IllegalArgumentException: x [2ms]"), lines)
    }

    @Test
    fun `disabled skips boundary logs`() {
        AutoDebugConfig.enabled = false
        AutoDebug.logEnter("Demo", "greet", "name=Ada")
        AutoDebug.logExit("Demo", "greet", "x", 1)
        AutoDebug.logThrow("Demo", "fail", RuntimeException("e"), 1)
        assertEquals(0, lines.size)
    }

    @Test
    fun `logBranch forwards when enabled`() {
        AutoDebug.logBranch("Demo", "classify", "if#0-then")
        assertEquals(listOf("Demo" to "↦ classify · if#0-then"), lines)
    }

    @Test
    fun `logBranch skipped when disabled`() {
        AutoDebugConfig.enabled = false
        AutoDebug.logBranch("Demo", "classify", "if#0-then")
        assertEquals(0, lines.size)
    }

    @Test
    fun `describeArgs joins name value pairs`() {
        assertEquals(
            "name=Ada, count=42",
            AutoDebug.describeArgs(arrayOf("name", "count"), arrayOf("Ada", 42)),
        )
    }
}
