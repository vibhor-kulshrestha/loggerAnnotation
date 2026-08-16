# Phase gates

## Phase 0

- [x] `./gradlew :annotations:test :runtime:test` passes
- [x] `./gradlew :sample-android:assembleDebug` passes
- [x] README documents modules

Only then start Phase 1 plan/implementation.

## Phase 1

- [x] `./gradlew :runtime:test :compiler-plugin:test` passes (BOUNDARY coverage)
- [x] `./gradlew :sample-android:assembleDebug` passes after publishToMavenLocal
- [x] Sample `@AutoDebug` methods produce enter/exit (and throw) logs (Logcat or compile-testing evidence)
- [x] Release/debug enable: release compilations get `enabled=false` (or documented equivalent)

Only then start Phase 2.

## Phase 2

- [x] Runtime `logBranch` + unit tests
- [x] IR instruments IrWhen only when depth is BRANCHES or VARS
- [x] Compile tests: BOUNDARY has no ↦; BRANCHES covers if + when
- [x] Sample assembleDebug; Logcat or compile-testing evidence for branch lines

Only then start Phase 3.

## Phase 3

- [x] Runtime `logAssignment` + unit tests
- [x] IR: VARS-only locals + this-property writes; foreign writes skipped
- [x] Compile tests: BOUNDARY/BRANCHES have no ↻; VARS covers local + this property
- [x] Sample assembleDebug; Logcat or compile-testing evidence
- [x] README notes scope (writes only; locals + this)

Phase 3 complete = product MVP of progressive AutoDebug (A→B→C).
