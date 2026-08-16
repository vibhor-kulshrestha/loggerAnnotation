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
