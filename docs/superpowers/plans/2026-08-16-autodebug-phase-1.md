# AutoDebug Phase 1 — BOUNDARY Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Instrument `@AutoDebug` functions so they log enter (args), exit (return + duration), and throw (exception + duration) via the runtime, visible in the Android sample Logcat.

**Architecture:** Extend the existing Kotlin IR transformer (no longer identity) to inject calls into `com.autodebug.runtime.AutoDebug` enter/exit/throw helpers. Runtime formats Hugo-style `⇢`/`⇠` messages with truncation. Gradle plugin passes `enabled=true` only for debug-like compilations; runtime `AutoDebugConfig.enabled` remains a second kill switch.

**Tech Stack:** Existing Phase 0 stack (Kotlin `2.1.21`, AGP `8.9.1`, Gradle `8.13`). Add `com.github.tschuchortdev:kotlin-compile-testing` (or `dev.zacsweers.kctfork:core` if that artifact fails to resolve for 2.1.21) for compiler-plugin tests. Android `Log` used only from the sample app sink (keep `runtime` JVM-only).

**Spec:** `docs/superpowers/specs/2026-08-16-auto-debug-log-design.md` § Phase 1.

## Global Constraints

- Engine remains **Kotlin IR only** (no ASM).
- Phase 1 depth is **BOUNDARY only** — do not instrument `if`/`when` or variable assignments (Phase 2/3).
- Annotation API stays: `@AutoDebug(tag, depth)`, `@AutoDebugSkip`, `DebugDepth`.
- Plugin id / Gradle id: `com.autodebug`; coordinates `com.autodebug:*:0.1.0-SNAPSHOT`.
- Logging must not change method semantics (same return, same thrown type); sink failures swallowed.
- Truncate `toString()` output (default **300** chars) and catch `toString()` failures.
- **Never** add `Co-authored-by:` (or any Cursor co-author) to commits; verify message body before push.
- Do not create commits unless the user asked in-session **or** this plan is executed under an explicit SDD/execution choice that already authorized commits (same as Phase 0 run).
- After changing `compiler-plugin`, always `./gradlew :compiler-plugin:publishToMavenLocal` before sample assemble.
- YAGNI: no Timber, no class-level auto-apply yet (optional only if trivial; prefer function-level in sample), no BRANCHES/VARS.

---

## File structure (create / modify)

```
runtime/
  src/main/kotlin/com/autodebug/runtime/
    AutoDebugConfig.kt          (existing)
    AutoDebugSink.kt            (existing — keep AutoDebug object; extend API)
    AutoDebugMessages.kt        (NEW — format enter/exit/throw + truncate)
  src/test/kotlin/com/autodebug/runtime/
    AutoDebugMessagesTest.kt    (NEW)
    AutoDebugBoundaryApiTest.kt (NEW — enter/exit/throw forwarding)

compiler-plugin/
  build.gradle.kts              (add test deps: compile-testing, annotations, runtime, kotlin-compiler-embeddable for tests)
  src/main/kotlin/com/autodebug/compiler/
    AutoDebugIrTransformer.kt   (REPLACE identity with BOUNDARY inject)
    AutoDebugIrGenerationExtension.kt (pass pluginContext; fix enabled plumbing)
    IrAutoDebugSymbols.kt       (NEW — resolve runtime call symbols)
  src/test/kotlin/com/autodebug/compiler/
    BoundaryLoggingIrTest.kt    (NEW)

gradle-plugin-build/
  src/main/kotlin/com/autodebug/gradle/
    AutoDebugGradlePlugin.kt    (enabled from compilation/build type)
    AutoDebugExtension.kt       (NEW optional: autodebug { enabled })

sample-android/
  src/main/java/com/autodebug/sample/
    Demo.kt                     (expand: greet + failing method)
    MainActivity.kt             (install Android Log sink; trigger demos)
    AndroidLogSink.kt           (NEW)

docs/superpowers/phase-gates.md (mark Phase 1 when green)
README.md                       (Phase 1 status + toolchain versions)
```

---

### Task 1: Runtime message formatting + boundary API

**Files:**
- Create: `runtime/src/main/kotlin/com/autodebug/runtime/AutoDebugMessages.kt`
- Modify: `runtime/src/main/kotlin/com/autodebug/runtime/AutoDebugSink.kt` (extend `AutoDebug` object)
- Create: `runtime/src/test/kotlin/com/autodebug/runtime/AutoDebugMessagesTest.kt`
- Create: `runtime/src/test/kotlin/com/autodebug/runtime/AutoDebugBoundaryApiTest.kt`

**Interfaces:**
- Consumes: existing `AutoDebugConfig`, `AutoDebugSink`, `AutoDebug.log`
- Produces:
  - `AutoDebugMessages.truncate(text: String, maxChars: Int = 300): String`
  - `AutoDebugMessages.formatValue(value: Any?): String`
  - `AutoDebugMessages.enter(method: String, argsDescription: String): String` → `"⇢ method(args)"`
  - `AutoDebugMessages.exit(method: String, resultDescription: String, durationMs: Long): String` → `"⇠ method = result [Nms]"`
  - `AutoDebugMessages.thrown(method: String, throwable: Throwable, durationMs: Long): String` → `"⇠ method threw Type: msg [Nms]"`
  - `AutoDebug.logEnter(tag: String, method: String, argsDescription: String)`
  - `AutoDebug.logExit(tag: String, method: String, result: Any?, durationMs: Long)`
  - `AutoDebug.logThrow(tag: String, method: String, throwable: Throwable, durationMs: Long)`
  - Keep existing `AutoDebug.log(tag, message)`

- [ ] **Step 1: Write failing tests for formatting**

`AutoDebugMessagesTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests — expect FAIL**

Run: `./gradlew :runtime:test --tests com.autodebug.runtime.AutoDebugMessagesTest`  
Expected: unresolved `AutoDebugMessages`.

- [ ] **Step 3: Implement `AutoDebugMessages.kt`**

```kotlin
package com.autodebug.runtime

object AutoDebugMessages {
    const val DEFAULT_MAX_CHARS: Int = 300

    fun truncate(text: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "..."
    }

    fun formatValue(value: Any?): String {
        if (value == null) return "null"
        return try {
            truncate(value.toString())
        } catch (t: Throwable) {
            truncate("<toString failed: ${t.javaClass.simpleName}>")
        }
    }

    fun enter(method: String, argsDescription: String): String =
        if (argsDescription.isEmpty()) "⇢ $method()" else "⇢ $method($argsDescription)"

    fun exit(method: String, resultDescription: String, durationMs: Long): String =
        "⇠ $method = $resultDescription [${durationMs}ms]"

    fun thrown(method: String, throwable: Throwable, durationMs: Long): String {
        val type = throwable.javaClass.simpleName
        val msg = truncate(throwable.message ?: "")
        return "⇠ $method threw $type: $msg [${durationMs}ms]"
    }
}
```

- [ ] **Step 4: Write failing boundary API tests**

```kotlin
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
}
```

- [ ] **Step 5: Extend `AutoDebug` object**

In `AutoDebugSink.kt`, add:

```kotlin
@JvmStatic
fun logEnter(tag: String, method: String, argsDescription: String) {
    log(tag, AutoDebugMessages.enter(method, argsDescription))
}

@JvmStatic
fun logExit(tag: String, method: String, result: Any?, durationMs: Long) {
    log(tag, AutoDebugMessages.exit(method, AutoDebugMessages.formatValue(result), durationMs))
}

@JvmStatic
fun logThrow(tag: String, method: String, throwable: Throwable, durationMs: Long) {
    log(tag, AutoDebugMessages.thrown(method, throwable, durationMs))
}
```

- [ ] **Step 6: Run all runtime tests — expect PASS**

Run: `./gradlew :runtime:test`  
Expected: all green (existing 3 + new tests).

- [ ] **Step 7: Commit if authorized**

Message: `feat: add AutoDebug boundary log formatting API`  
Verify commit body has **no** `Co-authored-by`.

---

### Task 2: IR BOUNDARY instrumentation

**Files:**
- Create: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/IrAutoDebugSymbols.kt`
- Modify: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/AutoDebugIrTransformer.kt`
- Modify: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/AutoDebugIrGenerationExtension.kt`
- Modify: `compiler-plugin/build.gradle.kts` (ensure runtime is available at compile of **user** code only — plugin resolves symbols by FQN from app classpath; plugin itself needs `compileOnly`/`testImplementation` of runtime for tests later)

**Interfaces:**
- Consumes: `IrPluginContext`, annotation `com.autodebug.AutoDebug`, runtime FQNs below
- Produces: rewritten `IrSimpleFunction` body that:
  1. Builds args description string from parameter names + values (`this` optional skip for Phase 1 unless easy)
  2. Calls `AutoDebug.logEnter(tag, methodName, argsDescription)`
  3. Records `start = System.currentTimeMillis()`
  4. Executes original body inside try
  5. On return: `logExit` then return value
  6. On throw: `logThrow` then rethrow

**Runtime FQNs (exact):**
- `com.autodebug.runtime.AutoDebug.logEnter`
- `com.autodebug.runtime.AutoDebug.logExit`
- `com.autodebug.runtime.AutoDebug.logThrow`

**Annotation reading:**
- Read `tag` from `@AutoDebug`; if empty, use containing class name or file name (`declaration.parent` / `file.name`).
- Ignore `depth` other than noting BOUNDARY is default; if somehow only BRANCHES/VARS later, Phase 1 still applies BOUNDARY behavior for all depths that include boundary (i.e. always log boundary when annotated — Phase 2 will gate extra).

- [ ] **Step 1: Fix extension enabled plumbing + pass `IrPluginContext`**

`AutoDebugIrGenerationExtension.kt`:

```kotlin
package com.autodebug.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class AutoDebugIrGenerationExtension(
    private val enabled: Boolean,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        if (!enabled) return
        val symbols = IrAutoDebugSymbols(pluginContext)
        moduleFragment.transformChildrenVoid(AutoDebugIrTransformer(symbols))
    }
}
```

- [ ] **Step 2: Implement symbol lookup**

`IrAutoDebugSymbols.kt` — resolve single matching `logEnter` / `logExit` / `logThrow` via `pluginContext.referenceFunctions(CallableId(...))` or `FqName` lookup used by Kotlin 2.1.21. Prefer:

```kotlin
package com.autodebug.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class IrAutoDebugSymbols(context: IrPluginContext) {
    private val owner = FqName("com.autodebug.runtime.AutoDebug")

    val logEnter: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(owner, Name.identifier("logEnter")),
    ).single()

    val logExit: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(owner, Name.identifier("logExit")),
    ).single()

    val logThrow: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(owner, Name.identifier("logThrow")),
    ).single()

    val currentTimeMillis: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(FqName("java.lang.System"), Name.identifier("currentTimeMillis")),
    ).single { it.owner.valueParameters.isEmpty() }
}
```

If `CallableId` / `referenceFunctions` signatures differ on 2.1.21, adjust to the working overload — keep the same three AutoDebug targets.

- [ ] **Step 3: Implement transformer body rewrite**

Replace `AutoDebugIrTransformer` with logic equivalent to:

```kotlin
// Pseudocode for the IR transform (implement with DeclarationIrBuilder / IrStatementsBuilder):

override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
    if (!declaration.hasAnnotation(autoDebugFqName)) {
        return super.visitSimpleFunction(declaration)
    }
    if (declaration.body == null || declaration.isFakeOverride || declaration.isInline) {
        return super.visitSimpleFunction(declaration)
    }
    // skip if also @AutoDebugSkip when class-level lands; Phase 1: function-level only

    val tag = readTag(declaration)
    val methodName = declaration.name.asString()
    val body = declaration.body as? IrBlockBody ?: return super.visitSimpleFunction(declaration)

    // Rebuild body:
    // val start = System.currentTimeMillis()
    // AutoDebug.logEnter(tag, methodName, argsString)
    // try {
    //   ... original statements with return sites wrapped ...
    // } catch (t: Throwable) {
    //   AutoDebug.logThrow(tag, methodName, t, System.currentTimeMillis() - start)
    //   throw t
    // }

    return declaration
}
```

**Return wrapping:** For each `IrReturn` targeting this function, replace with:

```kotlin
val resultTmp = irTemporary(returnExpr)
+irCall(logExit).apply { /* tag, method, resultTmp, duration */ }
+irReturn(irGet(resultTmp))
```

**Args string:** Join `name=value` for each value parameter (exclude extension receivers if awkward — include if straightforward). Build via string concatenation IR or a single `StringBuilder` — simplest: concatenate constants and `toString` via `AutoDebugMessages.formatValue` by calling a small runtime helper:

Add if needed (preferred for simpler IR):

```kotlin
// In AutoDebug object:
@JvmStatic
fun describeArgs(names: Array<String>, values: Array<Any?>): String
```

Then IR passes name array + boxed values array. Implement `describeArgs` in Task 1 follow-up if transformer needs it — **include `describeArgs` in Task 1 if not already added** before finishing Task 2.

Add to Task 1 deliverable if missing when starting Task 2:

```kotlin
@JvmStatic
fun describeArgs(names: Array<String>, values: Array<out Any?>): String {
    require(names.size == values.size)
    return names.indices.joinToString { i ->
        "${names[i]}=${AutoDebugMessages.formatValue(values[i])}"
    }
}
```

- [ ] **Step 4: Compile plugin**

Run: `./gradlew :compiler-plugin:compileKotlin`  
Expected: SUCCESS. Fix IR builder API mismatches without changing behavior contract.

- [ ] **Step 5: Publish plugin locally**

Run: `./gradlew :compiler-plugin:publishToMavenLocal :runtime:publishToMavenLocal`

- [ ] **Step 6: Commit if authorized**

Message: `feat: inject BOUNDARY enter/exit/throw logging in IR`

---

### Task 3: Compiler-plugin automated test

**Files:**
- Modify: `compiler-plugin/build.gradle.kts`
- Create: `compiler-plugin/src/test/kotlin/com/autodebug/compiler/BoundaryLoggingIrTest.kt`

**Interfaces:**
- Consumes: plugin registrar + runtime + annotations on classpath of compiled snippet
- Produces: JUnit test that compiles a Kotlin snippet with the plugin, runs `greet`, asserts sink received enter + exit lines

- [ ] **Step 1: Add test dependencies**

```kotlin
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(project(":annotations"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(project(":annotations"))
    testImplementation(project(":runtime"))
    // Prefer whichever resolves on Maven Central for Kotlin 2.1.21:
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.6.0")
    // If unresolved, switch to: "dev.zacsweers.kctfork:core:0.7.0" (or latest that supports 2.1.21)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write failing/integration test**

```kotlin
package com.autodebug.compiler

import com.autodebug.runtime.AutoDebug
import com.autodebug.runtime.AutoDebugConfig
import com.autodebug.runtime.AutoDebugSink
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

@OptIn(ExperimentalCompilerApi::class)
class BoundaryLoggingIrTest {
    @Test
    fun `annotated function logs enter and exit`() {
        val lines = mutableListOf<String>()
        AutoDebugConfig.enabled = true
        AutoDebug.sink = AutoDebugSink { _, msg -> lines += msg }

        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "Subject.kt",
                    """
                    import com.autodebug.AutoDebug
                    class Subject {
                      @AutoDebug(tag = "Subject")
                      fun greet(name: String): String = "Hello, " + name
                    }
                    """.trimIndent(),
                ),
            )
            compilerPluginRegistrars = listOf(AutoDebugComponentRegistrar())
            commandLineProcessors = listOf(AutoDebugCommandLineProcessor())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK, result.messages)

        val clazz = result.classLoader.loadClass("Subject")
        val instance = clazz.getDeclaredConstructor().newInstance()
        val greet: Method = clazz.getMethod("greet", String::class.java)
        val value = greet.invoke(instance, "Ada")
        assertTrue(value == "Hello, Ada")
        assertTrue(lines.any { it.startsWith("⇢ greet(") && it.contains("Ada") }, lines.toString())
        assertTrue(lines.any { it.startsWith("⇠ greet =") && it.contains("Hello, Ada") }, lines.toString())
    }
}
```

Wire plugin options `enabled=true` however compile-testing expects (constructor config or `pluginOptions`). If registrar reads default `enabled=true`, empty options are fine.

- [ ] **Step 3: Run test — fix until PASS**

Run: `./gradlew :compiler-plugin:test --tests com.autodebug.compiler.BoundaryLoggingIrTest`

- [ ] **Step 4: Add throw-path test** (same file)

Compile a `@AutoDebug fun boom(): String = error("x")`, invoke, catch, assert `lines` contain `threw` and exception still surfaces to caller.

- [ ] **Step 5: Commit if authorized**

Message: `test: verify BOUNDARY IR logging via compile-testing`

---

### Task 4: Gradle debug-only enable + sample Logcat demo

**Files:**
- Modify: `gradle-plugin-build/src/main/kotlin/com/autodebug/gradle/AutoDebugGradlePlugin.kt`
- Create: `sample-android/src/main/java/com/autodebug/sample/AndroidLogSink.kt`
- Modify: `sample-android/src/main/java/com/autodebug/sample/Demo.kt`
- Modify: `sample-android/src/main/java/com/autodebug/sample/MainActivity.kt`

**Interfaces:**
- Consumes: Android `Log.d`
- Produces: sample that on launch logs enter/exit for `greet` and enter/throw for a failing method

- [ ] **Step 1: Enable plugin only for debug compilations**

In `applyToCompilation`:

```kotlin
override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    return project.provider {
        val name = kotlinCompilation.name.lowercase()
        // debug / debuggable variants → true; release → false
        val enabled = name.contains("debug") && !name.contains("release")
        listOf(SubpluginOption(key = "enabled", value = enabled.toString()))
    }
}
```

(Adjust if Android compilation names differ; goal: release has `enabled=false`.)

- [ ] **Step 2: Android sink + Demo methods**

`AndroidLogSink.kt`:

```kotlin
package com.autodebug.sample

import android.util.Log
import com.autodebug.runtime.AutoDebugSink

class AndroidLogSink : AutoDebugSink {
    override fun log(tag: String, message: String) {
        Log.d(tag, message)
    }
}
```

`Demo.kt`:

```kotlin
package com.autodebug.sample

import com.autodebug.AutoDebug

class Demo {
    @AutoDebug(tag = "Demo")
    fun greet(name: String): String = "Hello, $name"

    @AutoDebug(tag = "Demo")
    fun fail(message: String): String {
        error(message)
    }
}
```

`MainActivity.kt` — install sink, call `greet("AutoDebug")`, call `fail` inside try/catch, show greet result in TextView.

- [ ] **Step 3: Publish + assemble**

```bash
./gradlew :annotations:publishToMavenLocal :runtime:publishToMavenLocal :compiler-plugin:publishToMavenLocal
./gradlew :sample-android:assembleDebug
./gradlew :annotations:test :runtime:test :compiler-plugin:test
```

Expected: all SUCCESS.

- [ ] **Step 4: Manual Logcat check** (on emulator/device if available)

```bash
./gradlew :sample-android:installDebug
adb logcat -s Demo:D | head -40
```

Expected lines resembling:
```
Demo  ⇢ greet(name=AutoDebug)
Demo  ⇠ greet = Hello, AutoDebug [Nms]
Demo  ⇢ fail(message=…)
Demo  ⇠ fail threw … [Nms]
```

If no device: document that assemble + compiler-plugin tests satisfy automated gate; Logcat remains checklist item.

- [ ] **Step 5: Commit if authorized**

Message: `feat: wire Android sample Logcat sink for BOUNDARY logs`

---

### Task 5: Docs + Phase 1 gate

**Files:**
- Modify: `docs/superpowers/phase-gates.md`
- Modify: `README.md`

- [ ] **Step 1: Update phase-gates.md**

Add:

```markdown
## Phase 1

- [ ] `./gradlew :runtime:test :compiler-plugin:test` passes (BOUNDARY coverage)
- [ ] `./gradlew :sample-android:assembleDebug` passes after publishToMavenLocal
- [ ] Sample `@AutoDebug` methods produce enter/exit (and throw) logs (Logcat or compile-testing evidence)
- [ ] Release/debug enable: release compilations get `enabled=false` (or documented equivalent)

Only then start Phase 2.
```

Mark checkboxes `[x]` only after Step 2 verification evidence exists.

- [ ] **Step 2: Run full Phase 1 verification**

```bash
./gradlew :annotations:publishToMavenLocal :runtime:publishToMavenLocal :compiler-plugin:publishToMavenLocal
./gradlew :annotations:test :runtime:test :compiler-plugin:test :sample-android:assembleDebug
```

Record exit code and key test names in the task report / commit message body (still no co-author).

- [ ] **Step 3: README**

- Set Phase 1 checkbox `[x]`, leave 2–3 unchecked.
- Add toolchain line: Kotlin `2.1.21`, AGP `8.9.1`, Gradle `8.13`.
- Note publishToMavenLocal prerequisite.

- [ ] **Step 4: Commit if authorized**

Message: `docs: mark Phase 1 BOUNDARY gate complete`

---

## Phase 1 exit criteria

1. Runtime formats enter/exit/throw; unit tests green.
2. IR injects calls; `BoundaryLoggingIrTest` proves enter/exit (+ throw).
3. Sample assembles; Android sink installed; demo methods annotated.
4. Debug compilations enable plugin; release disables (or equivalent).
5. `phase-gates.md` + README updated.
6. No `Co-authored-by` in new commits.

**Do not start Phase 2** until the above are checked.

---

## Plan self-review

1. **Spec coverage:** Enter/args, exit/return/duration, throw/rethrow, Hugo arrows, enable flags, tests + sample — tasked. Class-level annotate deferred (YAGNI). BRANCHES/VARS excluded.
2. **Placeholders:** No TBD; compile-testing artifact has a concrete fallback.
3. **Consistency:** Runtime FQNs match IR symbol lookup; tag/method/args contracts shared.
4. **Phase 0 minors addressed:** enabled plumbing fixed in Task 2; README versions in Task 5; publish still manual but documented.
