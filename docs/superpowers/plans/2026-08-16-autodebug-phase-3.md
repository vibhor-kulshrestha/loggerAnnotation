# AutoDebug Phase 3 — VARS Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When `@AutoDebug(depth = DebugDepth.VARS)`, log local `var` assignments and writes to **this** class’s properties as `name: old → new`, while BOUNDARY/BRANCHES depths stay free of assignment spam.

**Architecture:** Extend the existing IR transformer with a `VarsLoggingTransformer` that runs only when effective depth is `VARS` (which already includes BOUNDARY + BRANCHES behavior from Phase 1–2). Instrument (1) local `IrSetValue` and (2) same-receiver property setters / field stores on `this`. Do **not** log reads, foreign-object property writes, or Compose state specially in v1.

**Tech Stack:** Same as Phase 2 (Kotlin `2.1.21`, AGP `8.9.1`, Gradle `8.13`, kctfork).

**Spec:** `docs/superpowers/specs/2026-08-16-auto-debug-log-design.md` § Phase 3, with scope tightened below.

## Global Constraints

- Engine remains **Kotlin IR only**.
- Depth rules (locked):
  - `BOUNDARY` → enter/exit/throw only.
  - `BRANCHES` → + branch lines; **no** assignment lines.
  - `VARS` → + branch lines + assignment lines.
- **In scope (v1):**
  - Local `var` / mutable local assignments inside the annotated function.
  - Writes to properties / fields of **the same instance** (`this`) performed inside that function.
- **Out of scope (v1):**
  - Reads of any variable/property.
  - Writes to properties of **other** objects (`other.x = …`).
  - Top-level / companion / static vars (defer).
  - `@Sensitive` / allowlist-denylist annotations (defer; truncation is enough).
  - Compose `MutableState` / Flow special cases (defer).
- Noise: reuse 300-char truncation; catch `toString()` failures; sink failures swallowed.
- Skip: fake override, inline, suspend, non-`IrBlockBody` (same as prior phases).
- **Never** add `Co-authored-by:` to commits.
- After plugin/runtime changes: `publishToMavenLocal` before sample assemble.
- YAGNI: no name allow/deny filters in v1 unless tests prove unbearable spam on the sample.

---

## File structure

```
runtime/
  AutoDebugMessages.kt   — add assignment(...)
  AutoDebugSink.kt       — add logAssignment(...)
  tests

compiler-plugin/
  IrAutoDebugSymbols.kt  — logAssignment
  AutoDebugIrTransformer.kt — EffectiveDepth.VARS; VarsLoggingTransformer
  VarsLoggingIrTest.kt   — NEW

sample-android/
  Demo.kt or CounterDemo.kt — VARS demo with local + this.property
  MainActivity.kt

docs/superpowers/phase-gates.md
README.md
docs/references.md or README — short privacy/perf note
```

---

### Task 1: Runtime assignment message API

**Files:**
- Modify: `runtime/src/main/kotlin/com/autodebug/runtime/AutoDebugMessages.kt`
- Modify: `runtime/src/main/kotlin/com/autodebug/runtime/AutoDebugSink.kt`
- Modify: runtime tests

**Interfaces:**
- Produces:
  - `AutoDebugMessages.assignment(method: String, name: String, oldValue: Any?, newValue: Any?): String`
    → `"↻ method · name: old → new"` (values via `formatValue`)
  - `AutoDebug.logAssignment(tag: String, method: String, name: String, oldValue: Any?, newValue: Any?)`

- [ ] **Step 1: Failing tests** for shape, truncation of values, enabled/disabled gate.

```kotlin
assertEquals(
    "↻ bump · total: 0 → 3",
    AutoDebugMessages.assignment("bump", "total", 0, 3),
)
```

- [ ] **Step 2: Implement** `assignment` + `logAssignment` (delegate through `log`).

- [ ] **Step 3: `./gradlew :runtime:test` PASS**

- [ ] **Step 4: Commit** — `feat: add AutoDebug assignment log API` (no co-author)

---

### Task 2: IR VARS instrumentation (locals + this properties)

**Files:**
- Modify: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/IrAutoDebugSymbols.kt`
- Modify: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/AutoDebugIrTransformer.kt`

**Interfaces:**
- Extend depth enum usage:
  - Today: `BOUNDARY` | `BRANCHES` (VARS mapped to BRANCHES for branches).
  - Change to: `BOUNDARY` | `BRANCHES` | `VARS`.
  - Branch transformer when depth is `BRANCHES` **or** `VARS`.
  - Vars transformer when depth is `VARS` only.

**Local assignments (`IrSetValue`):**

For `IrSetValue` whose symbol is a **local** `IrVariable` (not a value parameter unless reassigned — Kotlin params are vals; skip params):

```kotlin
// Pseudocode wrap:
val old = irGet(variable)   // only if readable; if not safe, log new only as "· name := new"
val newExpr = /* original value expression, possibly after transform */
// After computing new value into temp:
+irCall(logAssignment)(tag, method, name, old, newTemp)
+irSet(variable, newTemp)
```

Prefer **old → new** when old is cheap to read before the set. If reading old is unsafe/impossible for that symbol, fall back to:

```text
↻ method · name := new
```

via `AutoDebugMessages.assignmentSet(method, name, newValue)` **only if needed** — prefer implementing old→new first; add `:=` helper only if IR forces it.

**This-property writes:**

Instrument when the store targets a field/property of the dispatch receiver of the annotated function:

- `IrSetField` where receiver is `this` (dispatch receiver of the function), **or**
- Call to a property setter where dispatch receiver is `this`.

Skip:

- Extension receivers that aren’t the enclosing instance (be conservative).
- Setters where receiver is clearly another value (`IrGetValue` of a different parameter/local holding another object).

Property name: use field/property simple name (`total`, not `setTotal`).

**Composition with branch transform:**

Order inside instrumented body statements:

1. If depth ≥ BRANCHES: apply `BranchLoggingTransformer`.
2. If depth == VARS: apply `VarsLoggingTransformer` (can run after branch wrap so assignments inside arms are still seen — visit full subtree).

Alternatively one combined visitor when `VARS`. Keep code readable; prefer separate transformers composed.

- [ ] **Step 1: Add `logAssignment` symbol**

- [ ] **Step 2: Refactor `readDepth` → three-way `EffectiveDepth`**

```kotlin
private enum class EffectiveDepth { BOUNDARY, BRANCHES, VARS }

// BRANCHES → BRANCHES
// VARS → VARS
// else → BOUNDARY

val wantBranches = depth == BRANCHES || depth == VARS
val wantVars = depth == VARS
```

- [ ] **Step 3: Implement `VarsLoggingTransformer`**

- [ ] **Step 4: `./gradlew :compiler-plugin:compileKotlin` SUCCESS; publish runtime+plugin**

- [ ] **Step 5: Commit** — `feat: instrument local and this-property assignments for VARS depth`

---

### Task 3: Compile-testing for depth isolation + locals + this property

**Files:**
- Create: `compiler-plugin/src/test/kotlin/com/autodebug/compiler/VarsLoggingIrTest.kt`

**Tests (minimum):**

1. **BOUNDARY** function with local `var` assignment → **no** `↻` lines (still may have ⇢/⇠).
2. **BRANCHES** function with local assignment → **no** `↻` (may have `↦`).
3. **VARS** local assignment → has `↻` with name and new (and old if implemented).
4. **VARS** class property write on `this` → has `↻` with property name.
5. **VARS** writing `other.x` (other object) → **no** `↻` for that foreign write (if easy to fixture); otherwise document skip and cover with unit-level receiver check test comment. Prefer a real negative fixture:

```kotlin
class Box(var v: Int)
class Subject {
  var total = 0
  @AutoDebug(tag = "S", depth = DebugDepth.VARS)
  fun mix(n: Int, other: Box): Int {
    var local = 0
    local = n
    total = total + n
    other.v = n   // must NOT log
    return local
  }
}
```

Assert `↻` contains `local` and `total`; assert no line attributing `other`/`v` as a logged assignment (or no `↻` containing `v` if that’s the property name — use distinct names: `other.boxValue` vs `total`).

- [ ] **Step 1–4: Implement tests; `./gradlew :compiler-plugin:test` PASS**

- [ ] **Step 5: Commit** — `test: verify VARS assignment logging and depth isolation`

---

### Task 4: Sample demo + docs note

**Files:**
- Modify: `sample-android/.../Demo.kt` (or add `Accumulator` class in same file)
- Modify: `MainActivity.kt`
- Modify: `README.md` (short “VARS logs writes only; locals + this properties” note)

**Sample shape:**

```kotlin
class Accumulator {
    var total = 0

    @AutoDebug(tag = "Demo", depth = DebugDepth.VARS)
    fun bump(n: Int): Int {
        var step = 0
        step = n
        total = total + step
        return total
    }
}
```

`MainActivity`: `Accumulator().bump(3)` (and maybe `bump(2)` again to show `total` old→new).

Keep existing greet/classify/pick/fail demos.

- [ ] **Step 1: Implement sample**

- [ ] **Step 2: Publish + assembleDebug + all tests**

- [ ] **Step 3: Manual Logcat if device available** — expect `↻` for `step`/`total`; no `↻` on greet/classify.

- [ ] **Step 4: Commit** — `feat: sample VARS demo for local and this-property writes`

---

### Task 5: Phase 3 gate docs

**Files:**
- Modify: `docs/superpowers/phase-gates.md`
- Modify: `README.md`

```markdown
## Phase 3

- [ ] Runtime `logAssignment` + unit tests
- [ ] IR: VARS-only locals + this-property writes; foreign writes skipped
- [ ] Compile tests: BOUNDARY/BRANCHES have no ↻; VARS covers local + this property
- [ ] Sample assembleDebug; Logcat or compile-testing evidence
- [ ] README notes scope (writes only; locals + this)

Phase 3 complete = product MVP of progressive AutoDebug (A→B→C).
```

- [ ] **Step 1: Verify full suite; mark gates `[x]`**

- [ ] **Step 2: Commit** — `docs: mark Phase 3 VARS gate complete`

---

## Phase 3 exit criteria

1. Assignment API + tests green.
2. Depth gating correct (only `VARS` emits `↻`).
3. Locals + `this` properties logged; foreign object writes not logged (tested).
4. Sample builds; evidence via tests and/or Logcat.
5. Docs state optimized scope / privacy (writes only, truncation).
6. No co-author trailers.

---

## Plan self-review

1. **Spec coverage:** VARS depth, assignments, isolation, sample, noise via truncation — tasked. Allowlist/`@Sensitive` deferred explicitly (YAGNI).
2. **Scope locked:** locals + this properties; no reads; no foreign writes.
3. **Consistency:** `↻` format shared; depth enum three-way aligned with branch gating.
4. **No TBD:** fallback `:=` only if old-value read impossible — implementers try old→new first.
