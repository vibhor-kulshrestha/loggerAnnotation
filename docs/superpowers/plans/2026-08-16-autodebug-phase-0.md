# AutoDebug Phase 0 — Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a multi-module Gradle project where `@AutoDebug` exists, a Kotlin IR compiler plugin registers and runs an identity pass, and `:sample-android:assembleDebug` succeeds.

**Architecture:** Kotlin IR compiler plugin wired through a Gradle `KotlinCompilerPluginSupportPlugin`, plus `annotations` and `runtime` libraries consumed by an Android sample. No logging injection yet — that is Phase 1. Vendor trees (`Cabret-Log-master`, `hugo-master`, `Hunter-master`) stay as read-only references during Phase 0; do not copy their outdated plugin APIs wholesale.

**Tech Stack:** Kotlin `2.1.21`, Android Gradle Plugin `8.9.1`, Gradle `8.13`, `compileSdk`/`targetSdk` `35`, `minSdk` `24`, `kotlin-compiler-embeddable` matching Kotlin version, JUnit 5 for JVM unit tests.

**Spec:** `docs/superpowers/specs/2026-08-16-auto-debug-log-design.md` (Phase 0 section only).

## Global Constraints

- Engine is **Kotlin IR only** (no ASM weave in this project).
- Modules required: `annotations`, `runtime`, `compiler-plugin`, `gradle-plugin`, `sample-android`.
- Annotation names locked: `@AutoDebug`, `DebugDepth` (`BOUNDARY`, `BRANCHES`, `VARS`), `@AutoDebugSkip`.
- Group / coordinates: `com.autodebug` / artifact names below / version `0.1.0-SNAPSHOT`.
- Compiler plugin id: `com.autodebug`.
- Gradle plugin id: `com.autodebug`.
- Phase 0 must **not** implement enter/exit logging (Phase 1).
- Do **not** delete vendor folders until after Phase 1 extraction notes exist (Phase 0 may read them; do not depend on them as Gradle modules).
- Do **not** create git commits unless the user explicitly asks in the session; skip commit steps or stop before `git commit` and report ready-to-commit files instead.
- Prefer small focused files; YAGNI — no Maven Central publish, no Timber, no branch/var IR yet.

---

## File structure (create in Phase 0)

```
loggerAnnotation/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle/libs.versions.toml
  gradle/wrapper/…          (via gradle wrapper)
  README.md
  annotations/
    build.gradle.kts
    src/main/kotlin/com/autodebug/AutoDebug.kt
    src/main/kotlin/com/autodebug/DebugDepth.kt
    src/main/kotlin/com/autodebug/AutoDebugSkip.kt
    src/test/kotlin/com/autodebug/DebugDepthTest.kt
  runtime/
    build.gradle.kts
    src/commonMain/…        (optional — prefer JVM+Android split below)
    src/main/kotlin/com/autodebug/runtime/AutoDebugConfig.kt
    src/main/kotlin/com/autodebug/runtime/AutoDebugSink.kt
    src/androidMain/kotlin/… OR src/main with android variant
    src/test/kotlin/com/autodebug/runtime/AutoDebugConfigTest.kt
  compiler-plugin/
    build.gradle.kts
    src/main/kotlin/com/autodebug/compiler/AutoDebugCommandLineProcessor.kt
    src/main/kotlin/com/autodebug/compiler/AutoDebugComponentRegistrar.kt
    src/main/kotlin/com/autodebug/compiler/AutoDebugIrGenerationExtension.kt
    src/main/kotlin/com/autodebug/compiler/AutoDebugIrTransformer.kt
    src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
    src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
  gradle-plugin/
    build.gradle.kts
    src/main/kotlin/com/autodebug/gradle/AutoDebugGradlePlugin.kt
  sample-android/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/autodebug/sample/MainActivity.kt
    src/main/java/com/autodebug/sample/Demo.kt
```

**Runtime shape for Phase 0:** Keep `runtime` as a plain Kotlin JVM library with Android-free APIs (`AutoDebugConfig`, `AutoDebugSink`). Sample uses `Log` directly later in Phase 1 for the Android sink; Phase 0 only needs the module to compile and be a dependency. (Avoid KMP complexity in Phase 0.)

**Gradle plugin wiring:** Root `settings.gradle.kts` uses `pluginManagement { includeBuild("gradle-plugin") }` only if `gradle-plugin` is a nested included build. To keep one settings file, implement `gradle-plugin` as a **normal subproject** and apply it to `sample-android` via:

```kotlin
plugins {
    id("com.autodebug")
}
```

with `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("gradle-plugin-build")
}
```

**Chosen wiring for this plan (simplest reliable local setup):**

1. Nested included build at `gradle-plugin-build/` containing the Gradle plugin project.
2. That included build depends on `:compiler-plugin` coordinates published to `mavenLocal` **or** uses dependency substitution.

**Even simpler (use this):** single multi-project build; `sample-android` depends on:

```kotlin
dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
```

and still create `gradle-plugin` subproject that implements `KotlinCompilerPluginSupportPlugin` for the real UX, applied via included build after `mavenLocal` publish of `compiler-plugin`.

**Final decision for Phase 0 tasks below:**

- Use **included build** `gradle-plugin-build` that publishes/resolves `com.autodebug:compiler-plugin`.
- Root project publishes `compiler-plugin`, `annotations`, `runtime` with `maven-publish` to `mavenLocal` as part of the sample build dependency chain **OR** use composite substitution.

To avoid mavenLocal fragility in the first green build, Task 5 will wire sample with **both**:

1. `id("com.autodebug")` from included build (passes `-Pplugin` options).
2. Fallback documented: `kotlinCompilerPluginClasspath(project(":compiler-plugin"))` if included-build resolution fails during bring-up.

Implementers must get **one** of these working; prefer the Gradle plugin path. If stuck >30 minutes on includeBuild, ship the classpath fallback, leave a README note, and still keep the `gradle-plugin` sources compiling.

---

### Task 1: Root Gradle skeleton + wrapper

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `.gitignore`

**Interfaces:**
- Consumes: nothing
- Produces: multi-project Gradle root ready for subprojects `annotations`, `runtime`, `compiler-plugin`, `sample-android`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
.gradle/
build/
**/build/
.idea/
*.iml
.DS_Store
local.properties
captures/
.cxx/
*.apk
*.ap_
*.dex
.kotlin/
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
kotlin = "2.1.21"
agp = "8.9.1"
junit = "5.11.4"
compileSdk = "35"
minSdk = "24"

[libraries]
kotlin-compiler-embeddable = { module = "org.jetbrains.kotlin:kotlin-compiler-embeddable", version.ref = "kotlin" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.parallel=true
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 4: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "loggerAnnotation"

include(":annotations")
include(":runtime")
include(":compiler-plugin")
include(":sample-android")
```

- [ ] **Step 5: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
}
```

- [ ] **Step 6: Generate Gradle wrapper**

Run from project root (requires network once):

```bash
gradle wrapper --gradle-version 8.13
```

If system `gradle` is missing, install via SDKMAN/Homebrew or copy wrapper jars from another project, then:

```bash
./gradlew --version
```

Expected: Gradle `8.13`, JVM 17+.

- [ ] **Step 7: Verify settings parse**

Run: `./gradlew projects`  
Expected: lists `annotations`, `runtime`, `compiler-plugin`, `sample-android` (modules may fail configuration until later tasks create `build.gradle.kts` — if so, proceed to Task 2 immediately after creating empty module dirs).

---

### Task 2: `annotations` module

**Files:**
- Create: `annotations/build.gradle.kts`
- Create: `annotations/src/main/kotlin/com/autodebug/DebugDepth.kt`
- Create: `annotations/src/main/kotlin/com/autodebug/AutoDebug.kt`
- Create: `annotations/src/main/kotlin/com/autodebug/AutoDebugSkip.kt`
- Create: `annotations/src/test/kotlin/com/autodebug/DebugDepthTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `enum class DebugDepth { BOUNDARY, BRANCHES, VARS }`
  - `annotation class AutoDebug(val tag: String = "", val depth: DebugDepth = DebugDepth.BOUNDARY)`
  - `annotation class AutoDebugSkip`

- [ ] **Step 1: Write the failing test**

`annotations/src/test/kotlin/com/autodebug/DebugDepthTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Create `annotations/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.autodebug"
version = "0.1.0-SNAPSHOT"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :annotations:test --tests com.autodebug.DebugDepthTest`  
Expected: FAIL ( unresolved `DebugDepth` / `AutoDebug` ).

- [ ] **Step 4: Implement annotations**

`DebugDepth.kt`:

```kotlin
package com.autodebug

enum class DebugDepth {
    BOUNDARY,
    BRANCHES,
    VARS,
}
```

`AutoDebug.kt`:

```kotlin
package com.autodebug

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AutoDebug(
    val tag: String = "",
    val depth: DebugDepth = DebugDepth.BOUNDARY,
)
```

`AutoDebugSkip.kt`:

```kotlin
package com.autodebug

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class AutoDebugSkip
```

- [ ] **Step 5: Run tests to verify pass**

Run: `./gradlew :annotations:test`  
Expected: BUILD SUCCESSFUL, both tests PASS.

- [ ] **Step 6: Commit only if user asked**

If user requested commits: `git add annotations gradle* settings.gradle.kts build.gradle.kts .gitignore && git commit -m "feat: add AutoDebug annotations module"`.  
Otherwise: skip.

---

### Task 3: `runtime` module (config + sink interface)

**Files:**
- Create: `runtime/build.gradle.kts`
- Create: `runtime/src/main/kotlin/com/autodebug/runtime/AutoDebugConfig.kt`
- Create: `runtime/src/main/kotlin/com/autodebug/runtime/AutoDebugSink.kt`
- Create: `runtime/src/test/kotlin/com/autodebug/runtime/AutoDebugConfigTest.kt`

**Interfaces:**
- Consumes: nothing from annotations (runtime stays annotation-free)
- Produces:
  - `object AutoDebugConfig { @JvmStatic var enabled: Boolean = true }`
  - `fun interface AutoDebugSink { fun log(tag: String, message: String) }`
  - `object AutoDebug { var sink: AutoDebugSink; fun log(tag: String, message: String) }`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Create `runtime/build.gradle.kts`** (same pattern as annotations)

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.autodebug"
version = "0.1.0-SNAPSHOT"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 3: Run test — expect FAIL**

Run: `./gradlew :runtime:test --tests com.autodebug.runtime.AutoDebugConfigTest`

- [ ] **Step 4: Implement runtime**

`AutoDebugConfig.kt`:

```kotlin
package com.autodebug.runtime

object AutoDebugConfig {
    @JvmStatic
    var enabled: Boolean = true
}
```

`AutoDebugSink.kt`:

```kotlin
package com.autodebug.runtime

fun interface AutoDebugSink {
    fun log(tag: String, message: String)
}

object AutoDebug {
    @JvmStatic
    var sink: AutoDebugSink = AutoDebugSink { tag, message ->
        println("$tag: $message")
    }

    @JvmStatic
    fun log(tag: String, message: String) {
        if (!AutoDebugConfig.enabled) return
        try {
            sink.log(tag, message)
        } catch (_: Throwable) {
            // never break app logic because of debug logging
        }
    }
}
```

- [ ] **Step 5: Run tests — expect PASS**

Run: `./gradlew :runtime:test`

---

### Task 4: `compiler-plugin` identity IR pass

**Files:**
- Create: `compiler-plugin/build.gradle.kts`
- Create: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/AutoDebugCommandLineProcessor.kt`
- Create: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/AutoDebugComponentRegistrar.kt`
- Create: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/AutoDebugIrGenerationExtension.kt`
- Create: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/AutoDebugIrTransformer.kt`
- Create: `compiler-plugin/src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor`
- Create: `compiler-plugin/src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`

**Interfaces:**
- Consumes: annotation FQNs as strings `"com.autodebug.AutoDebug"` (compileOnly on annotations optional; prefer string FQNs to avoid embedding annotations in compiler classpath wrongly — **do** add `compileOnly(project(":annotations"))` for safer refactories if desired)
- Produces: ServiceLoader-registered plugin id `com.autodebug` that registers `IrGenerationExtension` walking annotated functions without mutating bodies in Phase 0

Reference while coding (read-only): `Cabret-Log-master/cabret-compiler-runtime/.../CabretIrGenerationExtension.kt` and `CabretLogTransformer.kt` — **do not copy** old `ComponentRegistrar(project, …)` APIs; use `CompilerPluginRegistrar` + K2.

- [ ] **Step 1: Create `compiler-plugin/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.autodebug"
version = "0.1.0-SNAPSHOT"

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(project(":annotations"))
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 2: Implement CommandLineProcessor**

```kotlin
package com.autodebug.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

object AutoDebugConfigurationKeys {
    val ENABLED: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("com.autodebug.enabled")
}

@OptIn(ExperimentalCompilerApi::class)
class AutoDebugCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "com.autodebug"

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "enabled",
            valueDescription = "<true|false>",
            description = "Enable AutoDebug IR instrumentation",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            "enabled" -> configuration.put(AutoDebugConfigurationKeys.ENABLED, value.toBooleanStrict())
            else -> error("Unknown plugin option: ${option.optionName}")
        }
    }
}
```

- [ ] **Step 3: Implement identity IR transformer + extension + registrar**

`AutoDebugIrTransformer.kt`:

```kotlin
package com.autodebug.compiler

import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

/**
 * Phase 0: detect @AutoDebug and leave the body unchanged.
 * Phase 1 will inject enter/exit logging here.
 */
class AutoDebugIrTransformer(
    private val enabled: Boolean,
) : IrElementTransformerVoid() {

    private val autoDebugFqName = FqName("com.autodebug.AutoDebug")

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrFunction {
        if (enabled && declaration.hasAnnotation(autoDebugFqName)) {
            // Identity: intentionally no body mutation in Phase 0.
        }
        return super.visitSimpleFunction(declaration)
    }
}
```

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
        moduleFragment.transformChildrenVoid(AutoDebugIrTransformer(enabled = true))
    }
}
```

`AutoDebugComponentRegistrar.kt`:

```kotlin
package com.autodebug.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class AutoDebugComponentRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override val pluginId: String = "com.autodebug"

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val enabled = configuration.get(AutoDebugConfigurationKeys.ENABLED, true)
        IrGenerationExtension.registerExtension(AutoDebugIrGenerationExtension(enabled))
    }
}
```

- [ ] **Step 4: Add ServiceLoader resources**

`META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor`:

```text
com.autodebug.compiler.AutoDebugCommandLineProcessor
```

`META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`:

```text
com.autodebug.compiler.AutoDebugComponentRegistrar
```

- [ ] **Step 5: Compile the plugin**

Run: `./gradlew :compiler-plugin:compileKotlin`  
Expected: BUILD SUCCESSFUL.  
If IR APIs differ slightly on 2.1.21 (`hasAnnotation` import path, `transformChildrenVoid`), adjust imports until compile succeeds — keep identity behavior.

---

### Task 5: `gradle-plugin` + sample wiring

**Files:**
- Create: `gradle-plugin-build/settings.gradle.kts`
- Create: `gradle-plugin-build/build.gradle.kts`
- Create: `gradle-plugin-build/src/main/kotlin/com/autodebug/gradle/AutoDebugGradlePlugin.kt`
- Modify: root `settings.gradle.kts` (`pluginManagement.includeBuild`)
- Create: `sample-android/build.gradle.kts`
- Create: `sample-android/src/main/AndroidManifest.xml`
- Create: `sample-android/src/main/java/com/autodebug/sample/MainActivity.kt`
- Create: `sample-android/src/main/java/com/autodebug/sample/Demo.kt`

**Interfaces:**
- Consumes: compiler plugin id `com.autodebug`; artifact `com.autodebug:compiler-plugin:0.1.0-SNAPSHOT`
- Produces: Gradle plugin id `com.autodebug` applying subplugin options `enabled=true|false`

- [ ] **Step 1: Publish compiler-plugin to mavenLocal from root**

Add to `compiler-plugin/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.autodebug"
            artifactId = "compiler-plugin"
            version = "0.1.0-SNAPSHOT"
        }
    }
}
```

Also add the same `maven-publish` blocks to `annotations` and `runtime` with artifactIds `annotations` and `runtime`.

Run: `./gradlew :annotations:publishToMavenLocal :runtime:publishToMavenLocal :compiler-plugin:publishToMavenLocal`  
Expected: SUCCESS.

- [ ] **Step 2: Create included build `gradle-plugin-build`**

`gradle-plugin-build/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }
}

rootProject.name = "autodebug-gradle-plugin-build"
```

`gradle-plugin-build/build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.autodebug"
version = "0.1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.1.21")
}

gradlePlugin {
    plugins {
        create("autodebug") {
            id = "com.autodebug"
            implementationClass = "com.autodebug.gradle.AutoDebugGradlePlugin"
        }
    }
}
```

`AutoDebugGradlePlugin.kt`:

```kotlin
package com.autodebug.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class AutoDebugGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        // Extension point for future: autodebug { enabled.set(true) }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "com.autodebug"

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = "com.autodebug",
            artifactId = "compiler-plugin",
            version = "0.1.0-SNAPSHOT",
        )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            listOf(SubpluginOption(key = "enabled", value = "true"))
        }
    }
}
```

- [ ] **Step 3: Hook included build in root `settings.gradle.kts`**

At top of `pluginManagement`:

```kotlin
pluginManagement {
    includeBuild("gradle-plugin-build")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}
```

- [ ] **Step 4: Create Android sample**

`sample-android/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.autodebug")
}

android {
    namespace = "com.autodebug.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autodebug.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))
}
```

`AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="AutoDebug Sample"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`Demo.kt`:

```kotlin
package com.autodebug.sample

import com.autodebug.AutoDebug

class Demo {
    @AutoDebug(tag = "Demo")
    fun greet(name: String): String = "Hello, $name"
}
```

`MainActivity.kt`:

```kotlin
package com.autodebug.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this)
        text.text = Demo().greet("AutoDebug")
        setContentView(text)
    }
}
```

- [ ] **Step 5: Fallback if plugin artifact resolution fails**

If `./gradlew :sample-android:assembleDebug` fails resolving `com.autodebug:compiler-plugin`, add temporarily to `sample-android/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))
    "kotlinCompilerPluginClasspath"(project(":compiler-plugin"))
}
```

Keep the `id("com.autodebug")` plugin applied if options still work; if not, remove plugin id temporarily and document in README. Prefer fixing mavenLocal publish + includeBuild before accepting fallback long-term.

- [ ] **Step 6: Phase 0 pass gate**

Run:

```bash
./gradlew :annotations:publishToMavenLocal :runtime:publishToMavenLocal :compiler-plugin:publishToMavenLocal
./gradlew :sample-android:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and an APK under `sample-android/build/outputs/apk/debug/`.

Also run: `./gradlew :annotations:test :runtime:test`  
Expected: PASS.

---

### Task 6: README + Phase 0 checklist

**Files:**
- Create: `README.md`
- Create: `docs/superpowers/phase-gates.md` (short living checklist)

- [ ] **Step 1: Write root `README.md`**

```markdown
# AutoDebug

Kotlin annotation + compiler plugin for progressive debug logging on Android.

## Phase status

- [x] Phase 0 — Scaffold (plugin applies, sample assembles)
- [ ] Phase 1 — BOUNDARY logs
- [ ] Phase 2 — BRANCHES
- [ ] Phase 3 — VARS

## Modules

| Module | Role |
|---|---|
| `annotations` | `@AutoDebug`, `DebugDepth`, `@AutoDebugSkip` |
| `runtime` | Enable flag + sink |
| `compiler-plugin` | Kotlin IR plugin |
| `gradle-plugin-build` | Gradle plugin `com.autodebug` |
| `sample-android` | Manual verification app |

## Build

```bash
./gradlew :annotations:publishToMavenLocal :runtime:publishToMavenLocal :compiler-plugin:publishToMavenLocal
./gradlew :sample-android:assembleDebug
```

## Spec / plans

- Spec: `docs/superpowers/specs/2026-08-16-auto-debug-log-design.md`
- Phase 0 plan: `docs/superpowers/plans/2026-08-16-autodebug-phase-0.md`

Vendor reference trees (`Cabret-Log-master`, `hugo-master`, `Hunter-master`) are temporary; delete after Phase 1 extraction notes.
```

- [ ] **Step 2: Write `docs/superpowers/phase-gates.md`**

```markdown
# Phase gates

## Phase 0

- [ ] `./gradlew :annotations:test :runtime:test` passes
- [ ] `./gradlew :sample-android:assembleDebug` passes
- [ ] README documents modules

Only then start Phase 1 plan/implementation.
```

- [ ] **Step 3: Mark gate complete in phase-gates.md when verified**

---

## Phase 0 exit criteria

All must be true:

1. `:annotations:test` and `:runtime:test` green.
2. `:sample-android:assembleDebug` green with `@AutoDebug` present on `Demo.greet`.
3. Compiler plugin ServiceLoader + IR extension register; body still uninstrumented.
4. README exists.

**Do not start Phase 1** until the above are checked off.

---

## Follow-on (out of this plan)

- Phase 1 plan: inject enter/args/return/duration/throw via `AutoDebugIrTransformer` + runtime Android Log sink.
- Extract log-line style from Hugo; IR enter/exit patterns from Cabret (modernized).
- Delete vendor directories after short `docs/references.md`.

---

## Plan self-review

1. **Spec coverage (Phase 0):** Multi-module layout, annotation stub, identity IR, sample apply plugin, assembleDebug gate, README — all tasked. Phase 1+ explicitly excluded.
2. **Placeholders:** No TBD steps; fallback path for Gradle wiring is concrete.
3. **Type consistency:** Plugin id `com.autodebug`, coordinates `com.autodebug:*:0.1.0-SNAPSHOT`, `DebugDepth` / `AutoDebug` / `AutoDebugSkip` match the spec.
4. **Commits:** Steps respect user rule — commit only when explicitly requested.
