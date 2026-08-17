# AutoDebug

Kotlin compiler plugin that adds debug logs for you. Annotate a function — get entry/exit (and optionally branch + variable writes) in Logcat without hand-written `Log.d` calls.

Built for Android / Kotlin. Instrumentation runs at compile time (Kotlin IR). Debug builds only by default.

## Features

| Depth | What gets logged |
|---|---|
| `BOUNDARY` (default) | Enter with args, exit with return value + duration, throws |
| `BRANCHES` | Everything above + which `if` / `when` arm ran |
| `VARS` | Everything above + local `var` writes and `this` property writes |

Example Logcat:

```text
Demo  ⇢ greet(name=Ada)
Demo  ⇠ greet = Hello, Ada [1ms]

Demo  ⇢ classify(x=1)
Demo  ↦ classify · if#0-then
Demo  ⇠ classify = non-negative [0ms]

Demo  ⇢ bump(n=3)
Demo  ↻ bump · step: 0 → 3
Demo  ↻ bump · total: 0 → 3
Demo  ⇠ bump = 3 [0ms]
```

## Usage

### 1. Configure repositories

Make sure `mavenCentral()` is defined in your root `settings.gradle.kts` file:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral() // 👈 Required for the plugin
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral() // 👈 Required for library dependencies
    }
}
```

### 2. Apply the plugin and dependencies

In your app (or library) module's `build.gradle.kts` file:

```kotlin
plugins {
    id("com.android.application") // or library
    id("org.jetbrains.kotlin.android")
    id("io.github.vibhor-kulshrestha.autodebug") version "0.1.2"
}

dependencies {
    implementation("io.github.vibhor-kulshrestha:autodebug-annotations:0.1.2")
    implementation("io.github.vibhor-kulshrestha:autodebug-runtime:0.1.2")
}
```

### 3. Annotate functions

```kotlin
import com.autodebug.AutoDebug
import com.autodebug.DebugDepth
import com.autodebug.runtime.AutoDebug
import com.autodebug.runtime.AutoDebugSink
import android.util.Log

// Optional: route to Logcat (default sink is println)
AutoDebug.sink = AutoDebugSink { tag, message -> Log.d(tag, message) }

class Wallet {
    @AutoDebug(tag = "Pay")
    fun charge(amount: Int): String = "ok:$amount"

    @AutoDebug(tag = "Pay", depth = DebugDepth.BRANCHES)
    fun route(code: Int): String = when (code) {
        1 -> "upi"
        else -> "card"
    }

    var balance = 0

    @AutoDebug(tag = "Pay", depth = DebugDepth.VARS)
    fun credit(n: Int): Int {
        var step = 0
        step = n
        balance = balance + step
        return balance
    }
}
```

### 4. Skipping functions (In Progress)

The `@AutoDebugSkip` annotation is available in the `annotations` module. It is designed to let you exclude specific functions from being debugged when applying `@AutoDebug` at the class level (e.g., to avoid logging sensitive data or fast-running loops):

```kotlin
@AutoDebug
class UserRepository {
    fun fetchUser() { ... }

    @AutoDebugSkip // 👈 Excludes this function from logging
    fun login(password: String) { ... }
}
```

> [!NOTE]
> Class-level `@AutoDebug` and the `@AutoDebugSkip` filter are currently **in progress** and not yet fully supported in the compiler plugin. Currently, you must annotate individual functions with `@AutoDebug`.

### 5. Depth guide

- **`BOUNDARY`** — safest default for everyday debugging.
- **`BRANCHES`** — when you care which `if`/`when` path ran.
- **`VARS`** — logs **writes only**: local `var` reassignments and **`this`** property updates. Does **not** log reads, parameters as assignments, or fields on other objects.

Empty `tag` → class name (or file name for top-level functions).

### 6. Runtime kill switch

```kotlin
com.autodebug.runtime.AutoDebugConfig.enabled = false
```

The Gradle plugin also passes `enabled=false` for non-debug compilations (e.g. release), so release builds stay clean by default.

## Local development (this repo)

To build and run the sample application locally:

```bash
./gradlew :sample-android:assembleDebug
# optional: installDebug and filter Logcat with tag Demo
```

Sample app: `sample-android` (`Demo`, `Accumulator`).

## Modules

| Module | Role |
|---|---|
| `annotations` | `@AutoDebug`, `DebugDepth`, `@AutoDebugSkip` |
| `runtime` | Logging API + sink |
| `compiler-plugin` | Kotlin IR instrumentation |
| `gradle-plugin-build` | Gradle plugin `io.github.vibhor-kulshrestha.autodebug` |
| `sample-android` | Example app |

## Notes / limits

- Kotlin only (IR). Java sources are not instrumented.
- `suspend` / `inline` functions are skipped.
- Long values are truncated (~300 chars); `toString()` failures are caught.
- Do not annotate methods that always handle secrets (passwords, tokens) at `VARS` depth.
- Keep the plugin Kotlin version aligned with your project’s Kotlin version.
- Compatible with **Kotlin 2.1.21**, **AGP 8.9.1**, **Gradle 8.13** (see `gradle/libs.versions.toml`).

## Contributing

Contributions are welcome! Please feel free to submit issues, fork the repository, or open pull requests to improve the project.

## License

See [LICENSE](LICENSE).
