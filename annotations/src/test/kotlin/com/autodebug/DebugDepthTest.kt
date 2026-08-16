package com.autodebug

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DebugDepthTest {
    @Test
    fun `depth enum exposes progressive levels in order`() {
        assertArrayEquals(
            arrayOf(DebugDepth.BOUNDARY, DebugDepth.BRANCHES, DebugDepth.VARS),
            DebugDepth.entries.toTypedArray(),
        )
    }

    @Test
    fun `AutoDebug defaults match Phase 1 contract`() {
        val defaults = AutoDebug()
        assertEquals("", defaults.tag)
        assertEquals(DebugDepth.BOUNDARY, defaults.depth)
    }
}
