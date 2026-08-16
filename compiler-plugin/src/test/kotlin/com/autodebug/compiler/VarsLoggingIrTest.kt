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
class VarsLoggingIrTest {
    private val lines = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        lines.clear()
        AutoDebugConfig.enabled = true
        AutoDebug.sink = AutoDebugSink { _, msg -> lines += msg }
    }

    @Test
    fun `BOUNDARY depth does not emit assignment lines for local var`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            import com.autodebug.DebugDepth
            class Subject {
              @AutoDebug(tag = "Subject", depth = DebugDepth.BOUNDARY)
              fun bump(n: Int): Int {
                var local = 0
                local = n
                return local
              }
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val bump: Method = clazz.getMethod("bump", Int::class.javaPrimitiveType)
        val value = bump.invoke(instance, 3)
        assertEquals(3, value)
        assertTrue(lines.any { it.startsWith("⇢ bump(") }, lines.toString())
        assertTrue(lines.any { it.startsWith("⇠ bump =") && it.contains("3") }, lines.toString())
        assertFalse(lines.any { it.contains("↻") }, lines.toString())
    }

    @Test
    fun `BRANCHES depth does not emit assignment lines for local var`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            import com.autodebug.DebugDepth
            class Subject {
              @AutoDebug(tag = "Subject", depth = DebugDepth.BRANCHES)
              fun classify(x: Int): String {
                var label = "unknown"
                label = if (x > 0) "pos" else "nonpos"
                return label
              }
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val classify: Method = clazz.getMethod("classify", Int::class.javaPrimitiveType)
        val value = classify.invoke(instance, 1)
        assertEquals("pos", value)
        assertTrue(lines.any { it.contains("↦") }, lines.toString())
        assertFalse(lines.any { it.contains("↻") }, lines.toString())
    }

    @Test
    fun `VARS depth logs local var assignment`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            import com.autodebug.DebugDepth
            class Subject {
              @AutoDebug(tag = "Subject", depth = DebugDepth.VARS)
              fun bump(n: Int): Int {
                var local = 0
                local = n
                return local
              }
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val bump: Method = clazz.getMethod("bump", Int::class.javaPrimitiveType)
        val value = bump.invoke(instance, 3)
        assertEquals(3, value)
        assertTrue(
            lines.any { it.contains("↻") && it.contains("bump") && it.contains("local") && it.contains("→ 3") },
            lines.toString(),
        )
    }

    @Test
    fun `VARS depth logs this property assignment`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            import com.autodebug.DebugDepth
            class Subject {
              var total = 0
              @AutoDebug(tag = "Subject", depth = DebugDepth.VARS)
              fun bump(n: Int): Int {
                total = total + n
                return total
              }
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val bump: Method = clazz.getMethod("bump", Int::class.javaPrimitiveType)
        val value = bump.invoke(instance, 3)
        assertEquals(3, value)
        assertTrue(
            lines.any { it.contains("↻") && it.contains("bump") && it.contains("total") && it.contains("→ 3") },
            lines.toString(),
        )
    }

    @Test
    fun `VARS depth does not log foreign object property writes`() {
        val result = compile(
            """
            import com.autodebug.AutoDebug
            import com.autodebug.DebugDepth
            class Box(var boxValue: Int)
            class Subject {
              var total = 0
              @AutoDebug(tag = "S", depth = DebugDepth.VARS)
              fun mix(n: Int, other: Box): Int {
                var local = 0
                local = n
                total = total + n
                other.boxValue = n
                return local
              }
            }
            """.trimIndent(),
        )

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val boxClass = result.classLoader.loadClass("Box")
        val subjectClass = result.classLoader.loadClass("Subject")
        val subject = subjectClass.getDeclaredConstructor().newInstance()
        val other = boxClass.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(0)
        val mix: Method = subjectClass.getMethod("mix", Int::class.javaPrimitiveType, boxClass)
        val value = mix.invoke(subject, 5, other)
        assertEquals(5, value)

        val assignmentLines = lines.filter { it.contains("↻") }
        assertTrue(
            assignmentLines.any { it.contains("local") && it.contains("→ 5") },
            assignmentLines.toString(),
        )
        assertTrue(
            assignmentLines.any { it.contains("total") && it.contains("→ 5") },
            assignmentLines.toString(),
        )
        assertFalse(
            assignmentLines.any { it.contains("boxValue") },
            assignmentLines.toString(),
        )
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
