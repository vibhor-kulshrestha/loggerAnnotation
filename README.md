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
