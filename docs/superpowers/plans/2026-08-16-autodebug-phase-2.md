# AutoDebug Phase 2 — BRANCHES Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When `@AutoDebug(depth = BRANCHES)` (or `VARS`), log which `if`/`when` branch ran, while `depth = BOUNDARY` keeps Phase 1 enter/exit/throw only.

**Architecture:** Extend the existing IR transformer: always apply BOUNDARY instrumentation for annotated functions; additionally walk `IrWhen` (Kotlin’s representation of both `if` and `when`) when annotation depth is `BRANCHES` or `VARS`. Inject `AutoDebug.logBranch(...)` at the start of each taken branch result. No ASM; no variable assignment logging (Phase 3).

**Tech Stack:** Same as Phase 1 (Kotlin `2.1.21`, AGP `8.9.1`, Gradle `8.13`, kctfork for compiler tests).

**Spec:** `docs/superpowers/specs/2026-08-16-auto-debug-log-design.md` § Phase 2.

## Global Constraints

- Engine remains **Kotlin IR only**.
- Phase 2 adds **branch logging only** — do not instrument `IrSetValue` / property mutations (Phase 3).
- Depth rules (locked):
  - `BOUNDARY` → enter/exit/throw only (no branch lines).
  - `BRANCHES` → BOUNDARY + branch lines.
  - `VARS` → treat like BRANCHES for Phase 2 (branch lines on; var lines still off until Phase 3).
- Default annotation depth stays `BOUNDARY`.
- Skip: fake override, inline, suspend, non-`IrBlockBody` (same as Phase 1).
- Logging must not change branch outcomes or thrown types; sink failures swallowed.
- Truncate branch descriptions (reuse 300-char helper).
- **Never** add `Co-authored-by:` to commits; verify after each commit.
- After plugin/runtime changes: `publishToMavenLocal` before sample assemble.
- YAGNI: no nested-depth filters, no source-line mapping required if unavailable; branch index + short label is enough.

---

## File structure

```
runtime/
  AutoDebugMessages.kt     — add branch(...)
  AutoDebugSink.kt         — add logBranch(...)
  tests                    — message + API tests

compiler-plugin/
  AutoDebugIrTransformer.kt — read depth; IrWhen visitor when depth >= BRANCHES
  IrAutoDebugSymbols.kt     — logBranch symbol
  BranchLoggingIrTest.kt    — NEW (BOUNDARY vs BRANCHES; if + when)

sample-android/
  Demo.kt                   — classify(if), pick(when) at BRANCHES; keep greet at BOUNDARY
  MainActivity.kt           — call new demos

docs/superpowers/phase-gates.md
README.md
```

---

### Task 1: Runtime branch message API

**Files:**
- Modify: `runtime/src/main/kotlin/com/autodebug/runtime/AutoDebugMessages.kt`
- Modify: `runtime/src/main/kotlin/com/autodebug/runtime/AutoDebugSink.kt`
- Modify or create: `runtime/src/test/kotlin/com/autodebug/runtime/AutoDebugMessagesTest.kt`
- Modify or create: `runtime/src/test/kotlin/com/autodebug/runtime/AutoDebugBoundaryApiTest.kt` (or new `AutoDebugBranchApiTest.kt`)

**Interfaces:**
- Consumes: existing `log`, truncate helpers
- Produces:
  - `AutoDebugMessages.branch(method: String, label: String): String` → `"↦ method · label"`
  - `AutoDebug.logBranch(tag: String, method: String, label: String)`

Label examples (produced by IR later): `if#0-then`, `if#1-else`, `when#0`, `when#1-else`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test
fun `branch message shape`() {
    assertEquals("↦ classify · if#0-then", AutoDebugMessages.branch("classify", "if#0-then"))
}

@Test
fun `logBranch forwards when enabled`() {
    // sink capture → "Demo" to "↦ classify · if#0-then"
}

@Test
fun `logBranch skipped when disabled`() {
    AutoDebugConfig.enabled = false
    // no lines
}
```

- [ ] **Step 2: Run — expect FAIL** (missing API)

- [ ] **Step 3: Implement**

```kotlin
fun branch(method: String, label: String): String =
    "↦ $method · ${truncate(label)}"

@JvmStatic
fun logBranch(tag: String, method: String, label: String) {
    log(tag, AutoDebugMessages.branch(method, label))
}
```

- [ ] **Step 4: `./gradlew :runtime:test` — PASS**

- [ ] **Step 5: Commit if authorized** — `feat: add AutoDebug branch log API` (no co-author)

---

### Task 2: IR depth reading + IrWhen instrumentation

**Files:**
- Modify: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/IrAutoDebugSymbols.kt`
- Modify: `compiler-plugin/src/main/kotlin/com/autodebug/compiler/AutoDebugIrTransformer.kt`

**Interfaces:**
- Consumes: annotation `depth` enum (`com.autodebug.DebugDepth`), `logBranch` symbol
- Produces: for annotated functions with depth `BRANCHES` or `VARS`, each `IrWhen` branch result wrapped so the taken arm logs once before executing

**Depth helper (exact behavior):**

```kotlin
private enum class EffectiveDepth { BOUNDARY, BRANCHES }

private fun readDepth(function: IrSimpleFunction): EffectiveDepth {
    val annotation = function.getAnnotation(autoDebugFqName) ?: return EffectiveDepth.BOUNDARY
    // Read enum arg named "depth". If missing → BOUNDARY.
    // Map DebugDepth.BOUNDARY → BOUNDARY
    // Map DebugDepth.BRANCHES or DebugDepth.VARS → BRANCHES (vars still deferred)
}
```

Use Kotlin 2.1.21 IR annotation APIs (`getValueArgument`, enum entry name). If enum read is awkward, match by `IrGetEnumValue` / FqName ending in `.BRANCHES` / `.VARS`.

**IrWhen transform (inside instrumented function body only):**

After BOUNDARY body rebuild (or during the same pass on the original statements before wrapping), visit `IrWhen`:

For each branch index `i` in `irWhen.branches`:
- Let `label =` if looking like binary if (`branches.size == 2` and last is else/`IrConst true`): `if#$i-then` / `if#$i-else`; else `when#$i` (last else → `when#$i-else` when condition is constantly true).
- Replace branch result `R` with:

```kotlin
irBlock(type = R.type) {
  +irCall(logBranch).apply { tag; method; irString(label) }
  +R  // possibly after transforming nested IrWhen inside R
}
```

Transform nested `IrWhen` recursively so nested if/when also log.

**Important:** Only apply this visitor when `readDepth(function) == BRANCHES`. BOUNDARY functions must not visit/wrap branches.

Wire `logBranch` in `IrAutoDebugSymbols` like other AutoDebug members.

- [ ] **Step 1: Add `logBranch` symbol lookup**

- [ ] **Step 2: Implement `readDepth` + branch transformer**

- [ ] **Step 3: `./gradlew :compiler-plugin:compileKotlin` SUCCESS**

- [ ] **Step 4: Publish** `:runtime:publishToMavenLocal :compiler-plugin:publishToMavenLocal`

- [ ] **Step 5: Commit** — `feat: instrument if/when branches for AutoDebug BRANCHES depth`

---

### Task 3: Compile-testing for BOUNDARY vs BRANCHES

**Files:**
- Create: `compiler-plugin/src/test/kotlin/com/autodebug/compiler/BranchLoggingIrTest.kt`

**Interfaces:**
- Consumes: same compile-testing setup as `BoundaryLoggingIrTest`
- Produces: three tests minimum

- [ ] **Step 1: Test — BOUNDARY depth does not emit branch lines**

Compile:

```kotlin
class Subject {
  @AutoDebug(tag = "Subject", depth = DebugDepth.BOUNDARY)
  fun classify(x: Int): String = if (x > 0) "pos" else "nonpos"
}
```

Call `classify(1)`. Assert enter/exit present; assert **no** line containing `↦`.

- [ ] **Step 2: Test — BRANCHES depth logs if arm**

```kotlin
@AutoDebug(tag = "Subject", depth = DebugDepth.BRANCHES)
fun classify(x: Int): String = if (x > 0) "pos" else "nonpos"
```

Call `classify(1)` → expect a `↦` line with `if` / `then` (or `if#0`).  
Call `classify(-1)` → expect else label.

- [ ] **Step 3: Test — BRANCHES depth logs when arm**

```kotlin
@AutoDebug(tag = "Subject", depth = DebugDepth.BRANCHES)
fun pick(x: Int): String = when (x) {
  1 -> "one"
  2 -> "two"
  else -> "other"
}
```

Call `pick(2)` → expect `↦` mentioning `when` and branch identity; enter/exit still present.

- [ ] **Step 4: `./gradlew :compiler-plugin:test` PASS**

- [ ] **Step 5: Commit** — `test: verify BRANCHES if/when logging and BOUNDARY isolation`

---

### Task 4: Sample app demos + Logcat-friendly calls

**Files:**
- Modify: `sample-android/src/main/java/com/autodebug/sample/Demo.kt`
- Modify: `sample-android/src/main/java/com/autodebug/sample/MainActivity.kt`

- [ ] **Step 1: Add demos**

```kotlin
@AutoDebug(tag = "Demo", depth = DebugDepth.BOUNDARY)
fun greet(name: String): String = "Hello, $name"

@AutoDebug(tag = "Demo", depth = DebugDepth.BRANCHES)
fun classify(x: Int): String = if (x >= 0) "non-negative" else "negative"

@AutoDebug(tag = "Demo", depth = DebugDepth.BRANCHES)
fun pick(code: Int): String = when (code) {
    1 -> "one"
    2 -> "two"
    else -> "other"
}
```

Keep `fail` as-is (BOUNDARY default) or leave unchanged.

- [ ] **Step 2: MainActivity** call `classify(1)`, `classify(-3)`, `pick(2)` after greet (try/catch fail still OK).

- [ ] **Step 3: Publish + assembleDebug + tests**

```bash
./gradlew :annotations:publishToMavenLocal :runtime:publishToMavenLocal :compiler-plugin:publishToMavenLocal
./gradlew :runtime:test :compiler-plugin:test :sample-android:assembleDebug
```

- [ ] **Step 4: Manual Logcat (if device available)** — filter `Demo:D`; expect `↦` for classify/pick, **no** `↦` for greet.

- [ ] **Step 5: Commit** — `feat: sample BRANCHES demos for if/when`

---

### Task 5: Docs + Phase 2 gate

**Files:**
- Modify: `docs/superpowers/phase-gates.md`
- Modify: `README.md`

- [ ] **Step 1: Add Phase 2 section** with checkboxes:

```markdown
## Phase 2

- [ ] Runtime `logBranch` + unit tests
- [ ] IR instruments IrWhen only when depth is BRANCHES or VARS
- [ ] Compile tests: BOUNDARY has no ↦; BRANCHES covers if + when
- [ ] Sample assembleDebug; Logcat or compile-testing evidence for branch lines

Only then start Phase 3.
```

Mark `[x]` after verification.

- [ ] **Step 2: README** — Phase 2 `[x]`; link plan `docs/superpowers/plans/2026-08-16-autodebug-phase-2.md`

- [ ] **Step 3: Full verification commands** (same as Task 4 Step 3)

- [ ] **Step 4: Commit** — `docs: mark Phase 2 BRANCHES gate complete`

---

## Phase 2 exit criteria

1. `logBranch` API + tests green.
2. IR reads depth; BRANCHES/VARS get IrWhen logs; BOUNDARY does not.
3. Compile-testing covers BOUNDARY isolation + if + when.
4. Sample builds with demos; evidence via tests and/or Logcat.
5. Docs updated; no co-author trailers.

**Do not start Phase 3** until the above pass.

---

## Plan self-review

1. **Spec coverage:** IrWhen for if/when, depth gating, BOUNDARY isolation, tests, sample — tasked. VARS assignment logging excluded.
2. **No TBD:** Label scheme and depth mapping are explicit.
3. **Consistency:** `↦` message format shared by runtime tests and IR labels; FQN `logBranch` matches symbols.
4. **Phase 1 compatibility:** Default depth unchanged; existing greet/fail behavior preserved.
