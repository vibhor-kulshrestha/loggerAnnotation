package com.autodebug.compiler

import com.autodebug.runtime.AutoDebug
import com.autodebug.runtime.AutoDebugConfig
import com.autodebug.runtime.AutoDebugSink
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

@OptIn(ExperimentalCompilerApi::class)
class BranchLoggingIrTest {
    private val lines = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        lines.clear()
        AutoDebugConfig.enabled = true
        AutoDebug.sink = AutoDebugSink { _, msg -> lines += msg }
    }

    @Test
    fun `BOUNDARY depth does not emit branch lines`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            import com.autodebug.DebugDepth
            class Subject {
              @AutoDebug(tag = "Subject", depth = DebugDepth.BOUNDARY)
              fun classify(x: Int): String = if (x > 0) "pos" else "nonpos"
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val classify: Method = clazz.getMethod("classify", Int::class.javaPrimitiveType)
        val value = classify.invoke(instance, 1)
        assertEquals("pos", value)
        assertTrue(lines.any { it.startsWith("⇢ classify(") }, lines.toString())
        assertTrue(lines.any { it.startsWith("⇠ classify =") && it.contains("pos") }, lines.toString())
        assertFalse(lines.any { it.contains("↦") }, lines.toString())
    }

    @Test
    fun `BRANCHES depth logs if arm`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            import com.autodebug.DebugDepth
            class Subject {
              @AutoDebug(tag = "Subject", depth = DebugDepth.BRANCHES)
              fun classify(x: Int): String = if (x > 0) "pos" else "nonpos"
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val classify: Method = clazz.getMethod("classify", Int::class.javaPrimitiveType)

        lines.clear()
        val positive = classify.invoke(instance, 1)
        assertEquals("pos", positive)
        assertTrue(lines.any { it.contains("↦") && it.contains("classify") && it.contains("if") && it.contains("then") }, lines.toString())

        lines.clear()
        val negative = classify.invoke(instance, -1)
        assertEquals("nonpos", negative)
        assertTrue(lines.any { it.contains("↦") && it.contains("classify") && it.contains("else") }, lines.toString())
    }

    @Test
    fun `BRANCHES depth logs when arm`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            import com.autodebug.DebugDepth
            class Subject {
              @AutoDebug(tag = "Subject", depth = DebugDepth.BRANCHES)
              fun pick(x: Int): String = when (x) {
                1 -> "one"
                2 -> "two"
                else -> "other"
              }
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val pick: Method = clazz.getMethod("pick", Int::class.javaPrimitiveType)

        lines.clear()
        val value = pick.invoke(instance, 2)
        assertEquals("two", value)
        assertTrue(lines.any { it.startsWith("⇢ pick(") }, lines.toString())
        assertTrue(lines.any { it.contains("↦") && it.contains("pick") && it.contains("when") }, lines.toString())
        assertTrue(lines.any { it.startsWith("⇠ pick =") && it.contains("two") }, lines.toString())
    }

    private fun compile(source: String) =
        KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("Subject.kt", source))
            compilerPluginRegistrars = listOf(AutoDebugComponentRegistrar())
            commandLineProcessors = listOf(AutoDebugCommandLineProcessor())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()
}
