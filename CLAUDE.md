# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Lawnchair 16 is an Android home launcher built on AOSP Launcher3 from Android 16. The branch is `16-dev`. Most of the repo is the upstream AOSP/Launcher3 tree, and Lawnchair's own code lives in `lawnchair/`.

## Setup

- Run `git submodule update --init` first. `platform_frameworks_libs_systemui` is a submodule on the `16-dev` branch, and `settings.gradle` includes its `iconloaderlib`, `searchuilib`, `animationlib` and `msdllib` modules. The build fails without it.
- JDK 21 (CI uses Zulu 21). `build.gradle` pins compileSdk 37 and buildTools 37.0.0.

## Commands

Build variants are `flavor x recents x channel`. The flavor dimensions are `lawn`, `withQuickstep`, and a single `channel` flavor, `play`. Task names follow `assemble<Lawn><WithQuickstep><Channel><BuildType>`:

```
./gradlew assembleLawnWithQuickstepPlayDebug
./gradlew assembleLawnWithQuickstepPlayRelease
```

Lint and formatting (CI runs `spotlessCheck` on every PR):

```
./gradlew spotlessCheck
./gradlew spotlessApply                            # fixes Kotlin (ktlint + compose rules) and Java (google-java-format AOSP)
```

Tests:

- JVM unit tests live in `tests/unit/src`, which is wired to the Gradle `test` source set. Run them with `./gradlew testLawnWithQuickstepPlayDebugUnitTest` (check the exact name with `./gradlew tasks --all | grep -i unittest`).
- Single test class: `./gradlew testLawnWithQuickstepPlayDebugUnitTest --tests "com.android.launcher3.pm.InstallSessionHelperTest"`.
- `tests/src/` holds more Launcher3 Kotlin tests, and `tests/Android.bp`, `tests/multivalentTests` and `tests/tapl` are the AOSP/Soong-side test setup. I did not confirm that Gradle compiles `tests/src`, so check before relying on it.
- Custom lint detectors live in `checks/` (`Launcher3IssueRegistry`) and have their own tests there.

## Architecture

**Layering.** Lawnchair is a set of overlays on top of Launcher3. Upstream code stays at the repo root. The Gradle `main` source set compiles `src/`, `src_plugins/`, `compose/facade/{core,enabled}` and `compose/features/`. Quickstep (the Android recents/gesture layer) is in `quickstep/`, `wmshell/` and `systemUI/`. Lawnchair's own code is a separate `lawn` source set that adds `lawnchair/src`, `lawnchair/res`, `lawnchair/aidl` and `tests/shared`, so `lawn` builds layer on top of `main`.

**Flavors** (`build.gradle`):
- `app`: `lawn` is the only flavor. It selects the Lawnchair sources above.
- `recents`: `withQuickstep` enables Quickstep recents and sets minSdk 26.
- `channel`: `play` (default, applicationId `net.mdantas.terminal`), `github` and `nightly` each have their own applicationId, so they install side by side.
- Every variant gets `launcher_component` as a string resource pointing at `<applicationId>/app.lawnchair.LawnchairLauncher`. Anything that refers to the launcher component must go through that resource.

**Per-Android-version compat.** `compatLib/compatLibV{Q,R,S,T,U,V,Baklava}` each hold `ActivityManagerCompat`, `ActivityOptionsCompat` and `QuickstepCompatFactory` for one platform release (Q = Android 10 through Baklava = Android 16). The factory chooses the implementation at runtime. Add new platform API access through these modules, not inline.

**Shared libraries.** `androidx-lib/` and `concurrent/`, `dagger/`, `flags/`, `hidden-api/`, `aconfig/` and `flowerpot/` are AOSP-side support modules. Feature flags are declared as aconfig files in `aconfig/` (`launcher*.aconfig`).

**Lawnchair features** (`lawnchair/src/app/lawnchair/`):
- `LawnchairApp` / `LawnchairLauncher` / `LawnchairProcessInitializer` are the entry points.
- `preferences2/` wraps the preference store. `PreferenceManager2` and `PreferenceCollectorScope` are the API for reading and observing settings, and `SharedPreferencesMigration` handles older prefs. Settings screens are in `ui/preferences/destinations/`.
- `command/` is the command bar: `CommandEngine` and `CommandExecutor` run commands, `CustomActionStore` persists user actions, and `VoiceCommand`, `CalcEvaluator` and `CommandActivity` handle speech, calculation and the UI. This was the active area of development on this branch.
- `data/` holds the Room `AppDatabase` (with `folder`, `iconoverride` and `wallpaper` subpackages).
- `icons/`, `theme/`, `font/`, `search/`, `predictions/`, `smartspace/`, `backup/` and `root/` are feature packages. `root/` uses the `IRootHelper` AIDL service.
- `aidl/` defines the `IBridge` service and `ILauncherOverlay` callbacks used for cross-process communication.

## Conventions

- Branch names end in `-dev`. CI (`.github/workflows/ci.yml`) runs on pushes to `*-dev` branches and on PRs. The nightly release is published from `16-dev` pushes.
- Commit messages in this repo are written in Portuguese and prefixed with the feature area, for example `Barra de comando: ...` or `Botão [MIC] na barra da tela inicial: ...`. Follow that pattern for the same areas.
- Kotlin is formatted by Spotless with ktlint + Compose rules, and only `lawnchair/src/**/*.kt` is in its target. Java in `src/` and `quickstep/` is formatted with google-java-format AOSP style. Do not run Spotless against `compatLib/` (the Spotless config comments out that path intentionally).
- `.editorconfig` sets 4-space indentation, and `ktlint` allows trailing commas.
