package com.autodebug.compiler

import com.autodebug.runtime.AutoDebug
import com.autodebug.runtime.AutoDebugConfig
import com.autodebug.runtime.AutoDebugSink
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

@OptIn(ExperimentalCompilerApi::class)
class BoundaryLoggingIrTest {
    private val lines = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        lines.clear()
        AutoDebugConfig.enabled = true
        AutoDebug.sink = AutoDebugSink { _, msg -> lines += msg }
    }

    @Test
    fun `annotated function logs enter and exit`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            class Subject {
              @AutoDebug(tag = "Subject")
              fun greet(name: String): String = "Hello, " + name
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val greet: Method = clazz.getMethod("greet", String::class.java)
        val value = greet.invoke(instance, "Ada")
        assertEquals("Hello, Ada", value)
        assertTrue(lines.any { it.startsWith("⇢ greet(") && it.contains("Ada") }, lines.toString())
        assertTrue(lines.any { it.startsWith("⇠ greet =") && it.contains("Hello, Ada") }, lines.toString())
    }

    @Test
    fun `annotated function logs throw and rethrows`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            class Subject {
              @AutoDebug(tag = "Subject")
              fun boom(): String = error("x")
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val boom: Method = clazz.getMethod("boom")

        var thrown: Throwable? = null
        try {
            boom.invoke(instance)
        } catch (e: Throwable) {
            thrown = e.cause ?: e
        }

        assertTrue(thrown is IllegalStateException, thrown?.toString())
        assertTrue(lines.any { it.contains("threw") && it.contains("x") }, lines.toString())
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
