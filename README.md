# AutoDebug

Kotlin annotation + compiler plugin for progressive debug logging on Android.

## Phase status

- [x] Phase 0 — Scaffold (plugin applies, sample assembles)
- [x] Phase 1 — BOUNDARY logs
- [x] Phase 2 — BRANCHES
- [x] Phase 3 — VARS

**VARS depth** logs variable writes only — local `var` assignments and `this` property writes (e.g. `total = …`). It does not log parameters, reads, or fields on other objects.

## Modules

| Module | Role |
|---|---|
| `annotations` | `@AutoDebug`, `DebugDepth`, `@AutoDebugSkip` |
| `runtime` | Enable flag + sink |
| `compiler-plugin` | Kotlin IR plugin |
| `gradle-plugin-build` | Gradle plugin `com.autodebug` |
| `sample-android` | Manual verification app |

## Toolchain

Kotlin `2.1.21`, AGP `8.9.1`, Gradle `8.13`.

## Build

Publish compiler artifacts to the local Maven repo before building the sample (required once per clean checkout or after plugin/runtime changes):

```bash
./gradlew :annotations:publishToMavenLocal :runtime:publishToMavenLocal :compiler-plugin:publishToMavenLocal
./gradlew :sample-android:assembleDebug
```

## Spec / plans

- Spec: `docs/superpowers/specs/2026-08-16-auto-debug-log-design.md`
- Phase 0 plan: `docs/superpowers/plans/2026-08-16-autodebug-phase-0.md`
- Phase 1 plan: `docs/superpowers/plans/2026-08-16-autodebug-phase-1.md`
- Phase 2 plan: `docs/superpowers/plans/2026-08-16-autodebug-phase-2.md`
- Phase 3 plan: `docs/superpowers/plans/2026-08-16-autodebug-phase-3.md`

Prior art links: `docs/references.md`.
