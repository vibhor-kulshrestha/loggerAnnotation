# Auto Debug Log — Design Spec

**Date:** 2026-08-16  
**Status:** Draft for review  
**Product working name:** AutoDebug (annotation `@AutoDebug`)  
**Primary platform:** Android (Kotlin-first)  
**End goal:** Progressive instrumentation from method boundaries → control-flow branches → variable mutations

---

## 1. Problem

Android/Kotlin developers often sprinkle manual `Log` / Timber calls while debugging: print parameters, return values, which `if`/`when` branch ran, and when locals change. That is noisy, easy to forget to remove, and slow to re-add for the next bug.

We want a single annotation that injects this logging at compile time, with depth that can grow over time without rewriting the engine.

## 2. Goals and non-goals

### Goals

- Annotate a function (and later a class) and get automatic debug logs.
- Ship value in **phases**, each phase testable before the next starts.
- Use **one Kotlin IR compiler-plugin spine** from day one so Phase 1 → 2 → 3 is additive, not a rewrite.
- Android Logcat as the first sink; pluggable logger later.
- Debug builds only by default (zero / near-zero effect in release when disabled).

### Non-goals (for this project’s early life)

- Replacing production APM / distributed tracing (e.g. Tracy).
- Full Java-source support via IR (Kotlin annotated code is the contract; Java callers of Kotlin are fine).
- Shipping Cabret/Hugo/Hunter as dependencies — they are **reference sources only**, then deleted from the workspace.
- Automatic Compose recomposition tracing in early phases (explicitly deferred; high noise).

## 3. Prior art and what we take from it

Reference trees currently in the workspace (to be deleted after extraction):

| Source | Path | Take | Do not take |
|---|---|---|---|
| Cabret-Log | `Cabret-Log-master/` | IR transform shape: enter/exit, args, return, duration; Gradle + registrar wiring ideas | Old Kotlin plugin APIs; ship as-is |
| Hugo | `hugo-master/` | `@DebugLog` UX; `⇢` / `⇠` log style; debug-only mental model | AspectJ; ancient AGP |
| Hunter | `Hunter-master/` | Android enable/disable patterns; class-level annotate / skip ideas | ASM as the long-term engine |

**Decision:** Do **not** implement Tier A with ASM and later rewrite for Tier C. Implement Kotlin IR from Phase 0/1; grow the same transformer.

## 4. Chosen approach

**Kotlin IR compiler plugin + Gradle plugin + thin runtime**, with an annotation `depth` that unlocks deeper instrumentation over phases.

Rejected alternatives:

1. **ASM-first (Hunter-style)** — fast for boundary logs, forces rewrite for branches/vars.
2. **Dual ASM + IR** — Java coverage at double maintenance cost; not needed for Kotlin-first Android.

## 5. Progressive phases and pass gates

Hard rule: **do not start Phase N+1 until Phase N’s pass gate is green.** If a gate fails, fix that phase first.

### Phase 0 — Scaffold

**Deliver**

- Multi-module Gradle project.
- Modules: `annotations`, `runtime`, `compiler-plugin`, `gradle-plugin`, `sample-android`.
- `@AutoDebug` annotation exists (minimum parameters).
- Compiler plugin registers and runs a no-op / identity IR pass on annotated functions.
- Sample Android app applies the Gradle plugin and depends on annotations + runtime.

**Pass gate**

- `./gradlew :sample-android:assembleDebug` succeeds.
- Project structure documented in README (short).

### Phase 1 — BOUNDARY (Tier A)

**Deliver**

- For `@AutoDebug` (default `depth = BOUNDARY`):
  - Log on entry: class/file tag, method name, parameter names + values.
  - Log on exit: return value (or `Unit`), duration.
  - Log on throw: exception type + message; rethrow unchanged.
- Runtime formats messages (Hugo-inspired arrows optional).
- Global enable flag (runtime) and Gradle `enabled` for debug vs release.
- Unit/compiler tests for IR transform on small Kotlin fixtures (preferred) plus manual Logcat check in sample.

**Pass gate**

- Sample methods annotated with `@AutoDebug` produce entry/exit (and throw) lines in Logcat.
- Disabled plugin / release configuration does not inject useful debug noise (or strips / no-ops as designed).
- Focused tests for transform or golden log strings pass.

### Phase 2 — BRANCHES (Tier B)

**Deliver**

- Extend IR visitor for `if` / `when` (`IrWhen` and related).
- New depth: `BRANCHES` (includes BOUNDARY behavior).
- Log which branch was taken (condition summary or branch index + source hint when available).

**Pass gate**

- Sample with nested `if`/`when` shows branch-taken logs only when `depth = BRANCHES`.
- `depth = BOUNDARY` sample still shows only boundary logs (no branch spam).
- Tests cover at least one `if` and one `when`.

### Phase 3 — VARS (Tier C)

**Deliver**

- Instrument selected assignments (`IrSetValue` and/or property setters in annotated scope).
- Depth: `VARS` (includes BRANCHES + BOUNDARY).
- Noise controls: optional name allowlist/denylist; skip or truncate large/`String`/collection dumps; never log annotated `@Sensitive` / redacted params if we add that helper.
- Document performance and privacy caveats.

**Pass gate**

- Sample shows `name: old → new` (or equivalent) for opted-in vars.
- Default / BOUNDARY depth does not emit var logs.
- Stress sample does not freeze UI under normal debug use (sanity check).

## 6. Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│  App source     │     │  Gradle plugin   │     │  Kotlin compiler    │
│  @AutoDebug     │────▶│  wires options   │────▶│  plugin (IR)        │
└─────────────────┘     └──────────────────┘     └──────────┬──────────┘
                                                           │ injects calls
                                                           ▼
                                                ┌─────────────────────┐
                                                │  runtime            │
                                                │  AutoDebugLogger    │
                                                │  → Logcat / custom  │
                                                └─────────────────────┘
```

### Modules

| Module | Responsibility |
|---|---|
| `annotations` | `@AutoDebug`, `DebugDepth`, optional future helpers. No Android dependency. |
| `runtime` | Logging API, formatting, enable flag, Android Log sink (android source set) + JVM println fallback for tests. |
| `compiler-plugin` | `CompilerPluginRegistrar`, IR generation extension, transformers per depth. |
| `gradle-plugin` | Applies compiler plugin artifact + passes `enabled`, Kotlin version alignment helpers. |
| `sample-android` | Manual verification app; one screen/functions per phase. |

### Annotation API (progressive, stable names)

```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AutoDebug(
    val tag: String = "",
    val depth: DebugDepth = DebugDepth.BOUNDARY,
)

enum class DebugDepth {
    BOUNDARY,  // Phase 1
    BRANCHES,  // Phase 2
    VARS,      // Phase 3
}
```

- Empty `tag` → use class name for members, file name for top-level functions.
- Class-level `@AutoDebug` (Phase 1 or early Phase 2): apply to eligible methods unless `@AutoDebugSkip` (add when class-level lands; design includes the skip annotation name so Hunter-style opt-out is planned).

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class AutoDebugSkip
```

### Runtime API (minimal)

```kotlin
object AutoDebugConfig {
    @JvmStatic var enabled: Boolean = true
}

interface AutoDebugSink {
    fun log(tag: String, message: String)
}
```

Default Android sink: `android.util.Log.d`. Custom sink installable for tests/Timber later.

### IR transform behavior

**Phase 1 (BOUNDARY)** — for each annotated callable:

1. Detect `@AutoDebug` (or inherited from class, minus skip).
2. At start of body: log args; mark start time.
3. On each return: log result + duration.
4. Wrap body so uncaught exceptions are logged then rethrown.

**Phase 2** — additionally when `depth >= BRANCHES`:

- Before/after branch selection for `IrWhen`, emit branch identity.

**Phase 3** — additionally when `depth >= VARS`:

- After qualifying `IrSetValue`, log name + new value (and previous when cheap).

Exact IR node matching may adjust during implementation; behavior above is the contract.

## 7. Error handling and safety

- Instrumentation must not change method semantics (return values, thrown types, call order of user code aside from inserted log calls).
- Logging failures (sink throws) must be swallowed or guarded so app logic is not broken by the debugger.
- `toString()` on arguments may throw or be huge: catch, truncate (e.g. 200–500 chars), and mark truncated.
- Secrets: document that developers must not annotate methods that always handle passwords/tokens until redaction exists; Phase 3 may add `@AutoDebugRedact` on parameters — optional, only if needed during Phase 1/3 implementation.

## 8. Testing strategy

| Layer | What |
|---|---|
| Compiler-plugin tests | Compile Kotlin snippets with the plugin (e.g. kotlin-compile-testing or in-repo fixtures); assert IR side effects via runtime capture sink or bytecode/string presence. |
| Runtime unit tests | Formatting, truncation, enable flag. |
| Sample Android | Manual Logcat checklist per phase gate. |

Phase gate = automated tests for that phase’s scope **plus** sample checklist.

## 9. Build and versioning constraints

- Target current stable Kotlin 2.x and AGP used by the sample at implementation time; pin compiler-plugin to that Kotlin version (document in README).
- Compiler plugin APIs are unstable: expect version bumps when upgrading Kotlin — treat as supported maintenance, not scope creep.
- Prefer publishing locally / composite build first; Maven publish is out of scope until Phase 1 gate passes.

## 10. Workspace hygiene

After useful patterns are copied into our modules:

1. Delete `Cabret-Log-master/`, `hugo-master/`, `Hunter-master/` from the project root.
2. Keep short `docs/references.md` listing upstream URLs and what we learned (no vendored trees).

Do not commit vendor trees if git is initialized later.

## 11. Success criteria (project)

- Phase 1 alone is useful daily for Android Kotlin debugging (boundary logs).
- Phase 2–3 land without replacing the plugin architecture.
- A developer can add `@AutoDebug` and see Logcat output without writing manual log lines.

## 12. Open decisions locked by this spec

| Topic | Decision |
|---|---|
| Engine | Kotlin IR only |
| First depth | BOUNDARY |
| Path to C | Same plugin; raise `DebugDepth` |
| Java IR | Not supported |
| Vendor code | Reference extract, then delete |
| Process | Phase N must pass gate before N+1 |

---

## Spec self-review (2026-08-16)

- No intentional TBD placeholders left; implementation-time IR node names may be refined but behavior is specified.
- Phases, modules, and annotation API are consistent with the IR-only approach.
- Scope is one product with four gated phases; implementation plans should be written **per phase** (Phase 0 plan first), not one giant unscoped plan.
- Ambiguity resolved: class-level annotate + skip are planned; Compose auto-trace is explicitly out; ASM is not a fallback engine.
