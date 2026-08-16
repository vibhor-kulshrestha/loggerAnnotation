package com.autodebug.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoDebugMessagesTest {
    @Test
    fun `truncate short string unchanged`() {
        assertEquals("hi", AutoDebugMessages.truncate("hi", 300))
    }

    @Test
    fun `truncate long string with ellipsis`() {
        val long = "a".repeat(400)
        val out = AutoDebugMessages.truncate(long, 300)
        assertEquals(303, out.length) // 300 + "..."
        assertTrue(out.endsWith("..."))
    }

    @Test
    fun `formatValue null`() {
        assertEquals("null", AutoDebugMessages.formatValue(null))
    }

    @Test
    fun `formatValue catches toString failure`() {
        val bad = object {
            override fun toString(): String = error("boom")
        }
        assertTrue(AutoDebugMessages.formatValue(bad).contains("toString failed"))
    }

    @Test
    fun `branch message shape`() {
        assertEquals("↦ classify · if#0-then", AutoDebugMessages.branch("classify", "if#0-then"))
    }

    @Test
    fun `assignment message shape`() {
        assertEquals(
            "↻ bump · total: 0 → 3",
            AutoDebugMessages.assignment("bump", "total", 0, 3),
        )
    }

    @Test
    fun `assignment truncates long values`() {
        val long = "x".repeat(400)
        val out = AutoDebugMessages.assignment("m", "v", long, long)
        assertTrue(out.contains("↻ m · v: "))
        assertTrue(out.contains(" → "))
        assertTrue(out.endsWith("..."))
    }

    @Test
    fun `enter exit throw message shapes`() {
        assertEquals("⇢ greet(name=Ada)", AutoDebugMessages.enter("greet", "name=Ada"))
        assertEquals("⇠ greet = Hello [12ms]", AutoDebugMessages.exit("greet", "Hello", 12))
        val t = IllegalStateException("nope")
        assertEquals(
            "⇠ greet threw IllegalStateException: nope [3ms]",
            AutoDebugMessages.thrown("greet", t, 3),
        )
    }
}
