# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Lawnchair 16 is an Android home launcher built on AOSP Launcher3 from Android 16. The branch is `16-dev`. Most of the repo is the upstream AOSP/Launcher3 tree, and Lawnchair's own code lives in `lawnchair/`.

## Setup

- Run `git submodule update --init` first. `platform_frameworks_libs_systemui` is a submodule on the `16-dev` branch, and `settings.gradle` includes its `iconloaderlib`, `searchuilib`, `animationlib` and `msdllib` modules. The build fails without it.
- `commands/` is also a submodule (`terminal-commands`, the command bar engine shared with the Terminal Bar app). See the section "Motor da barra de comando" below and `commands/agent.md`.
- JDK 21. `build.gradle` pins compileSdk 37 and buildTools 37.0.0.

## Commands

Build variants are `flavor x recents x channel`. The flavor dimensions are `lawn`, `withQuickstep`, and a single `channel` flavor, `play`. Task names follow `assemble<Lawn><WithQuickstep><Channel><BuildType>`:

```
./gradlew assembleLawnWithQuickstepPlayDebug
./gradlew assembleLawnWithQuickstepPlayRelease
```

Lint and formatting:

```
./gradlew spotlessCheck
./gradlew spotlessApply                            # fixes Kotlin (ktlint + compose rules) and Java (google-java-format AOSP)
```

Tests:

- JVM unit tests live in `tests/unit/src`, which is wired to the Gradle `test` source set. Run them with `./gradlew testLawnWithQuickstepPlayDebugUnitTest` (check the exact name with `./gradlew tasks --all | grep -i unittest`).
- Single test class: `./gradlew testLawnWithQuickstepPlayDebugUnitTest --tests "com.android.launcher3.pm.InstallSessionHelperTest"`.
- The command bar engine tests live in the `:commands` module (`commands/tests/src`, plus `command-host/tests/src` for the launcher-only features). Run them with `./gradlew :commands:testDebugUnitTest`.
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
- `command/` is the command bar, split in three. The engine (`CommandEngine`, the rule objects such as `CalcEvaluator`, `TimeParse`, `VoiceCommand`, plus `CustomActionStore`, `Texts` and `foldAccents`) lives in the `:commands` Android library, the `commands/` submodule (namespace `app.lawnchair.command`), which must not depend on Launcher3 and is shared with the Terminal Bar app. Its strings are in `commands/res/values*/strings_commands.xml`, and launcher code that needs one imports `app.lawnchair.command.R as CommandR`. What differs between the two apps lives in `command-host/` (root of this repo), which the `:commands` module compiles together with its own code (same package, same `R`): `AppHost` (the `CommandHost` contract in `commands/src/.../CommandHost.kt`), `HostData`, `HostAiData`, and the launcher-only features: `ThemeCommand`, `ThemeChange`, `ThemeSpec`, `AiFolders`, `LauncherAiPrompts`, their actions (`ThemeAction`, `ImportAction`) and import targets (`LauncherImport`). Its strings are in `command-host/res/values*/strings_host.xml` and its tests in `command-host/tests/src`; `:commands:testDebugUnitTest` runs both. A rule that serves both apps goes in the submodule; one that exists only here goes in `command-host/`. What still touches the launcher (`CommandActivity`, `CommandExecutor`, `CommandSources`, `ThemeStore`, `FolderOrganizer`, the import activities) stays in `lawnchair/src/app/lawnchair/command/`, same package. Inside the engine each command is a `Command` (`Command.kt`): its `names()`, `requiresArgument`, and an optional `pattern`/`isApplicable` for text typed alone ("12*3", "github.com", "99999-9999"). `CommandRegistry` returns the command a word names; otherwise, if one command takes the text it runs, and if several do the bar lists them as options. Every built-in is its own class (`BuiltIn` gives it its words from the `BuiltInCommand` enum, plus its usage line and help): `CalcCommand`, `CallCommand`, `TaskCommand`, `UrlCommand`, `AiAskCommand`, and the groups in `AppCommands.kt`, `TimeCommands.kt`, `SystemCommands.kt`, `TextCommands.kt` and `DeviceCommands.kt`. A new built-in is a class added to `CommandRegistry.BUILT_IN` (and a word in the enum); custom actions are wrapped by `CustomCommand`, and a custom action can carry a `pattern` (a regex for text typed alone; it is run through `CommandPattern`, which caps its size and time and refuses patterns that take ordinary words). Enter on a list of options runs the highlighted one (`Analysis.picksOnEnter`). This was the active area of development on this branch.
- `data/` holds the Room `AppDatabase` (with `folder`, `iconoverride` and `wallpaper` subpackages).
- `icons/`, `theme/`, `font/`, `search/`, `predictions/`, `smartspace/`, `backup/` and `root/` are feature packages. `root/` uses the `IRootHelper` AIDL service.
- `aidl/` defines the `IBridge` service and `ILauncherOverlay` callbacks used for cross-process communication.

## Motor da barra de comando (submódulo `commands/`)

`commands/` é o submódulo `terminal-commands` (`git@github.com:mauriciodantas/terminal-commands.git`, privado, local em `/Users/mauricio/Projetos/terminal-commands`). É o motor da barra de comando, compartilhado com o Terminal Bar (`/Users/mauricio/Projetos/TerminalBar`). **Leia `commands/agent.md` antes de mexer no motor**: ele tem o contrato, o passo a passo e a recuperação.

Resumo do funcionamento:
- **Depois de clonar**: `git submodule update --init`. O módulo Gradle `:commands` é essa pasta e não funciona sem ela.
- **Onde cada mudança vai**: regra que serve aos dois apps entra no submódulo; regra que só existe aqui entra em `command-host/` (raiz deste repo), que o `:commands` compila junto com o próprio código (mesmo pacote `app.lawnchair.command`, mesmo `R`). Executar a ação (Intents, stores, telas) fica no código do app. Aqui o `command-host/` tem: `AppHost`, `HostData`, `HostAiData`, `ThemeCommand`, `ThemeChange`, `ThemeSpec`, `AiFolders`, `LauncherAiPrompts`, as ações `ThemeAction`/`ImportAction` e os alvos `LauncherImport`. Textos em `command-host/res/values*/strings_host.xml`, testes em `command-host/tests/src`.
- **Mudar o motor**: edite em `commands/` (na `main`, não em HEAD solto), rode `./gradlew :commands:testDebugUnitTest` aqui **e em o Terminal Bar**, faça push em `commands/`, e só então commite o novo ponteiro aqui e em o Terminal Bar. Ação ou campo novo no motor obriga os dois apps a tratá-lo no mesmo ciclo.
- **Sincronia**: os dois apps devem apontar para o mesmo commit (`git submodule status commands` nos dois). Nunca deixe o ponteiro apontar para um commit que não foi enviado.
- **Mudar só o que é daqui**: edite `command-host/` e rode os testes; o submódulo e os ponteiros não mudam.
- O SDK e o Java do `:commands` vêm do `allprojects` do `build.gradle` desta raiz; o Spotless cobre `commands/src`, `command-host/src` e `lawnchair/src`.

## Release

When the user asks for a "release", they always mean: build a new version and publish it to the Play Store **internal** track. Run `./release.sh` (default track is `internal`). Do only a build without publishing (`--build-only` for the `.aab`, `--apk` for an installable APK) or use another track (`PLAY_TRACK=...`) when the user explicitly asks for it. To publish to several tracks, pass them together (`./release.sh --tracks internal,closed-testing`): the script builds once, publishes to the first and promotes the same build to the others (`closed-testing` is the `alpha` track). To promote the build already in a track without building, use `./release.sh --promote-only` (internal to closed-testing by default; `--tracks from,to,...` picks the tracks).

`release.sh` fetches the signing credentials from 1Password (`op`), generates `versionCode`/`versionName` from the date and, without flags, publishes through Gradle Play Publisher. Artifacts from `--build-only` and `--apk` go to `releases/`, which is gitignored. The branch is `16-dev`; commit and push it before releasing.

## Conventions

- Branch names end in `-dev`. There are no GitHub Actions; builds and releases run locally (`release.sh`).
- Commit messages in this repo are written in Portuguese and prefixed with the feature area, for example `Barra de comando: ...` or `Botão [MIC] na barra da tela inicial: ...`. Follow that pattern for the same areas.
- Kotlin is formatted by Spotless with ktlint + Compose rules, and only `lawnchair/src/**/*.kt` is in its target. Java in `src/` and `quickstep/` is formatted with google-java-format AOSP style. Do not run Spotless against `compatLib/` (the Spotless config comments out that path intentionally).
- `.editorconfig` sets 4-space indentation, and `ktlint` allows trailing commas.
